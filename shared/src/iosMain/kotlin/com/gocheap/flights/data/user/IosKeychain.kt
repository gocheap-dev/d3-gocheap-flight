package com.gocheap.flights.data.user

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.cstr
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDataCreate
import platform.CoreFoundation.CFDataGetBytePtr
import platform.CoreFoundation.CFDataGetLength
import platform.CoreFoundation.CFDataRef
import platform.CoreFoundation.CFDictionaryAddValue
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFMutableDictionaryRef
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFStringCreateWithCString
import platform.CoreFoundation.CFStringRef
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFStringEncodingUTF8
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlock
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData

/**
 * A generic-password item in the iOS keychain, for the one thing this app stores that must outlive
 * the app itself.
 *
 * Not [com.gocheap.flights.data.storage.KeyValueStore]: `NSUserDefaults` is deleted with the app,
 * so a device that reinstalls would register as a brand new user every time. The keychain survives
 * deletion, which is the whole reason the device id is cached here and nowhere else.
 *
 * `kSecAttrAccessibleAfterFirstUnlock` rather than the default: registration runs at launch, which
 * can be a background launch before the device has been unlocked since boot, and the stricter
 * default would make the item unreadable exactly then — handing back a fresh UUID and a second
 * registration for a device that already had one.
 *
 * Written against CoreFoundation directly rather than through `NSDictionary` and toll-free
 * bridging: Kotlin/Native does not expose `CFBridgingRetain`, so every value here is created and
 * released by hand. Each `create` below has exactly one matching [CFRelease] in a `finally`.
 */
@OptIn(ExperimentalForeignApi::class)
internal object IosKeychain {

    /** Scopes the items to this app; the account name below is what distinguishes them. */
    private const val Service = "com.gocheap.flights.user"

    fun read(account: String): String? = memScoped {
        val query = baseQuery(account) ?: return null
        try {
            CFDictionaryAddValue(query, kSecReturnData, kCFBooleanTrue)
            CFDictionaryAddValue(query, kSecMatchLimit, kSecMatchLimitOne)

            val found = alloc<CFTypeRefVar>()
            if (SecItemCopyMatching(query, found.ptr) != errSecSuccess) return null

            // SecItemCopyMatching hands back a +1 reference, so this owns it and must release it.
            val data: CFDataRef = found.value?.reinterpret() ?: return null
            try {
                readUtf8(data)
            } finally {
                CFRelease(data)
            }
        } finally {
            CFRelease(query)
        }
    }

    /**
     * Upserts, by deleting first: `SecItemAdd` fails with `errSecDuplicateItem` rather than
     * replacing, and a silent no-op here would pin the device to a stale id forever.
     */
    fun write(account: String, value: String) {
        baseQuery(account)?.let { existing ->
            try {
                SecItemDelete(existing)
            } finally {
                CFRelease(existing)
            }
        }

        val insert = baseQuery(account) ?: return
        try {
            val data = utf8Data(value) ?: return
            try {
                CFDictionaryAddValue(insert, kSecValueData, data)
                CFDictionaryAddValue(insert, kSecAttrAccessible, kSecAttrAccessibleAfterFirstUnlock)
                SecItemAdd(insert, null)
            } finally {
                // The dictionary retained it on the way in, so this balances the create above.
                CFRelease(data)
            }
        } finally {
            CFRelease(insert)
        }
    }

    /** The `class + service + account` triple every operation above is keyed on. */
    private fun baseQuery(account: String): CFMutableDictionaryRef? {
        val query = CFDictionaryCreateMutable(
            allocator = null,
            capacity = 0,
            keyCallBacks = kCFTypeDictionaryKeyCallBacks.ptr,
            valueCallBacks = kCFTypeDictionaryValueCallBacks.ptr,
        ) ?: return null

        CFDictionaryAddValue(query, kSecClass, kSecClassGenericPassword)

        val service = cfString(Service)
        if (service != null) {
            CFDictionaryAddValue(query, kSecAttrService, service)
            CFRelease(service)
        }

        val name = cfString(account)
        if (name != null) {
            CFDictionaryAddValue(query, kSecAttrAccount, name)
            CFRelease(name)
        }

        return query
    }

    private fun cfString(value: String): CFStringRef? = memScoped {
        CFStringCreateWithCString(null, value.cstr.ptr, kCFStringEncodingUTF8)
    }

    private fun utf8Data(value: String): CFDataRef? {
        val bytes = value.encodeToByteArray()
        if (bytes.isEmpty()) return null
        return bytes.usePinned { pinned ->
            CFDataCreate(null, pinned.addressOf(0).reinterpret(), bytes.size.convert())
        }
    }

    private fun readUtf8(data: CFDataRef): String? {
        val length = CFDataGetLength(data).toInt()
        if (length <= 0) return null
        val bytes: CPointer<ByteVar> = CFDataGetBytePtr(data)?.reinterpret() ?: return null
        return ByteArray(length) { index -> bytes[index] }.decodeToString()
    }
}

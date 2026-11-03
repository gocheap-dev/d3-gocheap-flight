package com.gocheap.flights.ui.profile

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.gocheap.flights.utils.Constants
import platform.Foundation.NSBundle
import platform.Foundation.NSURL
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIViewController
import platform.UIKit.popoverPresentationController

@Composable
actual fun rememberProfileActions(): ProfileActions = remember { IosProfileActions() }

private class IosProfileActions : ProfileActions {

    /**
     * Presented from the key window's root controller: Compose Multiplatform runs inside one
     * hosting controller, so there is no view controller in scope to present from here.
     */
    override fun share(text: String) {
        val controller = UIActivityViewController(
            activityItems = listOf(text),
            applicationActivities = null,
        )
        val root = UIApplication.sharedApplication.keyWindow?.rootViewController ?: return
        // An iPad raises this as a popover and needs an anchor; the root view is the honest one
        // when the sheet was opened from a list row rather than from a bar button. The property is
        // declared on UIViewController, so it is reached through the base type here.
        (controller as UIViewController).popoverPresentationController?.sourceView = root.view
        root.presentViewController(controller, animated = true, completion = null)
    }

    override fun openUrl(url: String) {
        val target = NSURL.URLWithString(url) ?: return
        UIApplication.sharedApplication.openURL(target, options = emptyMap<Any?, Any>()) { }
    }

    /** CFBundleShortVersionString is the marketing version; the build number is not shown. */
    override val appVersion: String
        get() = NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleShortVersionString")
            as? String
            ?: "—"

    /**
     * The app has no App Store listing yet, so this stands the website in rather than linking to an
     * id that resolves to someone else's app. Swap it for `https://apps.apple.com/app/id<id>` as
     * soon as there is one.
     */
    override val storeUrl: String get() = Constants.Support.WebsiteUrl
}

package com.gocheap.travel.ui.profile

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun rememberProfileActions(): ProfileActions {
    val context = LocalContext.current
    return remember(context) { AndroidProfileActions(context) }
}

private class AndroidProfileActions(private val context: Context) : ProfileActions {

    override fun share(text: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        // The chooser rather than the bare intent: without it the system silently reuses whatever
        // app was last shared to, which reads as the sheet failing to open.
        start(Intent.createChooser(intent, null))
    }

    override fun openUrl(url: String) {
        start(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    override val appVersion: String
        get() = try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: Unknown
        } catch (error: PackageManager.NameNotFoundException) {
            Unknown
        }

    /**
     * The https form rather than `market://`: Play opens it itself when installed, and a device
     * without Play still has somewhere to send it.
     */
    override val storeUrl: String
        get() = "https://play.google.com/store/apps/details?id=${context.packageName}"

    /**
     * Nothing here is worth a crash: a device with no mail client, no browser or no store app is
     * unusual but not broken, and the row that was tapped is a convenience either way.
     */
    private fun start(intent: Intent) {
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (error: Exception) {
            Log.w(LogTag, "Nothing on this device could handle $intent", error)
        }
    }

    private companion object {
        const val LogTag = "ProfileActions"
        const val Unknown = "—"
    }
}

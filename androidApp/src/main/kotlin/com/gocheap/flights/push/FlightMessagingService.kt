package com.gocheap.flights.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import com.gocheap.flights.MainActivity
import com.gocheap.flights.R
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.gocheap.flights.push.PushNotifications as SharedPush

/**
 * Receives pushes while this app is running, and the token when Firebase changes it.
 *
 * Android only by nature: a `Service` is declared in a manifest, which the shared module does not
 * have. Everything this does with what it receives goes straight back to
 * [com.gocheap.flights.push.PushNotifications] in the shared module, so that the two platforms
 * cannot disagree about what a push means.
 *
 * Note what is *not* delivered here. A message carrying a `notification` payload is drawn by the
 * system on its own while this app is in the background, and [onMessageReceived] is never called
 * for it. This runs for a data-only message at any time, and for a `notification` message only
 * while the app is in the foreground — which is the case the system does not draw, and so the case
 * the notification below exists for.
 */
class FlightMessagingService : FirebaseMessagingService() {

    /**
     * A new registration token.
     *
     * Only fires on a *change* — a fresh install's first token arrives through
     * `startPushMessaging()` instead, which is why both paths exist.
     */
    override fun onNewToken(token: String) {
        SharedPush.onTokenChanged(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val title = message.notification?.title ?: message.data[TitleKey]
        val body = message.notification?.body ?: message.data[BodyKey]

        SharedPush.onReceived(title, body, message.data)

        // Nothing to show is a legitimate message: a data-only push can exist purely to make the
        // app refresh something, and inventing a notification for it would put an empty row in the
        // shade.
        if (title.isNullOrBlank() && body.isNullOrBlank()) return

        try {
            PushNotifier.show(this, title, body, message.data)
        } catch (error: Throwable) {
            // A notification that cannot be posted must not take the process down — this runs on a
            // system-driven thread, and an exception here is an uncaught one.
            Log.w(LogTag, "Could not post notification: ${error.message}", error)
        }
    }

    private companion object {
        const val LogTag = "Push"

        /** Where a data-only push puts the text a `notification` payload would have carried. */
        const val TitleKey = "title"
        const val BodyKey = "body"
    }
}

/**
 * Draws one push into the notification shade.
 *
 * Separate from the service so that the channel it needs is created in one place and can be
 * created early — the channel has to exist before the first notification is posted to it, and the
 * system silently drops a notification posted to a channel it does not know.
 */
internal object PushNotifier {

    /**
     * The one channel this app posts to.
     *
     * Also named in the manifest, as `default_notification_channel_id`, which is what the system
     * uses for a message drawn while this app is in the background — the path that never reaches
     * this code. Both have to agree, or a push would land in one channel in the foreground and
     * another in the background, and switching the channel off would only half work.
     */
    const val ChannelId = "flighttp_default"

    /**
     * Creates the channel if it is not already there.
     *
     * Safe to call repeatedly: the system treats a second creation with the same id as a no-op,
     * except for the name and description, which it updates — which is what carries a renamed
     * channel through an app update.
     */
    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            ChannelId,
            context.getString(R.string.push_channel_name),
            // Default rather than High: these are offers and price changes, not an alarm, and a
            // heads-up banner over whatever the user is doing is not what they are worth.
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.push_channel_description)
        }
        manager.createNotificationChannel(channel)
    }

    fun show(context: Context, title: String?, body: String?, data: Map<String, String>) {
        ensureChannel(context)

        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val notification = NotificationCompat.Builder(context, ChannelId)
            .setSmallIcon(R.drawable.ic_stat_push)
            .setContentTitle(title)
            .setContentText(body)
            // So a body longer than one line can be read without opening the app.
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(PushIntents.tapIntent(context, data))
            .build()

        // The message id where there is one, so a campaign that sends the same push twice replaces
        // its own row instead of stacking two identical ones. The clock is the fallback rather than
        // a constant, so two genuinely different pushes do not overwrite each other.
        val id = data[MessageIdKey]?.hashCode() ?: System.currentTimeMillis().toInt()
        manager.notify(id, notification)
    }

    private const val MessageIdKey = "google.message_id"
}

/**
 * How a tapped notification reaches the app, and how the app recognises one when it arrives.
 *
 * Two routes end up at the same place. A notification this app posted carries the `PendingIntent`
 * built here; one the system drew carries an intent the system built, with the push's `data` map
 * already copied into the extras. [isFromPush] is what tells either of them apart from an ordinary
 * launch, and [payload] reads the data back out of whichever it was.
 */
internal object PushIntents {

    /** Set on the intent this app builds. The system's own notifications do not carry it. */
    const val FromPushExtra = "com.gocheap.flights.FROM_PUSH"

    /**
     * Firebase stamps this on the launch intent of a notification it drew itself, so it doubles as
     * the marker for that route.
     */
    private const val MessageIdExtra = "google.message_id"

    fun tapIntent(context: Context, data: Map<String, String>): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            // The app's existing task, not a second copy of it: the user tapped a notification
            // belonging to an app they may already have open behind it.
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(FromPushExtra, true)
            for ((key, value) in data) putExtra(key, value)
        }
        return PendingIntent.getActivity(
            context,
            // Distinct per push, so two notifications do not share — and overwrite — one intent.
            data[MessageIdExtra]?.hashCode() ?: 0,
            intent,
            // Immutable because nothing is meant to rewrite this, and required from Android 12.
            // UPDATE_CURRENT so a re-sent push refreshes the extras of the one already queued.
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    fun isFromPush(intent: Intent?): Boolean {
        if (intent == null) return false
        return intent.getBooleanExtra(FromPushExtra, false) || intent.hasExtra(MessageIdExtra)
    }

    /**
     * The push's `data` map, read back off the intent.
     *
     * Everything that is a string, minus the marker: the system copies the data payload in as
     * individual string extras, and there is no record of which keys were the sender's. Anything
     * non-string was put there by the platform rather than the sender — `google.sent_time` is a
     * long — and is of no use to a caller reading the sender's own keys.
     */
    fun payload(intent: Intent?): Map<String, String> {
        val extras = intent?.extras ?: return emptyMap()
        return buildMap {
            for (key in extras.keySet()) {
                if (key == FromPushExtra) continue
                val value = extras.getString(key) ?: continue
                put(key, value)
            }
        }
    }
}

package com.sakura.client

import android.app.Notification
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.os.Build

class SakuraMediaListener : NotificationListenerService() {
    override fun onListenerConnected() {
        super.onListenerConnected()
        refreshSessions()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn?.packageName == SPOTIFY_PACKAGE) refreshSessions()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        if (sbn?.packageName == SPOTIFY_PACKAGE) refreshSessions()
    }

    private fun refreshSessions() {
        try {
            val spotifyNotification = activeNotifications
                ?.firstOrNull { it.packageName == SPOTIFY_PACKAGE }?.notification
            val token = spotifyNotification?.let { getSessionToken(it) }
            if (token != null) {
                currentController = MediaController(this, token)
            } else {
                currentController = null
            }
            currentController?.registerCallback(object : MediaController.Callback() {
                override fun onMetadataChanged(metadata: MediaMetadata?) {
                    currentController = currentController
                }
                override fun onSessionDestroyed() {
                    currentController = null
                }
            })
        } catch (_: Exception) {
            currentController = null
        }
    }

    @Suppress("DEPRECATION")
    private fun getSessionToken(notification: Notification): MediaSession.Token? {
        return if (Build.VERSION.SDK_INT >= 33) {
            notification.extras.getParcelable(Notification.EXTRA_MEDIA_SESSION, MediaSession.Token::class.java)
        } else {
            notification.extras.getParcelable(Notification.EXTRA_MEDIA_SESSION) as? MediaSession.Token
        }
    }

    companion object {
        const val SPOTIFY_PACKAGE = "com.spotify.music"
        @Volatile var currentController: MediaController? = null
    }
}

package com.sakura.client

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.SystemClock
import android.provider.Settings
import android.view.KeyEvent

/**
 * Управление Spotify через активные медиасессии системы.
 * Чтобы видеть название/обложку, нужен доступ к уведомлениям (для SakuraMediaListener).
 * Кнопки работают и без него: тогда шлём системные медиа-клавиши.
 */
object SpotifyBridge {
    const val PKG = "com.spotify.music"

    class Snapshot(
        val listenerOk: Boolean,
        val active: Boolean,
        val title: String?,
        val artist: String?,
        val art: Bitmap?,
        val playing: Boolean,
        val fraction: Float
    )

    private val EMPTY_OFF = Snapshot(false, false, null, null, null, false, 0f)

    fun listenerEnabled(ctx: Context): Boolean {
        val flat = Settings.Secure.getString(ctx.contentResolver, "enabled_notification_listeners")
            ?: return false
        val me = ComponentName(ctx, SakuraMediaListener::class.java)
        return flat.split(":").any { ComponentName.unflattenFromString(it) == me }
    }

    private fun controller(ctx: Context): MediaController? = try {
        val mm = ctx.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        mm.getActiveSessions(ComponentName(ctx, SakuraMediaListener::class.java))
            .firstOrNull { it.packageName == PKG }
    } catch (e: Exception) {
        null
    }

    fun snapshot(ctx: Context): Snapshot {
        val ok = listenerEnabled(ctx)
        if (!ok) return EMPTY_OFF
        val c = controller(ctx) ?: return Snapshot(true, false, null, null, null, false, 0f)
        val md = c.metadata
        val st = c.playbackState
        val playing = st?.state == PlaybackState.STATE_PLAYING || st?.state == PlaybackState.STATE_BUFFERING
        val dur = md?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
        var pos = st?.position ?: 0L
        if (st != null && st.state == PlaybackState.STATE_PLAYING && st.lastPositionUpdateTime > 0) {
            pos += ((SystemClock.elapsedRealtime() - st.lastPositionUpdateTime) * st.playbackSpeed).toLong()
        }
        val art = md?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: md?.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: md?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        val artist = md?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: md?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
        return Snapshot(
            true, true,
            md?.getString(MediaMetadata.METADATA_KEY_TITLE),
            artist, art, playing,
            if (dur > 0) pos.toFloat() / dur else 0f
        )
    }

    fun playPause(ctx: Context) {
        val c = controller(ctx)
        if (c != null) {
            if (c.playbackState?.state == PlaybackState.STATE_PLAYING) c.transportControls.pause()
            else c.transportControls.play()
        } else sendKey(ctx, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
    }

    fun next(ctx: Context) {
        val c = controller(ctx)
        if (c != null) c.transportControls.skipToNext() else sendKey(ctx, KeyEvent.KEYCODE_MEDIA_NEXT)
    }

    fun previous(ctx: Context) {
        val c = controller(ctx)
        if (c != null) c.transportControls.skipToPrevious() else sendKey(ctx, KeyEvent.KEYCODE_MEDIA_PREVIOUS)
    }

    private fun sendKey(ctx: Context, code: Int) {
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
    }
}

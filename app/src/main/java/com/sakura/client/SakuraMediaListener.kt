package com.sakura.client

import android.service.notification.NotificationListenerService

/**
 * Сам по себе ничего не делает. Нужен только как «разрешённый слушатель уведомлений»:
 * с таким доступом система разрешает приложению читать активные медиасессии (Spotify).
 */
class SakuraMediaListener : NotificationListenerService()

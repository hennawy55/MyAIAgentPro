package com.motohana.assistant

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class NotificationReaderService : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val item = sbn ?: return
        val prefs = getSharedPreferences("moto_hana_data", MODE_PRIVATE)
        val allowed = prefs.getStringSet("watched_packages", emptySet()) ?: emptySet()
        if (allowed.isNotEmpty() && item.packageName !in allowed) return
        val extras = item.notification.extras
        val title = extras.getCharSequence("android.title")?.toString().orEmpty()
        val body = extras.getCharSequence("android.text")?.toString().orEmpty()
        val assistantName = prefs.getString("assistant_name", "موتو حنا") ?: "موتو حنا"
        val message = "$title\n$body"
        if (message.contains(assistantName, ignoreCase = true)) {
            prefs.edit().putString("last_command", message).putLong("last_command_at", System.currentTimeMillis()).apply()
        }
    }
}

package com.motohana.assistant

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class NotificationReaderService : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val item = sbn ?: return
        val prefs = getSharedPreferences("moto_hana_data", MODE_PRIVATE)
        val selected = prefs.getStringSet("watched_packages", emptySet()) ?: emptySet()
        if (selected.isEmpty() || item.packageName !in selected) return
        val extras = item.notification.extras
        val title = extras.getCharSequence("android.title")?.toString().orEmpty()
        val body = extras.getCharSequence("android.bigText")?.toString()
            ?: extras.getCharSequence("android.text")?.toString().orEmpty()
        val assistantName = prefs.getString("assistant_name", "موتو حنا")?.trim().orEmpty()
        if (assistantName.isBlank()) return
        val fullText = "$title\n$body".trim()
        if (!fullText.contains(assistantName, ignoreCase = true)) return
        prefs.edit().putString("last_command", fullText)
            .putString("last_command_package", item.packageName)
            .putLong("last_command_at", System.currentTimeMillis()).apply()
        // Current release intentionally records the command only. Unsupported or ambiguous
        // actions are never executed speculatively.
    }
}

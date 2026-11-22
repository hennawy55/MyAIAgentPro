package com.motohana.assistant
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
class NotificationReaderService : NotificationListenerService() {
 override fun onNotificationPosted(sbn: StatusBarNotification?) {
  val extras = sbn?.notification?.extras ?: return
  val title = extras.getCharSequence("android.title")?.toString().orEmpty()
  val body = extras.getCharSequence("android.text")?.toString().orEmpty()
  if (title.isNotBlank() || body.isNotBlank()) getSharedPreferences("local", MODE_PRIVATE).edit().putString("last_notification", "$title\n$body").apply()
 }
}

package com.motohana.assistant

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(NotificationChannel("reminders", "التذكيرات", NotificationManager.IMPORTANCE_HIGH))
        }
        val text = intent.getStringExtra("text") ?: "لديك تذكير"
        val id = intent.getLongExtra("id", System.currentTimeMillis()).toInt()
        val notification = NotificationCompat.Builder(context, "reminders")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("موتو حنا")
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        manager.notify(id, notification)
        val reminderId = intent.getLongExtra("id", -1L)
        if (reminderId != -1L) ReminderStore.remove(context, reminderId)
    }
}

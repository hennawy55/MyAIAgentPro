package com.motohana.assistant

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import org.json.JSONArray
import org.json.JSONObject

data class Reminder(val id: Long, val text: String, val at: Long)

object ReminderStore {
    private const val PREF = "moto_hana_data"
    private const val KEY = "reminders"

    fun all(context: Context): List<Reminder> {
        val raw = context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY, "[]") ?: "[]"
        val arr = JSONArray(raw)
        return (0 until arr.length()).mapNotNull { i ->
            runCatching {
                val o = arr.getJSONObject(i)
                Reminder(o.getLong("id"), o.getString("text"), o.getLong("at"))
            }.getOrNull()
        }.sortedBy { it.at }
    }

    fun add(context: Context, text: String, at: Long): Reminder {
        val item = Reminder(System.currentTimeMillis(), text, at)
        val list = all(context).toMutableList().apply { add(item) }
        save(context, list)
        schedule(context, item)
        return item
    }

    fun remove(context: Context, id: Long) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ReminderReceiver::class.java).putExtra("id", id)
        val pi = PendingIntent.getBroadcast(context, id.toInt(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        am.cancel(pi)
        save(context, all(context).filterNot { it.id == id })
    }

    fun restore(context: Context) = all(context).filter { it.at > System.currentTimeMillis() }.forEach { schedule(context, it) }

    private fun save(context: Context, list: List<Reminder>) {
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("id", it.id).put("text", it.text).put("at", it.at)) }
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putString(KEY, arr.toString()).apply()
    }

    private fun schedule(context: Context, item: Reminder) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ReminderReceiver::class.java)
            .putExtra("id", item.id).putExtra("text", item.text)
        val pi = PendingIntent.getBroadcast(context, item.id.toInt(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        if (android.os.Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, item.at, pi)
        } else {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, item.at, pi)
        }
    }
}

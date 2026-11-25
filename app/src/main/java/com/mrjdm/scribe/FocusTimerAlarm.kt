package com.mrjdm.scribe

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat

object FocusTimerAlarm {
    private const val CHANNEL_ID = "scribe_focus_timer"
    private const val REQUEST_CODE = 7412

    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, FocusTimerAlarmReceiver::class.java).setAction("com.mrjdm.scribe.FOCUS_TIMER_FINISHED")
        return PendingIntent.getBroadcast(context, REQUEST_CODE, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    fun schedule(context: Context, endAtMillis: Long, label: String) {
        context.getSharedPreferences("scribe_preferences", Context.MODE_PRIVATE).edit().putString("focus_alarm_label", label).apply()
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endAtMillis, pendingIntent(context))
    }

    fun cancel(context: Context) {
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarm.cancel(pendingIntent(context))
    }

    internal fun showFinishedNotification(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Focus timer", NotificationManager.IMPORTANCE_DEFAULT))
        val label = context.getSharedPreferences("scribe_preferences", Context.MODE_PRIVATE).getString("focus_alarm_label", "Focused study") ?: "Focused study"
        val openApp = PendingIntent.getActivity(context, REQUEST_CODE + 1, Intent(context, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Focus session complete")
            .setContentText("$label · Your focus timer has finished.")
            .setContentIntent(openApp).setAutoCancel(true).setPriority(NotificationCompat.PRIORITY_DEFAULT).build()
        runCatching { manager.notify(REQUEST_CODE, notification) }
    }
}

class FocusTimerAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == "com.mrjdm.scribe.FOCUS_TIMER_FINISHED") {
            context.getSharedPreferences("scribe_preferences", Context.MODE_PRIVATE).edit().remove("focus_end_at").apply()
            FocusTimerAlarm.showFinishedNotification(context)
        }
    }
}
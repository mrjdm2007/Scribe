package com.mrjdm.scribe

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.mrjdm.scribe.data.ScribeDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private const val REMINDER_CHANNEL = "scribe_study_reminders"
private const val EXTRA_ID = "reminder_id"
private const val EXTRA_TITLE = "reminder_title"
private const val EXTRA_MESSAGE = "reminder_message"
private const val ACTION_MARK_DONE = "com.mrjdm.scribe.REMINDER_DONE"
private const val ACTION_SNOOZE = "com.mrjdm.scribe.REMINDER_SNOOZE"

fun scheduleStudyReminder(context: Context, id: Long, title: String, message: String, atMillis: Long) {
    val intent = Intent(context, StudyReminderReceiver::class.java).apply {
        putExtra(EXTRA_ID, id)
        putExtra(EXTRA_TITLE, title)
        putExtra(EXTRA_MESSAGE, message)
    }
    val pending = PendingIntent.getBroadcast(
        context, id.toInt(), intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pending)
}

fun cancelStudyReminder(context: Context, id: Long) {
    val intent = Intent(context, StudyReminderReceiver::class.java)
    val pending = PendingIntent.getBroadcast(
        context, id.toInt(), intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(pending)
    pending.cancel()
}

class StudyReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(EXTRA_ID, 0L)
        if (id <= 0L) return
        if (intent.action == ACTION_MARK_DONE || intent.action == ACTION_SNOOZE) {
            val pending = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    if (intent.action == ACTION_MARK_DONE) {
                        val dao = ScribeDatabase.get(context).dao()
                        dao.byId(id)?.let { item ->
                            dao.update(item.copy(deletedAt = System.currentTimeMillis(), updatedAt = System.currentTimeMillis()))
                        }
                    } else {
                        val title = intent.getStringExtra(EXTRA_TITLE) ?: "Study reminder"
                        val message = intent.getStringExtra(EXTRA_MESSAGE) ?: "Time to review your notes."
                        scheduleStudyReminder(context, id, title, message, System.currentTimeMillis() + 10 * 60 * 1000L)
                    }
                    NotificationManagerCompat.from(context).cancel(id.toInt())
                } finally {
                    pending.finish()
                }
            }
            return
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(NotificationChannel(REMINDER_CHANNEL, "Study reminders", NotificationManager.IMPORTANCE_DEFAULT))
        }
        val openApp = Intent(context, MainActivity::class.java)
        val contentIntent = PendingIntent.getActivity(context, id.toInt(), openApp, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        fun actionPending(action: String, requestCode: Int): PendingIntent {
            val actionIntent = Intent(context, StudyReminderReceiver::class.java).apply {
                this.action = action
                putExtra(EXTRA_ID, id)
                putExtra(EXTRA_TITLE, intent.getStringExtra(EXTRA_TITLE) ?: "Study reminder")
                putExtra(EXTRA_MESSAGE, intent.getStringExtra(EXTRA_MESSAGE) ?: "Time to review your notes.")
            }
            return PendingIntent.getBroadcast(context, requestCode, actionIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }
        val doneAction = actionPending(ACTION_MARK_DONE, id.toInt() + 1_000_000)
        val snoozeAction = actionPending(ACTION_SNOOZE, id.toInt() + 2_000_000)
        val notification = NotificationCompat.Builder(context, REMINDER_CHANNEL).setContentIntent(contentIntent)
            .addAction(R.drawable.ic_notification, "Mark done", doneAction)
            .addAction(R.drawable.ic_notification, "Snooze 10 min", snoozeAction)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(intent.getStringExtra(EXTRA_TITLE) ?: "Study reminder")
            .setContentText(intent.getStringExtra(EXTRA_MESSAGE) ?: "Time to review your notes.")
            .setStyle(NotificationCompat.BigTextStyle().bigText(intent.getStringExtra(EXTRA_MESSAGE) ?: "Time to review your notes."))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        NotificationManagerCompat.from(context).notify(id.toInt(), notification)
    }
}

class StudyBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                runCatching {
                    val items = ScribeDatabase.get(context).dao().allItems()
                    val now = System.currentTimeMillis()
                    items.filter { it.kind == "study_reminder" && it.deletedAt == null }.forEach { item ->
                        val at = item.tags.substringAfter("dueAt:", "").substringBefore(";").toLongOrNull()
                        if (at != null && at > now) scheduleStudyReminder(context, item.id, item.title, item.body, at)
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }
}

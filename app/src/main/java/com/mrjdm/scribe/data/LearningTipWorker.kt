package com.mrjdm.scribe.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.mrjdm.scribe.MainActivity
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

internal object LearningTipScheduler {
    private const val WORK_NAME = "scribe-hourly-learning-tips"

    fun update(context: Context) {
        val preferences = context.getSharedPreferences("scribe_study_workspace", Context.MODE_PRIVATE)
        val enabled = preferences.getBoolean("notifications_enabled", false)
        val interval = preferences.getInt("notification_interval", 1).coerceIn(1, 4)
        if (!enabled || ScribeFirebaseSession.read(context) == null) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            return
        }
        val request = PeriodicWorkRequestBuilder<LearningTipWorker>(interval.toLong(), TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(androidx.work.BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }
}

internal class LearningTipWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val preferences = applicationContext.getSharedPreferences("scribe_study_workspace", Context.MODE_PRIVATE)
        if (!preferences.getBoolean("notifications_enabled", false)) return Result.success()
        if (insideQuietHours(preferences.getString("quiet_start", "22:00"), preferences.getString("quiet_end", "07:00"))) return Result.success()

        val session = ScribeFirebaseSession.read(applicationContext) ?: return Result.success()
        return try {
            val notes = ScribeDatabase.get(applicationContext).dao().allItems()
                .filter { it.deletedAt == null && it.kind in setOf("topic", "page", "block") && it.title.isNotBlank() }
            if (notes.isEmpty()) return Result.success()

            val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            val usedKey = "used_topics_$today"
            val used = preferences.getStringSet(usedKey, emptySet()).orEmpty()
            val candidates = notes.filter { topicKey(it.title) !in used }
            if (candidates.isEmpty()) return Result.success()
            val topic = candidates.random()

            val accessToken = refreshFirebaseIdToken(session)
            val contextArray = JSONArray().put(
                JSONObject()
                    .put("title", topic.title)
                    .put("path", "Scribe notebook")
                    .put("content", topic.body.take(7000))
            )
            val requestBody = JSONObject()
                .put("question", "Give one useful, accurate learning tip about this topic in 1–3 short sentences. Keep it clear and memorable. Topic: ${topic.title}")
                .put("mode", "hourly_tip")
                .put("note_context", contextArray)
            val response = postJson(
                "https://asia-south1-${session.projectId}.cloudfunctions.net/studyAssistant",
                accessToken,
                requestBody
            )
            val tip = response.optString("answer").ifBlank { response.optString("tip") }
                .trim().take(360)
            if (tip.isBlank()) return Result.retry()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) return Result.success()

            showNotification(topic.title, tip)
            preferences.edit().putStringSet(usedKey, (used + topicKey(topic.title)).toSet()).apply()
            Result.success()
        } catch (_: java.net.SocketTimeoutException) {
            Result.retry()
        } catch (_: java.io.IOException) {
            Result.retry()
        } catch (_: Throwable) {
            Result.retry()
        }
    }

    private fun topicKey(title: String) = title.trim().lowercase(Locale.ROOT)

    private fun insideQuietHours(startValue: String?, endValue: String?): Boolean {
        fun minutes(value: String?, fallback: Int): Int {
            val parts = value.orEmpty().split(":")
            if (parts.size != 2) return fallback
            val hour = parts[0].toIntOrNull() ?: return fallback
            val minute = parts[1].toIntOrNull() ?: return fallback
            if (hour !in 0..23 || minute !in 0..59) return fallback
            return hour * 60 + minute
        }
        val start = minutes(startValue, 22 * 60)
        val end = minutes(endValue, 7 * 60)
        val now = Calendar.getInstance().let { it.get(Calendar.HOUR_OF_DAY) * 60 + it.get(Calendar.MINUTE) }
        return if (start == end) false else if (start < end) now in start until end else now >= start || now < end
    }

    private fun refreshFirebaseIdToken(session: FirebaseSavedSession): String {
        val connection = (URL("https://securetoken.googleapis.com/v1/token?key=${session.apiKey}").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; connectTimeout = 20000; readTimeout = 30000; doOutput = true
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        }
        val body = "grant_type=refresh_token&refresh_token=" + java.net.URLEncoder.encode(session.refreshToken, "UTF-8")
        connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        val status = connection.responseCode
        val stream = if (status in 200..299) connection.inputStream else connection.errorStream
        val raw = stream.bufferedReader().use { it.readText() }
        connection.disconnect()
        val json = JSONObject(raw)
        if (status !in 200..299) throw java.io.IOException("Firebase session refresh failed. Sign in again.")
        val idToken = json.getString("id_token")
        val nextRefresh = json.optString("refresh_token").takeIf { it.isNotBlank() } ?: session.refreshToken
        ScribeFirebaseSession.save(applicationContext, session.apiKey, session.projectId, session.email, nextRefresh)
        return idToken
    }

    private fun postJson(url: String, accessToken: String, body: JSONObject): JSONObject {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; connectTimeout = 20000; readTimeout = 90000; doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", "Bearer $accessToken")
        }
        connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
        val status = connection.responseCode
        val stream = if (status in 200..299) connection.inputStream else connection.errorStream
        val raw = stream.bufferedReader().use { it.readText() }
        connection.disconnect()
        val json = JSONObject(raw)
        if (status !in 200..299) throw java.io.IOException(json.optString("error", "AI tip request failed."))
        return json
    }

    private fun showNotification(topic: String, tip: String) {
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "scribe_learning_tips"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(NotificationChannel(channelId, "Learning tips", NotificationManager.IMPORTANCE_DEFAULT))
        }
        val intent = Intent(applicationContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pending = PendingIntent.getActivity(applicationContext, 8521, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("A quick study tip · $topic")
            .setContentText(tip)
            .setStyle(NotificationCompat.BigTextStyle().bigText(tip))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        manager.notify((System.currentTimeMillis() % Int.MAX_VALUE).toInt(), notification)
    }
}

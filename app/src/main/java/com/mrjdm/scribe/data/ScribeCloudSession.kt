package com.mrjdm.scribe.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

internal data class SavedCloudSession(
    val url: String,
    val anonKey: String,
    val email: String,
    val refreshToken: String
)

internal object ScribeCloudSession {
    private const val PREFS = "scribe_cloud_secure"

    private fun preferences(context: Context) = EncryptedSharedPreferences.create(
        context,
        PREFS,
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun save(context: Context, url: String, anonKey: String, email: String, refreshToken: String) {
        preferences(context).edit()
            .putString("url", url)
            .putString("anon_key", anonKey)
            .putString("email", email)
            .putString("refresh_token", refreshToken)
            .apply()
    }

    fun read(context: Context): SavedCloudSession? {
        val prefs = preferences(context)
        val url = prefs.getString("url", null) ?: return null
        val key = prefs.getString("anon_key", null) ?: return null
        val token = prefs.getString("refresh_token", null) ?: return null
        return SavedCloudSession(url, key, prefs.getString("email", "").orEmpty(), token)
    }

    fun clear(context: Context) {
        preferences(context).edit().clear().apply()
        WorkManager.getInstance(context).cancelUniqueWork("scribe-periodic-cloud-sync")
    }

    fun schedule(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = PeriodicWorkRequestBuilder<ScribeCloudSyncWorker>(6, TimeUnit.HOURS)
            .setConstraints(constraints)
            .setBackoffCriteria( androidx.work.BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "scribe-periodic-cloud-sync",
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }
}

internal class ScribeCloudSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        return try {
            val session = ScribeCloudSession.read(applicationContext) ?: return Result.success()
            ScribeSupabaseSync(
                applicationContext,
                ScribeDatabase.get(applicationContext).dao()
            ).sync(
                url = session.url,
                anonKey = session.anonKey,
                email = session.email,
                refreshToken = session.refreshToken
            )
            Result.success()
        } catch (_: Throwable) {
            Result.retry()
        }
    }
}

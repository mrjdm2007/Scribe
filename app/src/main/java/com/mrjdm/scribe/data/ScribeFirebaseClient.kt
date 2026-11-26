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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

internal data class FirebaseSavedSession(
    val apiKey: String,
    val projectId: String,
    val email: String,
    val refreshToken: String
)

internal object ScribeFirebaseSession {
    private const val PREFS = "scribe_firebase_secure"
    private fun prefs(context: Context) = EncryptedSharedPreferences.create(
        context, PREFS,
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
    fun save(context: Context, apiKey: String, projectId: String, email: String, refreshToken: String) {
        prefs(context).edit().putString("api_key", apiKey).putString("project_id", projectId)
            .putString("email", email).putString("refresh_token", refreshToken).apply()
    }
    fun read(context: Context): FirebaseSavedSession? {
        val p = prefs(context)
        val key = p.getString("api_key", null) ?: return null
        val project = p.getString("project_id", null) ?: return null
        val token = p.getString("refresh_token", null) ?: return null
        return FirebaseSavedSession(key, project, p.getString("email", "").orEmpty(), token)
    }
    fun clear(context: Context) { prefs(context).edit().clear().apply(); WorkManager.getInstance(context).cancelUniqueWork("scribe-firebase-library-sync") }
    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<ScribeFirebaseSyncWorker>(6, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork("scribe-firebase-library-sync", ExistingPeriodicWorkPolicy.UPDATE, request)
    }
}

/** Firebase Identity Toolkit + authenticated Cloud Functions REST client. */
internal class ScribeFirebaseClient(
    private val context: Context,
    private val dao: ScribeDao,
    private val apiKey: String,
    private val projectId: String
) {
    private data class Tokens(val idToken: String, val refreshToken: String, val expiresIn: Long)

    private fun request(url: String, method: String, body: String? = null, token: String? = null): JSONObject {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = 20_000
            connection.readTimeout = 120_000
            connection.setRequestProperty("Content-Type", "application/json")
            if (token != null) connection.setRequestProperty("Authorization", "Bearer $token")
            if (body != null) {
                connection.doOutput = true
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val raw = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                val message = runCatching { JSONObject(raw).optJSONObject("error")?.optString("message") }.getOrNull()
                error("Firebase request failed ($code): ${message?.takeIf { it.isNotBlank() } ?: raw.take(300)}")
            }
            return if (raw.isBlank()) JSONObject() else JSONObject(raw)
        } finally { connection.disconnect() }
    }

    private fun authenticate(email: String, password: String, create: Boolean): Tokens {
        require(apiKey.isNotBlank() && projectId.matches(Regex("[a-z][a-z0-9-]{4,28}[a-z0-9]"))) {
            "Enter your Firebase Web API key and project ID in Cloud Sync settings."
        }
        require(email.isNotBlank() && password.length >= 6) { "Enter a valid email and a password of at least 6 characters." }
        val endpoint = if (create) "accounts:signUp" else "accounts:signInWithPassword"
        val response = request(
            "https://identitytoolkit.googleapis.com/v1/$endpoint?key=$apiKey",
            "POST",
            JSONObject().put("email", email.trim()).put("password", password).put("returnSecureToken", true).toString()
        )
        return Tokens(response.getString("idToken"), response.getString("refreshToken"),
            response.optString("expiresIn", "3600").toLongOrNull() ?: 3600)
    }

    private fun refresh(saved: FirebaseSavedSession): Tokens {
        val connection = URL("https://securetoken.googleapis.com/v1/token?key=${saved.apiKey}").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 20_000
            connection.readTimeout = 30_000
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            connection.doOutput = true
            val body = "grant_type=refresh_token&refresh_token=" + java.net.URLEncoder.encode(saved.refreshToken, "UTF-8")
            connection.outputStream.use { it.write(body.toByteArray()) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val raw = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) error("Firebase session refresh failed ($code). Sign in again.")
            val json = JSONObject(raw)
            return Tokens(json.getString("id_token"), json.getString("refresh_token"),
                json.optString("expires_in", "3600").toLongOrNull() ?: 3600)
        } finally { connection.disconnect() }
    }

    suspend fun signInAndSync(email: String, password: String, createAccount: Boolean): String =
        withContext(Dispatchers.IO) {
            val tokens = authenticate(email, password, createAccount)
            ScribeFirebaseSession.save(context, apiKey, projectId, email.trim(), tokens.refreshToken)
            ScribeFirebaseSession.schedule(context)
            syncWithToken(tokens.idToken, tokens.refreshToken, projectId)
        }

    private fun attachmentRequest(project: String, token: String, method: String, id: String, body: JSONObject? = null): JSONObject {
        val url = "https://asia-south1-$project.cloudfunctions.net/studyAttachments" +
            if (method == "GET") "?id=" + java.net.URLEncoder.encode(id, "UTF-8") else ""
        return request(url, method, body?.toString(), token)
    }

    private fun uploadAttachment(project: String, token: String, id: String, file: File): JSONObject {
        require(file.length() <= 20L * 1024L * 1024L) { "Attachments larger than 20 MB stay local." }
        val mime = java.net.URLConnection.guessContentTypeFromName(file.name) ?: "application/octet-stream"
        val body = JSONObject().put("id", id).put("name", file.name).put("contentType", mime)
            .put("base64", android.util.Base64.encodeToString(file.readBytes(), android.util.Base64.NO_WRAP))
        return attachmentRequest(project, token, "POST", id, body)
    }

    suspend fun studyAssistant(question: String, mode: String, noteContext: JSONArray): JSONObject =
        withContext(Dispatchers.IO) {
            val saved = ScribeFirebaseSession.read(context) ?: error("Connect a Firebase account in Settings before using Scribe Intelligence.")
            val tokens = refresh(saved)
            ScribeFirebaseSession.save(context, saved.apiKey, saved.projectId, saved.email, tokens.refreshToken)
            val body = JSONObject().put("question", question).put("mode", mode).put("note_context", noteContext)
            request("https://asia-south1-${saved.projectId}.cloudfunctions.net/studyAssistant",
                "POST", body.toString(), tokens.idToken)
        }

    suspend fun syncSaved(): String = withContext(Dispatchers.IO) {
        val saved = ScribeFirebaseSession.read(context) ?: error("No Firebase account is connected.")
        val tokens = refresh(saved)
        ScribeFirebaseSession.save(context, saved.apiKey, saved.projectId, saved.email, tokens.refreshToken)
        syncWithToken(tokens.idToken, tokens.refreshToken, saved.projectId)
    }

    private suspend fun syncWithToken(idToken: String, refreshToken: String, targetProjectId: String): String {
        val root = "https://asia-south1-$targetProjectId.cloudfunctions.net/studyLibrary"
        val remote = JSONArray()
        var cursor = ""
        do {
            val suffix = if (cursor.isBlank()) "" else "&cursor=" + java.net.URLEncoder.encode(cursor, "UTF-8")
            val page = request("$root?limit=500$suffix", "GET", token = idToken)
            val rows = page.optJSONArray("records") ?: JSONArray()
            for (index in 0 until rows.length()) rows.optJSONObject(index)?.let(remote::put)
            cursor = page.optString("nextCursor").takeIf { it.isNotBlank() && it != "null" }.orEmpty()
        } while (cursor.isNotBlank())
        var local = dao.allItems()
        fun firebaseId(tags: String) = tags.split(";").firstOrNull { it.startsWith("firebase_id:") }?.substringAfter("firebase_id:")?.takeIf { it.isNotBlank() }
        fun tagged(tags: String, id: String): String =
            (tags.split(";").filter { it.isNotBlank() && !it.startsWith("firebase_id:") } + "firebase_id:$id").joinToString(";")
        fun restoreAttachment(payload: JSONObject, id: String, currentUri: String): String {
            val attachmentId = payload.optString("attachmentId")
            if (attachmentId.isBlank()) return currentUri
            return runCatching {
                val response = attachmentRequest(targetProjectId, idToken, "GET", attachmentId)
                val encoded = response.optString("base64")
                if (encoded.isBlank()) return@runCatching currentUri
                val folder = File(context.filesDir, "attachments").apply { mkdirs() }
                val name = response.optString("name", "attachment").substringAfterLast('/').replace(Regex("[^A-Za-z0-9._-]"), "_")
                val target = File(folder, "firebase_${id}_${name}")
                target.writeBytes(android.util.Base64.decode(encoded, android.util.Base64.DEFAULT))
                target.absolutePath
            }.getOrDefault(currentUri)
        }
        val ids = mutableMapOf<Long, String>()
        local.forEach { item ->
            val id = firebaseId(item.tags) ?: UUID.randomUUID().toString().lowercase()
            ids[item.id] = id
            if (firebaseId(item.tags) != id) dao.update(item.copy(tags = tagged(item.tags, id)))
        }
        local = dao.allItems()
        val localByFirebase = local.mapNotNull { item -> firebaseId(item.tags)?.let { it to item } }.toMap().toMutableMap()
        var inserted = 0
        var changed = 0
        val pending = (0 until remote.length()).mapNotNull { remote.optJSONObject(it) }.toMutableList()
        var progressed = true
        while (pending.isNotEmpty() && progressed) {
            progressed = false
            val iterator = pending.iterator()
            while (iterator.hasNext()) {
                val row = iterator.next()
                val id = row.optString("id")
                val payload = row.optJSONObject("payload")
                if (payload == null) { iterator.remove(); continue }
                val parentFirebase = payload.optString("parentFirebaseId").takeIf { it.isNotBlank() && it != "null" }
                if (parentFirebase != null && !localByFirebase.containsKey(parentFirebase)) continue
                val parentLocal = parentFirebase?.let { localByFirebase[it]?.id }
                val remoteUpdated = payload.optLong("updatedAt", row.optLong("updatedAt"))
                val current = localByFirebase[id]
                if (current != null) {
                    if (remoteUpdated > current.updatedAt) {
                        val updated = current.copy(
                            parentId = parentLocal,
                            kind = payload.optString("kind", current.kind),
                            title = payload.optString("title", current.title),
                            body = payload.optString("body", current.body),
                            uri = restoreAttachment(payload, id, current.uri),
                            tags = tagged(payload.optString("tags", current.tags), id),
                            bookmarked = payload.optBoolean("bookmarked", current.bookmarked),
                            position = payload.optInt("position", current.position),
                            createdAt = payload.optLong("createdAt", current.createdAt),
                            updatedAt = remoteUpdated,
                            deletedAt = if (payload.isNull("deletedAt")) null else payload.optLong("deletedAt")
                        )
                        dao.update(updated)
                        localByFirebase[id] = updated
                        changed++
                    }
                } else {
                    val created = ScribeItem(parentId = parentLocal, kind = payload.optString("kind", "block"),
                        title = payload.optString("title", "Synced note"), body = payload.optString("body"),
                        uri = restoreAttachment(payload, id, ""), tags = tagged(payload.optString("tags"), id), bookmarked = payload.optBoolean("bookmarked"),
                        position = payload.optInt("position"), createdAt = payload.optLong("createdAt", System.currentTimeMillis()),
                        updatedAt = remoteUpdated, deletedAt = if (payload.isNull("deletedAt")) null else payload.optLong("deletedAt"))
                    val localId = dao.insert(created)
                    val saved = created.copy(id = localId)
                    localByFirebase[id] = saved
                    inserted++
                }
                val target = localByFirebase[id]
                val history = payload.optJSONArray("history")
                if (target != null && history != null) {
                    val existing = dao.snapshots(target.id).first()
                    val combined = (existing + (0 until history.length()).mapNotNull { index ->
                        val entry = history.optJSONObject(index) ?: return@mapNotNull null
                        NoteSnapshot(itemId = target.id, title = entry.optString("title"), body = entry.optString("body"), savedAt = entry.optLong("savedAt"))
                    }).distinctBy { it.savedAt }.sortedByDescending { it.savedAt }.take(30)
                    dao.deleteSnapshotsForItem(target.id)
                    if (combined.isNotEmpty()) dao.insertSnapshots(combined.map { it.copy(id = 0) })
                }
                iterator.remove()
                progressed = true
            }
        }
        if (pending.isNotEmpty()) error("Firebase sync stopped safely because some parent records are missing.")
        local = dao.allItems()
        val records = JSONArray()
        for (item in local) {
            val id = ids[item.id] ?: firebaseId(item.tags) ?: UUID.randomUUID().toString().lowercase()
            ids[item.id] = id
            val parentFirebase = item.parentId?.let { parentId -> local.firstOrNull { it.id == parentId }?.tags?.let(::firebaseId) }
            val history = JSONArray()
            dao.snapshots(item.id).first().forEach { snapshot ->
                history.put(JSONObject().put("title", snapshot.title).put("body", snapshot.body).put("savedAt", snapshot.savedAt))
            }
            val payload = JSONObject().put("kind", item.kind).put("title", item.title).put("body", item.body)
                .put("tags", tagged(item.tags, id)).put("bookmarked", item.bookmarked).put("position", item.position)
                .put("createdAt", item.createdAt).put("updatedAt", item.updatedAt)
                .put("deletedAt", item.deletedAt ?: JSONObject.NULL)
                .put("parentFirebaseId", parentFirebase ?: JSONObject.NULL).put("history", history)
            if (item.uri.isNotBlank()) {
                val file = File(item.uri)
                if (file.isFile && file.length() <= 20L * 1024L * 1024L) {
                    val fingerprint = "${file.length()}_${file.lastModified()}"
                    val storedFingerprint = item.tags.split(";").firstOrNull { it.startsWith("firebase_attachment_fingerprint:") }
                        ?.substringAfter("firebase_attachment_fingerprint:")
                    if (storedFingerprint != fingerprint) {
                        uploadAttachment(targetProjectId, idToken, id, file)
                        val updatedTags = item.tags.split(";").filter {
                            it.isNotBlank() && !it.startsWith("firebase_attachment_fingerprint:")
                        } + "firebase_attachment_fingerprint:$fingerprint"
                        dao.update(item.copy(tags = updatedTags.joinToString(";")))
                    }
                    payload.put("attachmentId", id).put("attachmentName", file.name).put("attachmentFingerprint", fingerprint)
                } else if (file.isFile) payload.put("attachmentOmitted", true)
            }
            records.put(JSONObject().put("id", id).put("updatedAt", item.updatedAt).put("payload", payload))
        }
        var uploaded = 0
        var start = 0
        while (start < records.length()) {
            val batch = JSONArray()
            for (i in start until minOf(start + 100, records.length())) batch.put(records.getJSONObject(i))
            val result = request(root, "POST", JSONObject().put("records", batch).toString(), idToken)
            uploaded += result.optInt("saved")
            start += 100
        }
        ScribeFirebaseSession.read(context)?.let { saved ->
            ScribeFirebaseSession.save(context, saved.apiKey, saved.projectId, saved.email, refreshToken)
        }
        return "Firebase sync complete: $inserted added, $changed updated, $uploaded uploaded."
    }
}

internal class ScribeFirebaseSyncWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = try {
        val saved = ScribeFirebaseSession.read(applicationContext) ?: return Result.success()
        ScribeFirebaseClient(applicationContext, ScribeDatabase.get(applicationContext).dao(), saved.apiKey, saved.projectId).syncSaved()
        Result.success()
    } catch (_: Throwable) { Result.retry() }
}

package com.mrjdm.scribe.data

import android.content.Context
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.UUID

/**
 * Supabase Auth + PostgREST sync. Records use stable cloud_id tags while Room keeps
 * its own local integer primary keys. UpdatedAt is the last-writer-wins conflict clock.
 */
internal class ScribeSupabaseSync(
    private val context: Context,
    private val dao: ScribeDao
) {
    suspend fun sync(url: String, anonKey: String, email: String, password: String = "", refreshToken: String? = null): String =
        withContext(Dispatchers.IO) {
            require(url.startsWith("https://")) { "Enter a valid HTTPS Supabase project URL." }
            require(anonKey.isNotBlank() && (!refreshToken.isNullOrBlank() || (email.isNotBlank() && password.isNotBlank()))) {
                "Enter the Supabase anon key and sign in with your account."
            }
            val root = url.trimEnd('/')
            fun request(path: String, method: String, body: String? = null, token: String? = null,
                        prefer: String? = null): String {
                val connection = URL("$root/$path").openConnection() as HttpURLConnection
                try {
                    connection.requestMethod = method
                    connection.connectTimeout = 20_000
                    connection.readTimeout = 30_000
                    connection.setRequestProperty("apikey", anonKey)
                    connection.setRequestProperty("Content-Type", "application/json")
                    if (token != null) connection.setRequestProperty("Authorization", "Bearer $token")
                    if (prefer != null) connection.setRequestProperty("Prefer", prefer)
                    if (body != null) {
                        connection.doOutput = true
                        connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                    }
                    val code = connection.responseCode
                    val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                    val response = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                    if (code !in 200..299) error("Supabase request failed ($code): $response")
                    return response
                } finally { connection.disconnect() }
            }
            val authBody = if (!refreshToken.isNullOrBlank()) {
                JSONObject().put("refresh_token", refreshToken).toString()
            } else {
                JSONObject().put("email", email).put("password", password).toString()
            }
            val grant = if (!refreshToken.isNullOrBlank()) "refresh_token" else "password"
            val auth = JSONObject(request("auth/v1/token?grant_type=$grant", "POST", authBody))
            val token = auth.getString("access_token")
            val userId = auth.getJSONObject("user").getString("id")
            val nextRefreshToken = auth.optString("refresh_token").takeIf { it.isNotBlank() }
            if (nextRefreshToken != null) {
                ScribeCloudSession.save(context, root, anonKey, email, nextRefreshToken)
                if (refreshToken.isNullOrBlank()) ScribeCloudSession.schedule(context)
            }
            fun requestBytes(path: String, method: String, body: ByteArray? = null, token: String? = null,
                             contentType: String = "application/octet-stream", upsert: Boolean = false): ByteArray {
                val connection = URL("$root/$path").openConnection() as HttpURLConnection
                try {
                    connection.requestMethod = method
                    connection.connectTimeout = 20_000
                    connection.readTimeout = 60_000
                    connection.setRequestProperty("apikey", anonKey)
                    connection.setRequestProperty("Content-Type", contentType)
                    if (token != null) connection.setRequestProperty("Authorization", "Bearer $token")
                    if (upsert) connection.setRequestProperty("x-upsert", "true")
                    if (body != null) { connection.doOutput = true; connection.outputStream.use { it.write(body) } }
                    val code = connection.responseCode
                    val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                    val response = stream?.use { it.readBytes() } ?: ByteArray(0)
                    if (code !in 200..299) error("Supabase storage request failed ($code): " + String(response, Charsets.UTF_8))
                    return response
                } finally { connection.disconnect() }
            }
            suspend fun storeAttachment(file: File, cloud: String): String {
                val name = URLEncoder.encode(file.name, "UTF-8").replace("+", "%20")
                val objectPath = "$userId/$cloud/$name"
                requestBytes("storage/v1/object/scribe-attachments/$objectPath", "POST", file.readBytes(), token, upsert = true)
                return objectPath
            }
            suspend fun restoreAttachment(data: JSONObject, cloud: String): String {
                val folder = File(context.filesDir, "attachments").apply { mkdirs() }
                val target = File(folder, "cloud_$cloud")
                val objectPath = data.optString("attachmentPath")
                val bytes = if (objectPath.isNotBlank()) {
                    requestBytes("storage/v1/object/authenticated/scribe-attachments/$objectPath", "GET", token = token)
                } else {
                    val encoded = data.optString("attachmentBase64")
                    if (encoded.isBlank()) return ""
                    Base64.decode(encoded, Base64.DEFAULT)
                }
                target.writeBytes(bytes)
                return target.absolutePath
            }

            fun cloudId(tags: String): String? = tags.split(";")
                .firstOrNull { it.startsWith("cloud_id:") }?.substringAfter("cloud_id:")?.takeIf { it.isNotBlank() }
            fun withCloudId(tags: String, id: String): String =
                (tags.split(";").filter { it.isNotBlank() && !it.startsWith("cloud_id:") } + "cloud_id:$id").joinToString(";")

            dao.pendingCloudDeletions().forEach { deletion ->
                request("rest/v1/rpc/scribe_delete_item", "POST",
                    JSONObject().put("p_cloud_id", deletion.cloudId).put("p_deleted_at", deletion.deletedAt).toString(), token)
                dao.clearCloudDeletion(deletion.cloudId)
            }
            var local = dao.allItems()
            val ids = mutableMapOf<Long, String>()
            local.forEach { item ->
                val id = cloudId(item.tags) ?: UUID.randomUUID().toString()
                ids[item.id] = id
                if (cloudId(item.tags) != id) dao.update(item.copy(tags = withCloudId(item.tags, id)))
            }
            local = dao.allItems()

            suspend fun payload(item: ScribeItem): JSONObject {
                val cloud = ids[item.id] ?: cloudId(item.tags) ?: UUID.randomUUID().toString()
                val parentCloud = item.parentId?.let { ids[it] }
                val json = JSONObject()
                    .put("cloudId", cloud)
                    .put("parentCloudId", parentCloud ?: JSONObject.NULL)
                    .put("kind", item.kind).put("title", item.title).put("body", item.body)
                    .put("tags", withCloudId(item.tags, cloud)).put("bookmarked", item.bookmarked)
                    .put("position", item.position).put("createdAt", item.createdAt)
                    .put("updatedAt", item.updatedAt).put("deletedAt", item.deletedAt ?: JSONObject.NULL)
                val history = JSONArray()
                dao.snapshots(item.id).first().forEach { snapshot ->
                    history.put(JSONObject().put("title", snapshot.title).put("body", snapshot.body).put("savedAt", snapshot.savedAt))
                }
                json.put("history", history)
                if (item.uri.isNotBlank()) {
                    val file = File(item.uri)
                    if (file.isFile) {
                        json.put("attachmentName", file.name)
                        if (file.length() <= 8L * 1024L * 1024L) {
                            json.put("attachmentBase64", Base64.encodeToString(file.readBytes(), Base64.NO_WRAP))
                        } else {
                            json.put("attachmentPath", storeAttachment(file, cloud))
                        }
                    }
                }
                return json
            }
            suspend fun upload(items: List<ScribeItem>) {
                items.forEach { item ->
                    val cloud = ids[item.id] ?: cloudId(item.tags) ?: return@forEach
                    val row = JSONObject().put("p_cloud_id", cloud)
                        .put("p_updated_at", item.updatedAt).put("p_payload", payload(item))
                    request("rest/v1/rpc/scribe_upsert_item", "POST", row.toString(), token)
                }
            }
            val remote = JSONArray(request("rest/v1/scribe_cloud_items?select=cloud_id,payload,updated_at", "GET", token = token))
            val localByCloud = dao.allItems().mapNotNull { item -> cloudId(item.tags)?.let { it to item } }.toMap().toMutableMap()
            val tombstones = JSONArray(request("rest/v1/scribe_cloud_tombstones?select=cloud_id", "GET", token = token))
            val allLocal = dao.allItems()
            val byCloud = allLocal.mapNotNull { item -> cloudId(item.tags)?.let { it to item } }.toMap()
            for (index in 0 until tombstones.length()) {
                val cloud = tombstones.optJSONObject(index)?.optString("cloud_id").orEmpty()
                val target = byCloud[cloud] ?: continue
                val tree = allLocal.associateBy { it.id }
                suspend fun removeRemoteTree(id: Long) {
                    tree.values.filter { it.parentId == id }.forEach { removeRemoteTree(it.id) }
                    dao.deleteSnapshotsForItem(id)
                    dao.deleteId(id)
                }
                removeRemoteTree(target.id)
            }
            val pending = (0 until remote.length()).mapNotNull { remote.optJSONObject(it) }.toMutableList()
            suspend fun mergeHistory(itemId: Long, data: JSONObject) {
                val remoteHistory = data.optJSONArray("history") ?: return
                val combined = (dao.snapshots(itemId).first() + (0 until remoteHistory.length()).mapNotNull { index ->
                    val entry = remoteHistory.optJSONObject(index) ?: return@mapNotNull null
                    NoteSnapshot(itemId = itemId, title = entry.optString("title"),
                        body = entry.optString("body"), savedAt = entry.optLong("savedAt"))
                }).distinctBy { it.savedAt }.sortedByDescending { it.savedAt }.take(30)
                dao.deleteSnapshotsForItem(itemId)
                if (combined.isNotEmpty()) dao.insertSnapshots(combined.map { it.copy(id = 0) })
            }
            var inserted = 0
            var changed = 0
            var progress = true
            while (pending.isNotEmpty() && progress) {
                progress = false
                val iterator = pending.iterator()
                while (iterator.hasNext()) {
                    val row = iterator.next()
                    val cloud = row.optString("cloud_id")
                    val data = row.optJSONObject("payload")
                    if (data == null) { iterator.remove(); continue }
                    val parentCloud = data.optString("parentCloudId").takeIf { it.isNotBlank() && it != "null" }
                    if (parentCloud != null && !localByCloud.containsKey(parentCloud)) continue
                    val parentLocal = parentCloud?.let { localByCloud[it]?.id }
                    val current = localByCloud[cloud]
                    val remoteUpdated = data.optLong("updatedAt", row.optLong("updated_at"))
                    if (current != null) {
                        if (remoteUpdated > current.updatedAt) {
                            var uri = current.uri
                            if ((uri.isBlank() || !File(uri).isFile) &&
                                (data.optString("attachmentPath").isNotBlank() || data.optString("attachmentBase64").isNotBlank())) {
                                uri = restoreAttachment(data, cloud)
                            }
                            val updated = current.copy(parentId = parentLocal, kind = data.optString("kind", current.kind),
                                title = data.optString("title", current.title), body = data.optString("body", current.body),
                                uri = uri, tags = withCloudId(data.optString("tags", current.tags), cloud),
                                bookmarked = data.optBoolean("bookmarked", current.bookmarked),
                                position = data.optInt("position", current.position),
                                createdAt = data.optLong("createdAt", current.createdAt),
                                updatedAt = remoteUpdated,
                                deletedAt = if (data.isNull("deletedAt")) null else data.optLong("deletedAt"))
                            dao.update(updated)
                            localByCloud[cloud] = updated
                            changed++
                        }
                    } else {
                        val uri = if (data.optString("attachmentPath").isNotBlank() ||
                            data.optString("attachmentBase64").isNotBlank()) restoreAttachment(data, cloud) else ""
                        val newItem = ScribeItem(parentId = parentLocal, kind = data.optString("kind", "block"),
                            title = data.optString("title", "Synced note"), body = data.optString("body"),
                            uri = uri, tags = withCloudId(data.optString("tags"), cloud),
                            bookmarked = data.optBoolean("bookmarked"), position = data.optInt("position"),
                            createdAt = data.optLong("createdAt", System.currentTimeMillis()),
                            updatedAt = remoteUpdated,
                            deletedAt = if (data.isNull("deletedAt")) null else data.optLong("deletedAt"))
                        val id = dao.insert(newItem)
                        localByCloud[cloud] = newItem.copy(id = id)
                        inserted++
                    }
                    localByCloud[cloud]?.let { mergeHistory(it.id, data) }
                    iterator.remove()
                    progress = true
                }
            }
            if (pending.isNotEmpty()) {
                error("Sync stopped safely: ${pending.size} cloud record(s) have missing parent records. No incomplete sync was reported as successful.")
            }
            // Re-upload the merged local view so records pulled from another device are
            // present in this device's next sync cycle as well.
            local = dao.allItems()
            ids.clear()
            local.forEach { item -> cloudId(item.tags)?.let { ids[item.id] = it } }
            upload(local)
            "Synced $inserted new and $changed updated records, including attachments."
        }
}

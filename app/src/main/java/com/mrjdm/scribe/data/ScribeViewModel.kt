package com.mrjdm.scribe.data

import android.app.Application
import androidx.room.withTransaction
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

internal fun independentCopyTags(tags: String): String =
    tags.split(";").filter { it.isNotBlank() && !it.startsWith("cloud_id:") }.joinToString(";")

internal fun belongsToTrashBatch(candidateDeletedAt: Long?, rootDeletedAt: Long?): Boolean {
    if (candidateDeletedAt == null || rootDeletedAt == null) return false
    return kotlin.math.abs(candidateDeletedAt.toDouble() - rootDeletedAt.toDouble()) <= 5_000.0
}

internal fun shouldSnapshotBody(previous: String, next: String, latestSnapshotAt: Long, now: Long): Boolean =
    previous != next && (latestSnapshotAt <= 0L || now - latestSnapshotAt >= 60_000L)

internal fun appendTag(existingTags: String, rawTag: String): String {
    val clean = rawTag.trim().replace(";", " ").split(Regex("\\s+")).filter { it.isNotBlank() }.joinToString(" ")
    if (clean.isEmpty()) return existingTags
    val tags = existingTags.split(";").filter { it.isNotBlank() }
    return if (clean in tags) tags.joinToString(";") else (tags + clean).joinToString(";")
}

internal fun validateBackupItems(items: List<ScribeItem>): String? {
    if (items.any { it.id <= 0L }) return "Backup contains an invalid item ID."
    val ids = items.map { it.id }
    if (ids.distinct().size != ids.size) return "Backup contains duplicate item IDs."
    val byId = items.associateBy { it.id }
    for (item in items) {
        if (item.parentId != null && item.parentId !in byId) return "Backup contains an item with a missing parent."
        val visited = mutableSetOf<Long>()
        var cursor: Long? = item.id
        while (cursor != null) {
            if (!visited.add(cursor)) return "Backup contains a circular notebook hierarchy."
            cursor = byId[cursor]?.parentId
        }
    }
    return null
}

class ScribeViewModel(app: Application) : AndroidViewModel(app) {
    private val database = ScribeDatabase.get(app)
    private val dao = database.dao()
    fun children(parentId: Long?) = dao.children(parentId)
    fun kind(kind: String) = dao.byKind(kind)
    val bookmarks = dao.bookmarks()
    val trash = dao.trash()
    fun search(query: String) = dao.search(query)
    fun snapshots(id: Long) = dao.snapshots(id)
    suspend fun get(id: Long) = dao.byId(id)
    fun observe(id: Long) = dao.observeById(id)
    suspend fun all() = dao.allItems()
    fun observeAllItems() = dao.observeAllItems()
    suspend fun syncSupabase(url: String, anonKey: String, email: String, password: String): String =
        ScribeSupabaseSync(getApplication(), dao).sync(url, anonKey, email, password)

    fun add(parentId: Long?, kind: String, title: String, body: String = "", uri: String = "", tags: String = "", done: (Long) -> Unit = {}) {
        viewModelScope.launch {
            val count = dao.allItems().count { it.parentId == parentId && it.deletedAt == null }
            val id = dao.insert(ScribeItem(parentId = parentId, kind = kind, title = title.ifBlank { defaultTitle(kind) }, body = body, uri = uri, tags = tags, position = count))
            done(id)
        }
    }
    fun update(item: ScribeItem, title: String? = null, body: String? = null, uri: String? = null, tags: String? = null, bookmarked: Boolean? = null) {
        viewModelScope.launch {
            database.withTransaction {
                val current = dao.byId(item.id) ?: return@withTransaction
                val nextBody = body ?: current.body
                val now = maxOf(System.currentTimeMillis(), current.updatedAt + 1)
                if (current.body != nextBody) {
                    val latestSnapshotAt = dao.snapshots(current.id).first().firstOrNull()?.savedAt ?: 0L
                    if (shouldSnapshotBody(current.body, nextBody, latestSnapshotAt, now)) {
                        dao.snapshot(NoteSnapshot(itemId = current.id, title = current.title, body = current.body, savedAt = now))
                    }
                }
                dao.update(current.copy(
                    title = title ?: current.title,
                    body = nextBody,
                    uri = uri ?: current.uri,
                    tags = tags ?: current.tags,
                    bookmarked = bookmarked ?: current.bookmarked,
                    updatedAt = now
                ))
            }
        }
    }
    fun removeTree(item: ScribeItem) {
        viewModelScope.launch {
            database.withTransaction {
            val all = dao.allItems()
            val deletedAt = System.currentTimeMillis()
            suspend fun mark(id: Long) {
                all.filter { it.parentId == id && it.deletedAt == null }.forEach { mark(it.id) }
                dao.byId(id)?.let { dao.update(it.copy(deletedAt = deletedAt, updatedAt = deletedAt)) }
            }
            mark(item.id)
            }
        }
    }
    fun restoreTree(item: ScribeItem) {
        viewModelScope.launch {
            database.withTransaction {
            val all = dao.allItems()
            val rootDeletedAt = dao.byId(item.id)?.deletedAt ?: item.deletedAt ?: return@withTransaction
            suspend fun restore(id: Long) {
                val current = dao.byId(id) ?: return
                if (!belongsToTrashBatch(current.deletedAt, rootDeletedAt)) return
                dao.update(current.copy(deletedAt = null, updatedAt = System.currentTimeMillis()))
                all.filter { it.parentId == id && belongsToTrashBatch(it.deletedAt, rootDeletedAt) }
                    .forEach { restore(it.id) }
            }
            restore(item.id)
            }
        }
    }
    fun deleteForever(item: ScribeItem) {
        viewModelScope.launch {
            database.withTransaction {
            val all = dao.allItems()
            suspend fun remove(id: Long) {
                all.filter { it.parentId == id }.forEach { remove(it.id) }
                all.firstOrNull { it.id == id }?.tags?.split(";")
                    ?.firstOrNull { it.startsWith("cloud_id:") }
                    ?.substringAfter("cloud_id:")?.takeIf { it.isNotBlank() }
                    ?.let { dao.enqueueCloudDeletion(CloudDeletion(it)) }
                dao.deleteSnapshotsForItem(id)
                dao.deleteId(id)
            }
            remove(item.id)
            }
        }
    }
    fun duplicate(item: ScribeItem) {
        viewModelScope.launch {
            val all = dao.allItems().filter { it.deletedAt == null }
            suspend fun clone(source: ScribeItem, newParentId: Long?, isRoot: Boolean): Long {
                val position = dao.children(newParentId).first().size
                val newId = dao.insert(source.copy(id = 0, parentId = newParentId,
                    title = if (isRoot) source.title + " copy" else source.title,
                    tags = independentCopyTags(source.tags),
                    position = position, createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(), deletedAt = null))
                all.filter { it.parentId == source.id }.sortedBy { it.position }.forEach { child ->
                    clone(child, newId, false)
                }
                return newId
            }
            clone(item, item.parentId, true)
        }
    }
    fun move(item: ScribeItem, direction: Int) {
        viewModelScope.launch {
            val siblings = dao.children(item.parentId).first().sortedBy { it.position }
            if (siblings.isEmpty()) return@launch
            val from = siblings.indexOfFirst { it.id == item.id }
            if (from < 0) return@launch
            val to = (from + direction).coerceIn(0, siblings.lastIndex)
            if (to != from) {
                val moving = siblings[from]
                val displaced = siblings[to]
                dao.update(moving.copy(position = displaced.position, updatedAt = System.currentTimeMillis()))
                dao.update(displaced.copy(position = moving.position, updatedAt = System.currentTimeMillis()))
            }
        }
    }
    fun mergePageWithNext(page: ScribeItem) {
        if (page.kind != "page") return
        viewModelScope.launch {
            val siblings = dao.children(page.parentId).first().sortedBy { it.position }
            val index = siblings.indexOfFirst { it.id == page.id }
            if (index < 0) return@launch
            val next = siblings.getOrNull(index + 1) ?: return@launch
            val existing = dao.children(page.id).first()
            val incoming = dao.children(next.id).first()
            database.withTransaction {
                var position = existing.size
                incoming.forEach { block ->
                    dao.update(block.copy(parentId = page.id, position = position++, updatedAt = System.currentTimeMillis()))
                }
                dao.update(next.copy(deletedAt = System.currentTimeMillis()))
            }
        }
    }

    fun splitPage(page: ScribeItem) {
        if (page.kind != "page") return
        viewModelScope.launch {
            val blocks = dao.children(page.id).first().sortedBy { it.position }
            if (blocks.size < 2) return@launch
            val siblings = dao.children(page.parentId).first()
            database.withTransaction {
                val now = System.currentTimeMillis()
                val newPageId = dao.insert(page.copy(
                    id = 0, title = page.title + " · Part 2", body = "",
                    position = siblings.size, bookmarked = false,
                    createdAt = now, updatedAt = now, deletedAt = null
                ))
                blocks.drop((blocks.size + 1) / 2).forEachIndexed { index, block ->
                    dao.update(block.copy(parentId = newPageId, position = index, updatedAt = now))
                }
            }
        }
    }

    fun toggleBookmark(item: ScribeItem) {
        viewModelScope.launch {
            database.withTransaction {
                val current = dao.byId(item.id) ?: return@withTransaction
                dao.update(current.copy(
                    bookmarked = !current.bookmarked,
                    updatedAt = maxOf(System.currentTimeMillis(), current.updatedAt + 1)
                ))
            }
        }
    }
    suspend fun restore(items: List<ScribeItem>, snapshots: List<NoteSnapshot> = emptyList()) {
        dao.replaceLibrary(items, snapshots)
    }
    fun bulkMove(items: List<ScribeItem>, targetParentId: Long?) {
        viewModelScope.launch {
            database.withTransaction {
                val all = dao.allItems()
                val movingIds = items.map { it.id }.toSet()
                if (targetParentId != null) {
                    var cursor: Long? = targetParentId
                    val visited = mutableSetOf<Long>()
                    while (cursor != null && visited.add(cursor)) {
                        if (cursor in movingIds) return@withTransaction
                        cursor = all.firstOrNull { it.id == cursor }?.parentId
                    }
                }
                var position = dao.children(targetParentId).first().size
                items.distinctBy { it.id }.forEach { selected ->
                    val current = dao.byId(selected.id) ?: return@forEach
                    if (current.deletedAt == null) {
                        dao.update(current.copy(parentId = targetParentId, position = position++,
                            updatedAt = maxOf(System.currentTimeMillis(), current.updatedAt + 1)))
                    }
                }
            }
        }
    }
    fun bulkArchive(items: List<ScribeItem>) {
        viewModelScope.launch {
            database.withTransaction {
                val all = dao.allItems()
                val deletedAt = System.currentTimeMillis()
                suspend fun markTree(id: Long) {
                    all.filter { it.parentId == id && it.deletedAt == null }.forEach { markTree(it.id) }
                    dao.byId(id)?.takeIf { it.deletedAt == null }?.let {
                        dao.update(it.copy(deletedAt = deletedAt, updatedAt = deletedAt))
                    }
                }
                items.distinctBy { it.id }.forEach { if (it.deletedAt == null) markTree(it.id) }
            }
        }
    }
    fun bulkRestore(items: List<ScribeItem>) {
        viewModelScope.launch {
            database.withTransaction {
                val all = dao.allItems()
                suspend fun restoreBatch(id: Long, rootDeletedAt: Long) {
                    val current = dao.byId(id) ?: return
                    if (!belongsToTrashBatch(current.deletedAt, rootDeletedAt)) return
                    dao.update(current.copy(deletedAt = null, updatedAt = System.currentTimeMillis()))
                    all.filter { it.parentId == id && belongsToTrashBatch(it.deletedAt, rootDeletedAt) }
                        .forEach { restoreBatch(it.id, rootDeletedAt) }
                }
                items.distinctBy { it.id }.forEach { selected ->
                    val deletedAt = dao.byId(selected.id)?.deletedAt ?: selected.deletedAt ?: return@forEach
                    restoreBatch(selected.id, deletedAt)
                }
            }
        }
    }
    fun bulkTag(items: List<ScribeItem>, tag: String) {
        val clean = tag.trim().replace(";", " ")
        if (clean.isEmpty()) return
        viewModelScope.launch {
            database.withTransaction {
                items.distinctBy { it.id }.forEach { item ->
                    val current = dao.byId(item.id) ?: return@forEach
                    val nextTags = appendTag(current.tags, clean)
                    if (nextTags != current.tags) {
                        dao.update(current.copy(tags = nextTags, updatedAt = maxOf(System.currentTimeMillis(), current.updatedAt + 1)))
                    }
                }
            }
        }
    }
    fun bulkDeleteForever(items: List<ScribeItem>) {
        viewModelScope.launch {
            database.withTransaction {
                val all = dao.allItems()
                suspend fun deleteTree(id: Long) {
                    all.filter { it.parentId == id }.forEach { deleteTree(it.id) }
                    all.firstOrNull { it.id == id }?.tags?.split(";")
                        ?.firstOrNull { it.startsWith("cloud_id:") }
                        ?.substringAfter("cloud_id:")?.takeIf { it.isNotBlank() }
                        ?.let { dao.enqueueCloudDeletion(CloudDeletion(it)) }
                    dao.deleteSnapshotsForItem(id)
                    dao.deleteId(id)
                }
                items.distinctBy { it.id }.forEach { deleteTree(it.id) }
            }
        }
    }

    companion object {
        fun defaultTitle(kind: String) = when(kind) {
            "course" -> "Untitled course"; "chapter" -> "Untitled chapter"; "topic" -> "Untitled topic"
            "page" -> "Untitled page"; "block" -> "New block"; else -> "Untitled"
        }
    }
}

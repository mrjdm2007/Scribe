package com.mrjdm.scribe.data

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class ScribeViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = ScribeDatabase.get(app).dao()
    fun children(parentId: Long?) = dao.children(parentId)
    fun kind(kind: String) = dao.byKind(kind)
    val bookmarks = dao.bookmarks()
    val trash = dao.trash()
    fun search(query: String) = dao.search(query)
    fun snapshots(id: Long) = dao.snapshots(id)
    suspend fun get(id: Long) = dao.byId(id)
    suspend fun all() = dao.allItems()

    fun add(parentId: Long?, kind: String, title: String, body: String = "", uri: String = "", tags: String = "", done: (Long) -> Unit = {}) {
        viewModelScope.launch {
            val count = dao.allItems().count { it.parentId == parentId && it.deletedAt == null }
            val id = dao.insert(ScribeItem(parentId = parentId, kind = kind, title = title.ifBlank { defaultTitle(kind) }, body = body, uri = uri, tags = tags, position = count))
            done(id)
        }
    }
    fun update(item: ScribeItem, title: String = item.title, body: String = item.body, uri: String = item.uri, tags: String = item.tags, bookmarked: Boolean = item.bookmarked) {
        viewModelScope.launch {
            if (item.kind == "block" && item.body != body) dao.snapshot(NoteSnapshot(itemId = item.id, title = item.title, body = item.body))
            dao.update(item.copy(title = title, body = body, uri = uri, tags = tags, bookmarked = bookmarked, updatedAt = System.currentTimeMillis()))
        }
    }
    fun removeTree(item: ScribeItem) {
        viewModelScope.launch {
            val all = dao.allItems()
            suspend fun mark(id: Long) {
                all.filter { it.parentId == id && it.deletedAt == null }.forEach { mark(it.id) }
                dao.byId(id)?.let { dao.update(it.copy(deletedAt = System.currentTimeMillis())) }
            }
            mark(item.id)
        }
    }
    fun restoreTree(item: ScribeItem) {
        viewModelScope.launch {
            val all = dao.allItems()
            suspend fun restore(id: Long) {
                dao.byId(id)?.let { dao.update(it.copy(deletedAt = null)) }
                all.filter { it.parentId == id && it.deletedAt != null }.forEach { restore(it.id) }
            }
            restore(item.id)
        }
    }
    fun deleteForever(item: ScribeItem) {
        viewModelScope.launch {
            val all = dao.allItems()
            suspend fun remove(id: Long) {
                all.filter { it.parentId == id }.forEach { remove(it.id) }
                dao.deleteSnapshotsForItem(id)
                dao.deleteId(id)
            }
            remove(item.id)
        }
    }
    fun duplicate(item: ScribeItem) {
        viewModelScope.launch {
            val all = dao.allItems().filter { it.deletedAt == null }
            suspend fun clone(source: ScribeItem, newParentId: Long?, isRoot: Boolean): Long {
                val position = dao.children(newParentId).first().size
                val newId = dao.insert(source.copy(id = 0, parentId = newParentId,
                    title = if (isRoot) source.title + " copy" else source.title,
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

    fun toggleBookmark(item: ScribeItem) = update(item, bookmarked = !item.bookmarked)
    suspend fun restore(items: List<ScribeItem>, snapshots: List<NoteSnapshot> = emptyList()) {
        dao.replaceLibrary(items, snapshots)
    }
    companion object {
        fun defaultTitle(kind: String) = when(kind) {
            "course" -> "Untitled course"; "chapter" -> "Untitled chapter"; "topic" -> "Untitled topic"
            "page" -> "Untitled page"; "block" -> "New block"; else -> "Untitled"
        }
    }
}

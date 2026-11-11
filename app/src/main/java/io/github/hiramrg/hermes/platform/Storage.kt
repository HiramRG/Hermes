package io.github.hiramrg.hermes.platform

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.provider.OpenableColumns

/** A file found inside a folder the user granted. */
data class DocFile(
    val uri: Uri,
    val name: String,
    val parentId: String,
    val lastModified: Long,
    val size: Long,
) {
    val baseName: String get() = name.substringBeforeLast('.').lowercase()
    val extension: String get() = name.substringAfterLast('.', "").lowercase()
}

/** Storage Access Framework helpers: no storage permission or root needed. */
class Storage(context: Context) {
    private val resolver: ContentResolver = context.contentResolver

    fun takePermission(uri: Uri, write: Boolean) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or (if (write) Intent.FLAG_GRANT_WRITE_URI_PERMISSION else 0)
        runCatching { resolver.takePersistableUriPermission(uri, flags) }
    }

    fun hasPermission(uri: Uri): Boolean =
        resolver.persistedUriPermissions.any { it.uri == uri && it.isReadPermission }

    fun readBytes(uri: Uri, limit: Int = Int.MAX_VALUE): ByteArray? = runCatching {
        resolver.openInputStream(uri)?.use { input ->
            if (limit == Int.MAX_VALUE) input.readBytes()
            else {
                val buf = ByteArray(limit)
                var read = 0
                while (read < limit) {
                    val n = input.read(buf, read, limit - read)
                    if (n < 0) break
                    read += n
                }
                buf.copyOf(read)
            }
        }
    }.getOrNull()

    /** Replaces the whole file. "wt" truncates; some providers only know "w" (fine for same-size saves). */
    fun writeBytes(uri: Uri, bytes: ByteArray): Boolean {
        for (mode in listOf("wt", "w")) {
            val ok = runCatching {
                resolver.openOutputStream(uri, mode)?.use { it.write(bytes); it.flush() } != null
            }.getOrDefault(false)
            if (ok) return true
        }
        return false
    }

    fun lastModified(uri: Uri): Long = runCatching {
        resolver.query(uri, arrayOf(Document.COLUMN_LAST_MODIFIED), null, null, null)?.use { c ->
            if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else 0L
        } ?: 0L
    }.getOrDefault(0L)

    fun displayName(uri: Uri): String = runCatching {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/') ?: "file"

    /** Human-friendly folder name for a tree, e.g. "Documents/Transfer Pak". */
    fun treeLabel(tree: Uri): String = runCatching {
        DocumentsContract.getTreeDocumentId(tree).substringAfter(':').ifEmpty { "Internal storage" }
    }.getOrDefault(tree.toString())

    /** Every file under [tree], up to [maxDepth] folders deep. */
    fun listFiles(tree: Uri, maxDepth: Int = 4): List<DocFile> {
        val out = mutableListOf<DocFile>()
        val rootId = runCatching { DocumentsContract.getTreeDocumentId(tree) }.getOrNull() ?: return out
        fun walk(parentId: String, depth: Int) {
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId)
            val cols = arrayOf(
                Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE,
                Document.COLUMN_LAST_MODIFIED, Document.COLUMN_SIZE,
            )
            val folders = mutableListOf<String>()
            runCatching {
                resolver.query(children, cols, null, null, null)?.use { c ->
                    while (c.moveToNext()) {
                        val id = c.getString(0) ?: continue
                        val name = c.getString(1) ?: continue
                        if (c.getString(2) == Document.MIME_TYPE_DIR) {
                            if (!name.startsWith(".")) folders += id
                        } else {
                            out += DocFile(
                                uri = DocumentsContract.buildDocumentUriUsingTree(tree, id),
                                name = name,
                                parentId = parentId,
                                lastModified = if (c.isNull(3)) 0L else c.getLong(3),
                                size = if (c.isNull(4)) 0L else c.getLong(4),
                            )
                        }
                    }
                }
            }
            if (depth < maxDepth) folders.forEach { walk(it, depth + 1) }
        }
        walk(rootId, 0)
        return out
    }

    /** Finds [name] directly inside [tree], creating an empty file if it isn't there. */
    fun findOrCreate(tree: Uri, name: String): Uri? = runCatching {
        val rootId = DocumentsContract.getTreeDocumentId(tree)
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, rootId)
        resolver.query(children, arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { c ->
            while (c.moveToNext()) {
                if (c.getString(1) == name) return@runCatching DocumentsContract.buildDocumentUriUsingTree(tree, c.getString(0))
            }
        }
        val parent = DocumentsContract.buildDocumentUriUsingTree(tree, rootId)
        // A generic binary type so providers don't append an extension of their own.
        DocumentsContract.createDocument(resolver, parent, "application/octet-stream", name)
    }.getOrNull()
}

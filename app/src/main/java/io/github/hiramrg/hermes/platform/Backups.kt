package io.github.hiramrg.hermes.platform

import android.content.Context
import io.github.hiramrg.hermes.core.Gen1Save
import io.github.hiramrg.hermes.core.SyncPlan
import java.io.File

/** A copy of a Game Boy save kept inside Hermes. */
data class Backup(val file: File, val time: Long, val reason: String) {
    val trainer: String? get() = runCatching { Gen1Save.parse(file.readBytes())?.name }.getOrNull()
}

/**
 * Keeps copies of saves before Hermes touches them, in app storage (survives the user deleting
 * the Transfer Pak folder). The newest [KEEP] per cartridge are kept.
 */
class Backups(context: Context) {
    private val root = File(context.filesDir, "backups")

    companion object {
        const val KEEP = 15
        const val BEFORE_STADIUM = "Before playing Stadium"
        const val BEFORE_COPY_BACK = "Before copying Stadium's changes"
        const val STADIUM_VERSION = "Stadium's version (not kept)"
        const val BEFORE_RESTORE = "Before restoring a backup"
    }

    private fun dir(cartKey: String) = File(root, SyncPlan.hash(cartKey.toByteArray()).take(16))

    fun save(cartKey: String, bytes: ByteArray, reason: String) {
        val d = dir(cartKey).apply { mkdirs() }
        // Don't stack identical copies back to back.
        val newest = list(cartKey).firstOrNull()
        if (newest != null && newest.file.readBytes().contentEquals(bytes)) return
        val safeReason = reason.replace(Regex("[^A-Za-z0-9 ',()]"), "")
        File(d, "${System.currentTimeMillis()}_$safeReason.sav").writeBytes(bytes)
        list(cartKey).drop(KEEP).forEach { it.file.delete() }
    }

    fun list(cartKey: String): List<Backup> =
        (dir(cartKey).listFiles() ?: emptyArray()).mapNotNull { f ->
            val time = f.name.substringBefore('_').toLongOrNull() ?: return@mapNotNull null
            Backup(f, time, f.nameWithoutExtension.substringAfter('_'))
        }.sortedByDescending { it.time }

    fun totalSize(): Long = root.walkTopDown().filter { it.isFile }.sumOf { it.length() }
}

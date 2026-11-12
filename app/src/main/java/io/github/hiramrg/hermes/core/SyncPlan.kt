package io.github.hiramrg.hermes.core

import java.security.MessageDigest

/** What to do with the save in the Transfer Pak slot after Stadium has used it. */
enum class SyncAction {
    /** Stadium didn't change the save. Nothing to copy. */
    NOTHING_NEW,
    /** Stadium changed the save and the original is as we left it: copy the slot back. */
    COPY_TO_ORIGINAL,
    /** Both changed (e.g. the Game Boy game was played elsewhere meanwhile): ask which to keep. */
    CONFLICT,
    /** The slot save is damaged or missing: never copy it over the original. */
    SLOT_INVALID,
}

object SyncPlan {
    fun hash(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    /**
     * @param slotHashAtInsert hash of what we copied into the slot.
     * @param slot the slot save now (null if it couldn't be read).
     * @param originalHashAtInsert hash of the original save when we copied it.
     * @param originalHashNow hash of the original save now (null if it's gone).
     */
    /**
     * @param requireValid false when the user chose "Use it anyway" for a save Hermes couldn't
     *   verify; then only a short or blank slot counts as invalid.
     */
    fun decide(
        slotHashAtInsert: String,
        slot: ByteArray?,
        originalHashAtInsert: String,
        originalHashNow: String?,
        requireValid: Boolean = true,
    ): SyncAction {
        if (slot == null) return SyncAction.SLOT_INVALID
        if (hash(slot) == slotHashAtInsert) return SyncAction.NOTHING_NEW
        val ok = if (requireValid) Gen1Save.isValid(slot) else slot.size >= Gen1Save.SIZE && !Gen1Save.isBlank(slot)
        if (!ok) return SyncAction.SLOT_INVALID
        return if (originalHashNow == null || originalHashNow == originalHashAtInsert) SyncAction.COPY_TO_ORIGINAL
        else SyncAction.CONFLICT
    }
}

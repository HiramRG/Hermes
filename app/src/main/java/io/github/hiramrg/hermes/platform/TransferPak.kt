package io.github.hiramrg.hermes.platform

import android.net.Uri
import io.github.hiramrg.hermes.core.Gen1Save
import io.github.hiramrg.hermes.core.SyncAction
import io.github.hiramrg.hermes.core.SyncPlan

/**
 * The virtual Transfer Pak: two fixed files that M64Plus FZ is pointed at once
 * (Pokémon Stadium's settings → "Player 1 Gameboy ROM" and "Player 1 Gameboy RAM").
 *
 * M64Plus FZ copies both files in when the game starts and writes the RAM file back when the
 * game is exited (CoreService → CoreInterface.writeGbRamData). So "inserting a cartridge" means
 * copying a ROM and save into the slot, and afterwards copying the slot's save back.
 */
class TransferPak(private val storage: Storage, private val prefs: Prefs, private val backups: Backups) {

    companion object {
        const val SLOT_ROM = "hermes-cartridge.gb"
        const val SLOT_SAVE = "hermes-cartridge.sav"
    }

    sealed class InsertResult {
        data object Ok : InsertResult()
        data class Failed(val message: String) : InsertResult()
    }

    /** Result of looking at the slot after Stadium has (maybe) used it. */
    data class Check(
        val action: SyncAction,
        /** M64Plus FZ wrote the slot save since we inserted, i.e. Stadium was exited properly. */
        val handedBack: Boolean,
    )

    fun slotFiles(): Pair<Uri, Uri>? {
        val tree = prefs.slotTree ?: return null
        val rom = storage.findOrCreate(tree, SLOT_ROM) ?: return null
        val save = storage.findOrCreate(tree, SLOT_SAVE) ?: return null
        return rom to save
    }

    fun insert(cart: Cartridge): InsertResult {
        val saveUri = cart.saveUri ?: return InsertResult.Failed("No save file was found for ${cart.title}.")
        val (slotRom, slotSave) = slotFiles() ?: return InsertResult.Failed("The Transfer Pak folder can't be opened. Choose it again in Settings.")
        val rom = (if (cart.zipped) storage.readZipEntry(cart.romUri, Library.ROM_EXTENSIONS) else storage.readBytes(cart.romUri))
            ?: return InsertResult.Failed("Couldn't read ${cart.romName}.")
        val save = storage.readBytes(saveUri) ?: return InsertResult.Failed("Couldn't read ${cart.saveName}.")
        // Stadium expects exactly 32 KB of cartridge RAM; emulators sometimes add a header or footer.
        val ram = Gen1Save.extract(save)
            ?: return InsertResult.Failed("Hermes can't use this save. ${Gen1Save.diagnose(save)}")
        backups.save(cart.key, save, Backups.BEFORE_STADIUM)
        if (!storage.writeBytes(slotRom, rom) || !storage.writeBytes(slotSave, ram)) {
            return InsertResult.Failed("Couldn't write to the Transfer Pak folder.")
        }
        prefs.insertion = Insertion(
            romUri = cart.romUri.toString(),
            saveUri = saveUri.toString(),
            gameLabel = cart.title,
            trainer = cart.card?.name.orEmpty(),
            slotHash = SyncPlan.hash(ram),
            originalHash = SyncPlan.hash(save),
            slotModifiedAtInsert = storage.lastModified(slotSave),
            insertedAt = System.currentTimeMillis(),
        )
        return InsertResult.Ok
    }

    fun markLaunched() {
        prefs.insertion?.let { prefs.insertion = it.copy(launched = true) }
    }

    fun slotModified(): Long = slotFiles()?.let { storage.lastModified(it.second) } ?: 0L

    fun check(): Check? {
        val ins = prefs.insertion ?: return null
        val (_, slotSave) = slotFiles() ?: return Check(SyncAction.SLOT_INVALID, false)
        val handedBack = storage.lastModified(slotSave) > ins.slotModifiedAtInsert
        if (handedBack) prefs.linkVerified = true
        val slot = storage.readBytes(slotSave)
        val original = storage.readBytes(Uri.parse(ins.saveUri))
        val action = SyncPlan.decide(ins.slotHash, slot, ins.originalHash, original?.let { SyncPlan.hash(it) })
        return Check(action, handedBack)
    }

    /**
     * Copies the slot save over the original (after backing the original up).
     * Keeps any emulator footer the original had, so its own emulator still likes the file.
     */
    fun copyBack(): Boolean {
        val ins = prefs.insertion ?: return false
        val (_, slotSave) = slotFiles() ?: return false
        val slot = storage.readBytes(slotSave)?.let { Gen1Save.extract(it) } ?: return false
        val originalUri = Uri.parse(ins.saveUri)
        val original = storage.readBytes(originalUri)
        if (original != null) backups.save(ins.romUri, original, Backups.BEFORE_COPY_BACK)
        // Put the new 32 KB back where it was in the file, keeping any header/footer the emulator uses.
        val offset = original?.let { Gen1Save.locate(it) } ?: 0
        val merged = if (original != null && original.size >= offset + Gen1Save.SIZE) original.copyOf().also { slot.copyInto(it, offset) } else slot
        if (!storage.writeBytes(originalUri, merged)) return false
        prefs.insertion = ins.copy(slotHash = SyncPlan.hash(slot), originalHash = SyncPlan.hash(merged))
        return true
    }

    /** Conflict resolved in favour of the Game Boy save: keep Stadium's copy as a backup, refresh the slot. */
    fun keepOriginal(): Boolean {
        val ins = prefs.insertion ?: return false
        val (_, slotSave) = slotFiles() ?: return false
        storage.readBytes(slotSave)?.let { backups.save(ins.romUri, it, Backups.STADIUM_VERSION) }
        val original = storage.readBytes(Uri.parse(ins.saveUri)) ?: return false
        val ram = Gen1Save.extract(original) ?: original.copyOf(Gen1Save.SIZE)
        if (!storage.writeBytes(slotSave, ram)) return false
        prefs.insertion = ins.copy(
            slotHash = SyncPlan.hash(ram), originalHash = SyncPlan.hash(original),
            slotModifiedAtInsert = storage.lastModified(slotSave),
        )
        return true
    }

    /** Puts a backup back as the cartridge's save (backing up the current one first). */
    fun restore(cart: Cartridge, backup: Backup): Boolean {
        val saveUri = cart.saveUri ?: return false
        val bytes = runCatching { backup.file.readBytes() }.getOrNull() ?: return false
        storage.readBytes(saveUri)?.let { backups.save(cart.key, it, Backups.BEFORE_RESTORE) }
        val ok = storage.writeBytes(saveUri, bytes)
        // If that cartridge is in the slot, forget the old insertion so it isn't "synced" over the restore.
        if (ok && prefs.insertion?.romUri == cart.key) prefs.insertion = null
        return ok
    }
}

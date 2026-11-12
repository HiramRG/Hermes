package io.github.hiramrg.hermes.platform

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.hiramrg.hermes.core.N64Header
import io.github.hiramrg.hermes.core.N64Rom
import io.github.hiramrg.hermes.core.SyncAction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.zip.ZipInputStream

/** What the bottom screen is showing. */
enum class BottomMode { HOME, SETUP_GUIDE, PLAYING }

/** Where the in-app update is. */
sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class Available(val release: Updater.Release) : UpdateState
    data class Downloading(val progress: Float) : UpdateState
    data object Installing : UpdateState
    data object UpToDate : UpdateState
    data class Failed(val reason: String) : UpdateState
}

/** Something that needs the user's attention after a Stadium session. */
sealed class Pending {
    /** Stadium hasn't handed the cartridge back yet (still open, or the link isn't set up). */
    data object NotHandedBack : Pending()
    /** Both Stadium and the Game Boy game changed the save. */
    data object Conflict : Pending()
}

/**
 * App-wide state and actions, shared by the top-screen and bottom-screen activities
 * (both run in the same process). Compose reads the `by mutableStateOf` fields directly.
 */
@SuppressLint("StaticFieldLeak") // holds the application context only
object Hermes {
    private lateinit var app: Context
    lateinit var prefs: Prefs; private set
    lateinit var storage: Storage; private set
    lateinit var emulator: Emulator; private set
    lateinit var backups: Backups; private set
    lateinit var pak: TransferPak; private set
    lateinit var updater: Updater; private set
    private lateinit var library: Library

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    var cartridges by mutableStateOf<List<Cartridge>>(emptyList()); private set
    var scanning by mutableStateOf(false); private set
    var selectedKey by mutableStateOf<String?>(null)
    var stadium by mutableStateOf<N64Header?>(null); private set
    var stadiumName by mutableStateOf<String?>(null); private set
    var message by mutableStateOf<String?>(null)
    var pending by mutableStateOf<Pending?>(null); private set
    var busy by mutableStateOf<String?>(null); private set
    var bottomMode by mutableStateOf(BottomMode.HOME)
    var update by mutableStateOf<UpdateState>(UpdateState.Idle); private set
    /** Hermes' main screen is on screen (between onStart and onStop). */
    var mainVisible by mutableStateOf(false)
    /** The cartridge the user last asked to play (for "Start anyway"). */
    var lastRequested by mutableStateOf<Cartridge?>(null); private set
    /** Bumped whenever prefs change, so screens that read prefs recompose. */
    var prefsVersion by mutableStateOf(0); private set

    val selected: Cartridge? get() = cartridges.firstOrNull { it.key == selectedKey } ?: cartridges.firstOrNull()
    val insertedCartridge: Cartridge? get() = prefs.insertion?.let { ins -> cartridges.firstOrNull { it.key == ins.romUri } }

    fun init(context: Context) {
        if (::app.isInitialized) return
        app = context.applicationContext
        prefs = Prefs(app)
        storage = Storage(app)
        emulator = Emulator(app)
        backups = Backups(app)
        pak = TransferPak(storage, prefs, backups)
        updater = Updater(app)
        library = Library(storage, prefs)
        selectedKey = prefs.lastSelected
    }

    fun select(cart: Cartridge) {
        selectedKey = cart.key
        prefs.lastSelected = cart.key
    }

    private fun touchedPrefs() { prefsVersion++ }

    // ------------------------------------------------------------------ setup

    val setupComplete: Boolean get() = emulator.isInstalled && prefs.stadiumUri != null &&
        prefs.gameFolders.isNotEmpty() && prefs.slotTree != null

    fun refresh() {
        scope.launch {
            scanning = true
            val (carts, header) = withContext(Dispatchers.IO) {
                library.scan() to prefs.stadiumUri?.let { readStadiumHeader(it) }
            }
            cartridges = carts
            stadium = header
            stadiumName = prefs.stadiumUri?.let { storage.displayName(it) }
            scanning = false
            touchedPrefs() // e.g. the emulator was installed meanwhile
            if (selectedKey == null || carts.none { it.key == selectedKey }) selectedKey = carts.firstOrNull()?.key
            // Catches Stadium sessions started from M64Plus FZ itself, too.
            if (prefs.insertion != null && prefs.insertion?.launched != true) sync(waitForStadium = false)
        }
    }

    private fun readStadiumHeader(uri: Uri): N64Header? {
        storage.readBytes(uri, N64Rom.HEADER_SIZE)?.let { N64Rom.readHeader(it) }?.let { return it }
        // Zipped ROMs: look at the first N64 file inside.
        return runCatching {
            app.contentResolver.openInputStream(uri)?.use { raw ->
                ZipInputStream(raw).use { zip ->
                    while (true) {
                        val e = zip.nextEntry ?: break
                        val ext = e.name.substringAfterLast('.').lowercase()
                        if (ext in setOf("z64", "n64", "v64")) {
                            val buf = ByteArray(N64Rom.HEADER_SIZE)
                            var read = 0
                            while (read < buf.size) {
                                val n = zip.read(buf, read, buf.size - read)
                                if (n < 0) break
                                read += n
                            }
                            return@runCatching N64Rom.readHeader(buf)
                        }
                    }
                    null
                }
            }
        }.getOrNull()
    }

    fun setStadium(uri: Uri) {
        storage.takePermission(uri, write = false)
        prefs.stadiumUri = uri
        touchedPrefs()
        refresh()
    }

    fun addGameFolder(uri: Uri) {
        storage.takePermission(uri, write = false)
        prefs.gameFolders = (prefs.gameFolders + uri).distinct()
        touchedPrefs()
        refresh()
    }

    fun removeGameFolder(uri: Uri) {
        prefs.gameFolders = prefs.gameFolders - uri
        touchedPrefs()
        refresh()
    }

    /** Remembers the Transfer Pak folder and fills the slot with a cartridge so there's something to pick. */
    fun setSlotFolder(uri: Uri) {
        storage.takePermission(uri, write = true)
        prefs.slotTree = uri
        prefs.linkVerified = false
        prefs.insertion = null
        touchedPrefs()
        scope.launch {
            busy = "Preparing the Transfer Pak…"
            val msg = withContext(Dispatchers.IO) {
                if (pak.slotFiles() == null) return@withContext "Couldn't create files in that folder. Try a different one."
                val first = cartridges.firstOrNull { it.hasUsableSave }
                if (first != null) (pak.insert(first) as? TransferPak.InsertResult.Failed)?.message else null
            }
            busy = null
            msg?.let { message = it }
            touchedPrefs()
        }
    }

    fun pairSave(cart: Cartridge, save: Uri) {
        storage.takePermission(save, write = true)
        prefs.manualSaves = prefs.manualSaves + (cart.key to save.toString())
        refresh()
    }

    fun finishSetup() {
        prefs.setupDone = true
        bottomMode = BottomMode.HOME
        touchedPrefs()
    }

    // ------------------------------------------------------------------ playing

    /** Starts Stadium with [cart] in the Transfer Pak. */
    fun play(cart: Cartridge, force: Boolean = false) {
        lastRequested = cart
        val stadiumUri = prefs.stadiumUri ?: run { message = "Choose your Pokémon Stadium game in Settings first."; return }
        if (!emulator.isInstalled) { message = "M64Plus FZ isn't installed."; return }
        scope.launch {
            busy = "Inserting ${cart.title}…"
            // First bring anything Stadium changed last time home, so it's never overwritten.
            val previous = prefs.insertion
            if (previous != null) {
                val check = withContext(Dispatchers.IO) { pak.check() }
                if (check != null) {
                    if (check.action == SyncAction.CONFLICT) { busy = null; pending = Pending.Conflict; return@launch }
                    if (check.action == SyncAction.COPY_TO_ORIGINAL) withContext(Dispatchers.IO) { pak.copyBack() }
                    if (!force && previous.launched && !check.handedBack && prefs.linkVerified) {
                        // Stadium is probably still open with the old cartridge; restarting it would lose its changes.
                        busy = null; pending = Pending.NotHandedBack; return@launch
                    }
                }
            }
            val result = withContext(Dispatchers.IO) { pak.insert(cart) }
            if (result is TransferPak.InsertResult.Failed) { busy = null; message = result.message; return@launch }
            val roles = Screens.roles(app, prefs.swapScreens)
            val ok = emulator.launch(stadiumUri, roles.top)
            busy = null
            pending = null
            if (ok) {
                pak.markLaunched()
                bottomMode = BottomMode.PLAYING
            } else {
                message = "Couldn't start M64Plus FZ."
            }
            touchedPrefs()
        }
    }

    /**
     * Looks at the slot after a session. With [waitForStadium], gives M64Plus FZ a few seconds to
     * finish writing the save after the game closes.
     */
    fun sync(waitForStadium: Boolean) {
        val ins = prefs.insertion ?: return
        if (syncing) return
        syncing = true
        val wasLinked = prefs.linkVerified
        scope.launch {
          try {
            var check = withContext(Dispatchers.IO) { pak.check() } ?: return@launch
            if (waitForStadium && ins.launched) {
                var waited = 0
                while (!check.handedBack && waited < 12) {
                    delay(1000); waited++
                    check = withContext(Dispatchers.IO) { pak.check() } ?: return@launch
                }
                if (check.handedBack) {
                    // Wait for the write to settle.
                    var last = withContext(Dispatchers.IO) { pak.slotModified() }
                    for (attempt in 0 until 5) {
                        delay(700)
                        val now = withContext(Dispatchers.IO) { pak.slotModified() }
                        if (now == last) break
                        last = now
                    }
                    check = withContext(Dispatchers.IO) { pak.check() } ?: return@launch
                }
            }
            val label = ins.gameLabel
            when (check.action) {
                SyncAction.NOTHING_NEW -> {
                    if (check.handedBack) {
                        endSession()
                        if (ins.launched) message = "Welcome back! Nothing changed in your $label save."
                    } else if (ins.launched && waitForStadium) {
                        pending = Pending.NotHandedBack
                    }
                }
                SyncAction.COPY_TO_ORIGINAL -> {
                    val ok = withContext(Dispatchers.IO) { pak.copyBack() }
                    if (ok) {
                        endSession()
                        message = "Saved! Stadium's changes are now in your $label save. A backup of the old one was kept."
                        refresh()
                    } else {
                        message = "Couldn't write to your $label save. Nothing was lost: the Transfer Pak still has Stadium's version."
                    }
                }
                SyncAction.CONFLICT -> pending = Pending.Conflict
                SyncAction.SLOT_INVALID -> {
                    endSession()
                    message = "The Transfer Pak's save looked damaged or missing, so your $label save was left as it was."
                    prefs.insertion = null
                }
            }
            if (!wasLinked && prefs.linkVerified) {
                message = "It works! Stadium read your $label cartridge through Hermes. You're all set."
                if (bottomMode == BottomMode.SETUP_GUIDE) bottomMode = BottomMode.HOME
            }
            touchedPrefs()
          } finally {
            syncing = false
          }
        }
    }

    private var syncing = false

    private fun endSession() {
        prefs.insertion?.let { prefs.insertion = it.copy(launched = false) }
        pending = null
        if (bottomMode == BottomMode.PLAYING) bottomMode = BottomMode.HOME
    }

    fun resolveConflict(keepStadium: Boolean) {
        scope.launch {
            val ok = withContext(Dispatchers.IO) { if (keepStadium) pak.copyBack() else pak.keepOriginal() }
            if (ok) {
                endSession()
                message = if (keepStadium) "Kept Stadium's version. The Game Boy one is in Backups."
                else "Kept the Game Boy version. Stadium's one is in Backups."
                refresh()
            } else {
                message = "Couldn't write the save. Nothing was changed."
            }
        }
    }

    /**
     * Closes the dialog. Once the link is proven, the session stays open so Stadium's changes are
     * still picked up later; before that, "not handed back" usually means "not linked", so give up.
     */
    fun dismissPending() {
        pending = null
        if (!prefs.linkVerified) endSession()
    }

    fun restore(cart: Cartridge, backup: Backup) {
        scope.launch {
            val ok = withContext(Dispatchers.IO) { pak.restore(cart, backup) }
            message = if (ok) "Restored. Your current save was backed up first." else "Couldn't restore that backup."
            refresh()
        }
    }

    /** Opens M64Plus FZ on the top screen with the setup steps on the bottom one. */
    fun openEmulatorForSetup() {
        bottomMode = BottomMode.SETUP_GUIDE
        if (!emulator.open(Screens.roles(app, prefs.swapScreens).top)) message = "Couldn't open M64Plus FZ."
    }

    /** Starts Stadium once so M64Plus FZ adds it to its game list. */
    fun addStadiumToEmulator() {
        val uri = prefs.stadiumUri ?: run { message = "Choose your Pokémon Stadium game first."; return }
        bottomMode = BottomMode.SETUP_GUIDE
        if (!emulator.launch(uri, Screens.roles(app, prefs.swapScreens).top)) message = "Couldn't start M64Plus FZ."
    }

    // ------------------------------------------------------------------ updates

    /** Looks for a newer build on GitHub. [silent]: only speak up if there is one. */
    fun checkForUpdate(silent: Boolean) {
        if (update is UpdateState.Checking || update is UpdateState.Downloading || update is UpdateState.Installing) return
        scope.launch {
            if (!silent) update = UpdateState.Checking
            update = when (val r = updater.check()) {
                is Updater.Check.UpdateAvailable -> UpdateState.Available(r.release)
                is Updater.Check.UpToDate -> if (silent) UpdateState.Idle else UpdateState.UpToDate
                is Updater.Check.Failed -> if (silent) UpdateState.Idle else UpdateState.Failed(r.reason)
            }
        }
    }

    /** Downloads the new APK and hands it to Android, which asks "Update this app?". */
    fun installUpdate(release: Updater.Release) {
        if (!updater.canInstall()) {
            message = "Allow Hermes to install updates (the switch on the next screen), then come back and tap Install again."
            runCatching { updater.openInstallPermissionSettings() }
            return
        }
        scope.launch {
            update = UpdateState.Downloading(0f)
            try {
                val apk = updater.download(release) { p -> scope.launch { update = UpdateState.Downloading(p) } }
                update = UpdateState.Installing
                withContext(Dispatchers.IO) { updater.install(apk) }
            } catch (e: Exception) {
                update = UpdateState.Failed(e.message ?: "Download failed")
            }
        }
    }

    /** Goes back to the running Stadium (M64Plus FZ) on the top screen. */
    fun returnToStadium() {
        emulator.open(Screens.roles(app, prefs.swapScreens).top)
    }

    /** Brings Hermes' main screen to the top display (from the bottom screen's "I'm done"). */
    fun bringHermesToFront(activityClass: Class<*>) {
        val roles = Screens.roles(app, prefs.swapScreens)
        val intent = Intent(app, activityClass).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        runCatching { app.startActivity(intent, Screens.options(roles.top)) }
            .onFailure { runCatching { app.startActivity(intent) } }
    }
}

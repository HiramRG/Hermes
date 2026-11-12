package io.github.hiramrg.hermes.platform

import android.content.Context
import android.net.Uri
import org.json.JSONObject

/** What's in the Transfer Pak slot right now, and what it looked like when we put it there. */
data class Insertion(
    val romUri: String,
    val saveUri: String,
    val gameLabel: String,
    val trainer: String,
    /** Hash of the save we copied into the slot. */
    val slotHash: String,
    /** Hash of the original save at that moment. */
    val originalHash: String,
    /** The slot save's modification time right after we wrote it; M64Plus FZ writing it back moves this on. */
    val slotModifiedAtInsert: Long,
    val insertedAt: Long,
    /** True once Stadium was started with this cartridge. */
    val launched: Boolean = false,
    /** The user chose "Use it anyway" for a save Hermes couldn't verify. */
    val trusted: Boolean = false,
) {
    fun toJson(): String = JSONObject()
        .put("romUri", romUri).put("saveUri", saveUri).put("gameLabel", gameLabel).put("trainer", trainer)
        .put("slotHash", slotHash).put("originalHash", originalHash)
        .put("slotModifiedAtInsert", slotModifiedAtInsert).put("insertedAt", insertedAt).put("launched", launched).put("trusted", trusted)
        .toString()

    companion object {
        fun fromJson(s: String?): Insertion? = runCatching {
            val o = JSONObject(s ?: return null)
            Insertion(
                o.getString("romUri"), o.getString("saveUri"), o.optString("gameLabel"), o.optString("trainer"),
                o.getString("slotHash"), o.getString("originalHash"),
                o.optLong("slotModifiedAtInsert"), o.optLong("insertedAt"), o.optBoolean("launched"), o.optBoolean("trusted"),
            )
        }.getOrNull()
    }
}

class Prefs(context: Context) {
    private val p = context.getSharedPreferences("hermes", Context.MODE_PRIVATE)

    var stadiumUri: Uri?
        get() = p.getString("stadium", null)?.let(Uri::parse)
        set(v) = p.edit().putString("stadium", v?.toString()).apply()

    var slotTree: Uri?
        get() = p.getString("slotTree", null)?.let(Uri::parse)
        set(v) = p.edit().putString("slotTree", v?.toString()).apply()

    var gameFolders: List<Uri>
        get() = p.getStringSet("gameFolders", emptySet())!!.sorted().map(Uri::parse)
        set(v) = p.edit().putStringSet("gameFolders", v.map { it.toString() }.toSet()).apply()

    /** Set once M64Plus FZ has handed a save back through the slot, which proves the link works. */
    var linkVerified: Boolean
        get() = p.getBoolean("linkVerified", false)
        set(v) = p.edit().putBoolean("linkVerified", v).apply()

    /** The user finished or skipped the walkthrough. */
    var setupDone: Boolean
        get() = p.getBoolean("setupDone", false)
        set(v) = p.edit().putBoolean("setupDone", v).apply()

    var insertion: Insertion?
        get() = Insertion.fromJson(p.getString("insertion", null))
        set(v) = p.edit().putString("insertion", v?.toJson()).apply()

    /** Saves the user paired by hand: ROM uri → save uri. */
    var manualSaves: Map<String, String>
        get() = runCatching {
            val o = JSONObject(p.getString("manualSaves", "{}")!!)
            o.keys().asSequence().associateWith { o.getString(it) }
        }.getOrDefault(emptyMap())
        set(v) = p.edit().putString("manualSaves", JSONObject(v as Map<*, *>).toString()).apply()

    var lastSelected: String?
        get() = p.getString("lastSelected", null)
        set(v) = p.edit().putString("lastSelected", v).apply()

    var swapScreens: Boolean
        get() = p.getBoolean("swapScreens", false)
        set(v) = p.edit().putBoolean("swapScreens", v).apply()
}

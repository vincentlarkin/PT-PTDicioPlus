package com.euptdicio.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/** A saved word. Keyed by (word, pos) so it survives dictionary rebuilds that renumber entries. */
data class SavedWord(val word: String, val pos: String, val gloss: String, val at: Long)

/** Favourites and lookup history in a small private database. */
class UserStore(context: Context) : SQLiteOpenHelper(context, "user.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE favorite(word TEXT NOT NULL, pos TEXT NOT NULL, gloss TEXT NOT NULL, at INTEGER NOT NULL, PRIMARY KEY(word, pos))")
        db.execSQL("CREATE TABLE history(word TEXT NOT NULL, pos TEXT NOT NULL, gloss TEXT NOT NULL, at INTEGER NOT NULL, PRIMARY KEY(word, pos))")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun favorites(): List<SavedWord> = list("favorite", 500)
    fun history(limit: Int = 100): List<SavedWord> = list("history", limit)

    fun isFavorite(word: String, pos: String): Boolean =
        readableDatabase.rawQuery("SELECT 1 FROM favorite WHERE word = ? AND pos = ?", arrayOf(word, pos)).use { it.moveToFirst() }

    fun setFavorite(w: SavedWord, on: Boolean) {
        if (on) put("favorite", w) else writableDatabase.delete("favorite", "word = ? AND pos = ?", arrayOf(w.word, w.pos))
    }

    fun addHistory(w: SavedWord) {
        put("history", w)
        writableDatabase.execSQL("DELETE FROM history WHERE rowid NOT IN (SELECT rowid FROM history ORDER BY at DESC LIMIT 300)")
    }

    fun clearHistory() = writableDatabase.delete("history", null, null).let { }

    private fun put(table: String, w: SavedWord) {
        writableDatabase.insertWithOnConflict(table, null, ContentValues().apply {
            put("word", w.word); put("pos", w.pos); put("gloss", w.gloss); put("at", w.at)
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    private fun list(table: String, limit: Int): List<SavedWord> =
        readableDatabase.rawQuery("SELECT word, pos, gloss, at FROM $table ORDER BY at DESC LIMIT $limit", null).use { c ->
            buildList { while (c.moveToNext()) add(SavedWord(c.getString(0), c.getString(1), c.getString(2), c.getLong(3))) }
        }
}

enum class ThemeMode { System, Light, Dark }

/** Small preferences wrapper. */
class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var theme: ThemeMode
        get() = runCatching { ThemeMode.valueOf(prefs.getString("theme", null) ?: "System") }.getOrDefault(ThemeMode.System)
        set(v) = prefs.edit().putString("theme", v.name).apply()

    /** Show Brazilian-only conjugation variants (falamos as preterite) next to European ones. */
    var showBrazilianForms: Boolean
        get() = prefs.getBoolean("showBrazilianForms", true)
        set(v) = prefs.edit().putBoolean("showBrazilianForms", v).apply()

    var showBrazilianIpa: Boolean
        get() = prefs.getBoolean("showBrazilianIpa", false)
        set(v) = prefs.edit().putBoolean("showBrazilianIpa", v).apply()
}

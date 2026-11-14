package com.euptdicio.data

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.euptdicio.core.Dictionary
import com.euptdicio.core.Row
import com.euptdicio.core.SqlDb
import java.io.File

/**
 * Installs the bundled dictionary (assets/dictionary/dicio.sqlite) into app storage on first run or
 * after an app update ships a newer build, then opens it read-only.
 */
object DictionaryDatabase {
    private const val ASSET = "dictionary/dicio.sqlite"
    private const val STAMP_ASSET = "dictionary/dicio.version"

    @Volatile private var instance: Dictionary? = null

    fun isInstalled(context: Context): Boolean {
        val target = file(context)
        return target.exists() && installedStamp(context) == assetStamp(context)
    }

    /** Blocking; call off the main thread. [progress] receives 0..1. */
    fun open(context: Context, progress: (Float) -> Unit = {}): Dictionary {
        instance?.let { return it }
        synchronized(this) {
            instance?.let { return it }
            val app = context.applicationContext
            if (!isInstalled(app)) install(app, progress)
            val db = SQLiteDatabase.openDatabase(
                file(app).path, null, SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS,
            )
            return Dictionary(AndroidSqlDb(db)).also { instance = it }
        }
    }

    private fun file(context: Context) = File(context.noBackupFilesDir, "dicio.sqlite")

    private fun assetStamp(context: Context): String =
        runCatching { context.assets.open(STAMP_ASSET).bufferedReader().use { it.readText().trim() } }.getOrDefault("unknown")

    private fun installedStamp(context: Context): String? =
        context.getSharedPreferences("dictionary", Context.MODE_PRIVATE).getString("stamp", null)

    private fun install(context: Context, progress: (Float) -> Unit) {
        val target = file(context)
        val tmp = File(target.path + ".tmp")
        val total = runCatching { context.assets.open(STAMP_ASSET).bufferedReader().use { it.readLines().getOrNull(1)?.toLong() } }
            .getOrNull() ?: 75_000_000L
        context.assets.open(ASSET).use { input ->
            tmp.outputStream().use { out ->
                val buf = ByteArray(1 shl 16)
                var copied = 0L
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    copied += n
                    progress((copied.toFloat() / total).coerceAtMost(1f))
                }
            }
        }
        target.delete()
        File(target.path + "-journal").delete()
        check(tmp.renameTo(target)) { "could not install dictionary" }
        context.getSharedPreferences("dictionary", Context.MODE_PRIVATE).edit()
            .putString("stamp", assetStamp(context)).apply()
    }
}

class AndroidSqlDb(private val db: SQLiteDatabase) : SqlDb {
    override fun <T> query(sql: String, args: List<Any?>, map: (Row) -> T): List<T> {
        // Android binds everything as strings; integer comparisons need real integers, so inline numbers.
        val stringArgs = ArrayList<String>()
        var i = 0
        val inlined = buildString {
            for (ch in sql) {
                if (ch == '?') {
                    when (val a = args[i++]) {
                        null -> append("NULL")
                        is Int, is Long -> append(a.toString())
                        else -> { append('?'); stringArgs += a.toString() }
                    }
                } else append(ch)
            }
        }
        return db.rawQuery(inlined, stringArgs.toTypedArray()).use { c ->
            val row = CursorRow(c)
            buildList(c.count) { while (c.moveToNext()) add(map(row)) }
        }
    }

    private class CursorRow(val c: Cursor) : Row {
        override fun long(i: Int) = c.getLong(i)
        override fun int(i: Int) = c.getInt(i)
        override fun intOrNull(i: Int) = if (c.isNull(i)) null else c.getInt(i)
        override fun longOrNull(i: Int) = if (c.isNull(i)) null else c.getLong(i)
        override fun double(i: Int) = c.getDouble(i)
        override fun string(i: Int): String = c.getString(i) ?: ""
        override fun stringOrNull(i: Int): String? = if (c.isNull(i)) null else c.getString(i)
    }
}

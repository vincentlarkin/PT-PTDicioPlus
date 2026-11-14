package com.euptdicio.core

/** Minimal read-only SQL access so the same search code runs on Android SQLite and JDBC (tests, tools). */
interface SqlDb {
    fun <T> query(sql: String, args: List<Any?>, map: (Row) -> T): List<T>
}

interface Row {
    fun long(i: Int): Long
    fun int(i: Int): Int
    fun intOrNull(i: Int): Int?
    fun longOrNull(i: Int): Long?
    fun double(i: Int): Double
    fun string(i: Int): String
    fun stringOrNull(i: Int): String?
}

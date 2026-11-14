package com.euptdicio.core

import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet

class JdbcDb(path: String) : SqlDb, AutoCloseable {
    private val conn: Connection = DriverManager.getConnection("jdbc:sqlite:file:$path?mode=ro")

    override fun <T> query(sql: String, args: List<Any?>, map: (Row) -> T): List<T> =
        conn.prepareStatement(sql).use { st ->
            args.forEachIndexed { i, a -> st.setObject(i + 1, a) }
            st.executeQuery().use { rs ->
                val row = JdbcRow(rs)
                buildList { while (rs.next()) add(map(row)) }
            }
        }

    override fun close() = conn.close()

    private class JdbcRow(val rs: ResultSet) : Row {
        override fun long(i: Int) = rs.getLong(i + 1)
        override fun int(i: Int) = rs.getInt(i + 1)
        override fun intOrNull(i: Int) = rs.getInt(i + 1).takeUnless { rs.wasNull() }
        override fun longOrNull(i: Int) = rs.getLong(i + 1).takeUnless { rs.wasNull() }
        override fun double(i: Int) = rs.getDouble(i + 1)
        override fun string(i: Int): String = rs.getString(i + 1)
        override fun stringOrNull(i: Int): String? = rs.getString(i + 1)
    }
}

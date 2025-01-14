package com.example.dmd

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "TemperatureReadings.db"
        private const val DATABASE_VERSION = 1

        const val TABLE_NAME = "readings"
        const val COLUMN_ID = "id"
        const val COLUMN_TEMPERATURE = "temperature"
        const val COLUMN_TIMESTAMP = "timestamp"
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createTableQuery = """
            CREATE TABLE $TABLE_NAME (
                $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_TEMPERATURE TEXT NOT NULL,
                $COLUMN_TIMESTAMP DATETIME DEFAULT CURRENT_TIMESTAMP
            )
        """
        db.execSQL(createTableQuery)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_NAME")
        onCreate(db)
    }

    fun getLastNReadings(n: Int): List<Pair<String, String>> {
        val readings = mutableListOf<Pair<String, String>>()
        val db = readableDatabase
        val query = "SELECT temperature, timestamp FROM readings ORDER BY timestamp DESC LIMIT $n"
        val cursor = db.rawQuery(query, null)
        while (cursor.moveToNext()) {
            val temperature = cursor.getString(cursor.getColumnIndexOrThrow("temperature"))
            val timestamp = cursor.getString(cursor.getColumnIndexOrThrow("timestamp"))
            readings.add(Pair(temperature, timestamp))
        }
        cursor.close()
        db.close()
        return readings
    }


    fun insertTemperature(temperature: String): Long {
        val db = writableDatabase
        val contentValues = ContentValues().apply {
            put(COLUMN_TEMPERATURE, temperature)
        }
        return db.insert(TABLE_NAME, null, contentValues)
    }

    fun getAllReadings(): List<Pair<String, String>> {
        val db = readableDatabase
        val cursor = db.query(
            TABLE_NAME,
            arrayOf(COLUMN_TEMPERATURE, COLUMN_TIMESTAMP),
            null, null, null, null,
            "$COLUMN_TIMESTAMP DESC"
        )

        val readings = mutableListOf<Pair<String, String>>()
        cursor.use {
            while (cursor.moveToNext()) {
                val temperature = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_TEMPERATURE))
                val timestamp = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_TIMESTAMP))
                readings.add(Pair(temperature, timestamp))
            }
        }
        return readings
    }
}

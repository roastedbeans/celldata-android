package com.example.celldata

import android.content.Context
import android.database.sqlite.SQLiteDatabase

object DatabaseSingleton {
    private var database: SQLiteDatabase? = null

    fun getDatabase(context: Context): SQLiteDatabase {
        if (database == null) {
            database = DatabaseHelper(context).writableDatabase
            database?.execSQL("PRAGMA busy_timeout = 3000")  // 잠금 대기 시간 설정 (3초)
        }
        return database!!
    }
}
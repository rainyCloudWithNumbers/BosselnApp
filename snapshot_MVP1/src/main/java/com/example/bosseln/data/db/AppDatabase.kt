package com.example.bosseln.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [Team::class, Match::class, ThrowEvent::class, Player::class],
    version = 2, exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun teamDao(): TeamDao
    abstract fun matchDao(): MatchDao
    abstract fun throwDao(): ThrowDao
    abstract fun playerDao(): PlayerDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null
        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "bosseln.db"
                )
                // einfache Auto-Migration: DB neu aufbauen, wenn Version springt
                .fallbackToDestructiveMigration()
                .build().also { INSTANCE = it }
            }
    }
}

package com.example.bosseln.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [ 
        Team::class,
        Player::class,
        Match::class,
        MatchTeam::class,
        MatchResult::class,
        ThrowEvent::class,
        CounterAdjustment::class],
    version = 5,
     exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
        abstract fun teamDao(): TeamDao
    abstract fun playerDao(): PlayerDao
    abstract fun matchDao(): MatchDao
    abstract fun matchTeamDao(): MatchTeamDao
    abstract fun matchResultDao(): MatchResultDao
    abstract fun throwDao(): ThrowDao
    abstract fun counterAdjustmentDao(): CounterAdjustmentDao

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

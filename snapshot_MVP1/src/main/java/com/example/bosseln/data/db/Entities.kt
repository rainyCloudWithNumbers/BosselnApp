package com.example.bosseln.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity
data class Team(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val colorArgb: Long = 0xFF2196F3
)

@Entity
data class Match(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val startedAt: Long = System.currentTimeMillis()
)

@Entity(
    foreignKeys = [
        ForeignKey(entity = Match::class, parentColumns = ["id"], childColumns = ["matchId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Team::class, parentColumns = ["id"], childColumns = ["teamId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index("matchId"), Index("teamId")]
)
data class ThrowEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val matchId: Long,
    val teamId: Long,
    val sequence: Int,              // laufende Wurfnummer
    val kind: String,               // "ABWURF" | "KUGEL_LIEGT"
    val timestamp: Long = System.currentTimeMillis(),
    val lat: Double? = null,
    val lon: Double? = null,
    val accuracyM: Float? = null,
    val photoUri: String? = null,
    val distanceSinceLastM: Double? = null
)

@Entity(
    foreignKeys = [
        ForeignKey(entity = Team::class, parentColumns = ["id"], childColumns = ["teamId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index("teamId")]
)
data class Player(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val teamId: Long,
    val name: String,
    val orderIndex: Int = 0 // Reihenfolge/Rotation
)
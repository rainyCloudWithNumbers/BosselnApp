package com.example.bosseln.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "teams")
data class Team(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val colorArgb: Long = 0xFF2196F3
)



@Entity(
    tableName = "throw_events",
    foreignKeys = [
        ForeignKey(entity = Match::class, parentColumns = ["id"], childColumns = ["matchId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Team::class,  parentColumns = ["id"], childColumns = ["teamId"],  onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Player::class, parentColumns = ["id"], childColumns = ["playerId"], onDelete = ForeignKey.SET_NULL) // 
    ],
    indices = [Index("matchId"), Index("teamId"), Index("playerId")] //  playerId index
)
data class ThrowEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val matchId: Long,
    val teamId: Long,
    val sequence: Int,
    val kind: String,               // "ABWURF"
    val timestamp: Long = System.currentTimeMillis(),
    val lat: Double? = null,
    val lon: Double? = null,
    val accuracyM: Float? = null,
    val photoUri: String? = null,
    val distanceSinceLastM: Double? = null,
    val playerId: Long? = null      // 
)

// -----  Player (optionale Werferliste pro Team) -----

@Entity(
    tableName = "players",
    foreignKeys = [
        ForeignKey(entity = Team::class, parentColumns = ["id"], childColumns = ["teamId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index("teamId")]
)
data class Player(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val teamId: Long,
    val name: String,
    val orderIndex: Int = 0
)


// ----- NEU: CounterAdjustment (Zähler-Korrektur je Match+Team) -----

@Entity(
    tableName = "counter_adjustments",
    indices = [Index(value = ["matchId", "teamId"], unique = true)]
)
data class CounterAdjustment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val matchId: Long,
    val teamId: Long,
    val offset: Int = 0
)

// Match bekommt ein Ende-Zeitfeld (null = läuft)

@Entity(tableName = "matches")
data class Match(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val startedAt: Long = System.currentTimeMillis(),
    val endedAt: Long? = null
)

// Teilnehmer eines Matches (für History & Auswertung)

@Entity(
    tableName = "match_teams",
    primaryKeys = ["matchId", "teamId"],
    indices = [Index("teamId")],
    foreignKeys = [
        ForeignKey(entity = Match::class, parentColumns = ["id"], childColumns = ["matchId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Team::class,  parentColumns = ["id"], childColumns = ["teamId"],  onDelete = ForeignKey.CASCADE)
    ]
)
data class MatchTeam(
    val matchId: Long,
    val teamId: Long
)

@Entity(
    tableName = "match_results",
    indices = [Index("matchId"), Index("teamId")],
    foreignKeys = [
        ForeignKey(entity = Match::class, parentColumns = ["id"], childColumns = ["matchId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Team::class,  parentColumns = ["id"], childColumns = ["teamId"],  onDelete = ForeignKey.CASCADE)
    ]
)
data class MatchResult(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val matchId: Long,
    val teamId: Long,
    val throwsCount: Int,
    val totalDistanceM: Double
)
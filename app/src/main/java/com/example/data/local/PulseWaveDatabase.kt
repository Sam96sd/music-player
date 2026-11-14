package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        TrackEntity::class,
        SavedLyricsEntity::class,
        PlaylistEntity::class,
        PlaylistTrackCrossRef::class,
        BlacklistedFolderEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class PulseWaveDatabase : RoomDatabase() {
    abstract fun musicDao(): MusicDao

    companion object {
        @Volatile
        private var INSTANCE: PulseWaveDatabase? = null

        fun getInstance(context: Context): PulseWaveDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    PulseWaveDatabase::class.java,
                    "pulsewave_studio.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

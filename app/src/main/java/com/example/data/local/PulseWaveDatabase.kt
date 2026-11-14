package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        TrackEntity::class,
        SavedLyricsEntity::class,
        PlaylistEntity::class,
        PlaylistTrackCrossRef::class,
        BlacklistedFolderEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class PulseWaveDatabase : RoomDatabase() {
    abstract fun musicDao(): MusicDao

    companion object {
        @Volatile
        private var INSTANCE: PulseWaveDatabase? = null

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tracks ADD COLUMN customSearchTitle TEXT")
                db.execSQL("ALTER TABLE tracks ADD COLUMN customSearchArtist TEXT")
            }
        }

        fun getInstance(context: Context): PulseWaveDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    PulseWaveDatabase::class.java,
                    "pulsewave_studio.db"
                )
                    .addMigrations(MIGRATION_2_3)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

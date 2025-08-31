package com.calibre.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.calibre.data.dao.*
import com.calibre.data.model.*

/**
 * The main Room database class for the Calibre application.
 *
 * This class is the central access point to the underlying SQLite database. It brings together
 * all the entities (tables) and DAOs (data access objects).
 *
 * It follows the singleton pattern to ensure only one instance of the database is ever created.
 */
@Database(
    entities = [
        Book::class,
        Author::class,
        Tag::class,
        Publisher::class,
        Series::class,
        BookAuthorCrossRef::class,
        BookTagCrossRef::class,
        BookPublisherCrossRef::class
    ],
    version = 1,
    exportSchema = false // Schema exporting is disabled for this example
)
abstract class AppDatabase : RoomDatabase() {

    // Abstract methods to provide access to each DAO
    abstract fun bookDao(): BookDao
    abstract fun authorDao(): AuthorDao
    abstract fun tagDao(): TagDao
    abstract fun publisherDao(): PublisherDao
    abstract fun seriesDao(): SeriesDao

    companion object {
        // The volatile annotation ensures that the INSTANCE variable is always up-to-date
        // and visible to all threads.
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * Gets the singleton instance of the AppDatabase.
         *
         * @param context The application context.
         * @return The singleton AppDatabase instance.
         */
        fun getInstance(context: Context): AppDatabase {
            // synchronized block ensures that only one thread can execute this code at a time,
            // preventing multiple instances from being created.
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "calibre_library.db"
                )
                // In a real application, you would need a proper migration strategy.
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

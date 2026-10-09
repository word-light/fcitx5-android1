package com.kazumaproject.markdownhelperkeyboard

import androidx.room.Database
import androidx.room.RoomDatabase
import com.kazumaproject.markdownhelperkeyboard.learning.database.LearnDao
import com.kazumaproject.markdownhelperkeyboard.learning.database.LearnEntity
import com.kazumaproject.markdownhelperkeyboard.user_dictionary.database.UserWord
import com.kazumaproject.markdownhelperkeyboard.user_dictionary.database.UserWordDao

/** SogaKey: small database for the vendored Sumire converter (learning history + user words). */
@Database(
    entities = [LearnEntity::class, UserWord::class],
    version = 1,
    exportSchema = false
)
abstract class SogaJpDatabase : RoomDatabase() {
    abstract fun learnDao(): LearnDao
    abstract fun userWordDao(): UserWordDao
}

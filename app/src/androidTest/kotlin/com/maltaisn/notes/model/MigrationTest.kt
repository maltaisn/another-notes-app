/*
 * Copyright 2025 Nicolas Maltais
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.maltaisn.notes.model

import androidx.core.database.getIntOrNull
import androidx.core.database.getLongOrNull
import androidx.core.database.getStringOrNull
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.maltaisn.notes.model.entity.FractionalIndex
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException
import kotlin.random.Random
import kotlin.random.nextLong
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(),
        NotesDatabase::class.java, emptyList(), FrameworkSQLiteOpenHelperFactory())

    @Test
    @Throws(IOException::class)
    fun migrate1To2() {
        // No data changes, schema is validated by helper.
        helper.createDatabase(DB_NAME, 1)
        helper.runMigrationsAndValidate(DB_NAME, 2, true, NotesDatabase.MIGRATION_1_2)
    }

    @Test
    @Throws(IOException::class)
    fun migrate2To3() {
        // Test that pinned default value is `false`, new reminder columns defaults
        helper.createDatabase(DB_NAME, 2).apply {
            execSQL("""
                INSERT INTO notes (id, type, title, content, metadata, added_date, modified_date, status) 
                VALUES (1, 0, 'note1', 'content', '{"type":"blank"}', 1577836800000, 1577836800000, 0),
                (2, 0, 'note2', 'content', '{"type":"blank"}', 1577836800000, 1577836800000, 1)
                """.trimIndent())
            close()
        }
        val db = helper.runMigrationsAndValidate(DB_NAME, 3, true, NotesDatabase.MIGRATION_2_3)

        val cursor = db.query("""SELECT pinned, reminder_start, reminder_recurrence,
                                       reminder_next, reminder_count, reminder_done FROM notes""")
        cursor.moveToFirst()
        assertEquals(1, cursor.getInt(0))
        assertNull(cursor.getLongOrNull(1))
        assertNull(cursor.getStringOrNull(2))
        assertNull(cursor.getLongOrNull(3))
        assertNull(cursor.getIntOrNull(4))
        assertNull(cursor.getIntOrNull(5))
        cursor.moveToNext()
        assertEquals(0, cursor.getInt(0))
        cursor.close()

        db.close()
    }

    @Test
    @Throws(IOException::class)
    fun migrate3To4() {
        // Test that default hidden status for label is false
        helper.createDatabase(DB_NAME, 3).apply {
            execSQL("""
                INSERT INTO labels (id, name) VALUES (1, 'label')
                """.trimIndent())
            close()
        }
        val db = helper.runMigrationsAndValidate(DB_NAME, 4, true, NotesDatabase.MIGRATION_3_4)

        val cursor = db.query("""SELECT hidden FROM labels""")
        cursor.moveToFirst()
        assertEquals(0, cursor.getInt(0))
        cursor.close()

        db.close()
    }

    @Test
    @Throws(IOException::class)
    fun migrate4To5() {
        // Test that the rank column is created and default values are correct.
        val rng = Random(0)
        for (count in listOf(0, 1, 256, 257)) {
            val db = helper.createDatabase(DB_NAME, 4)
            val notes = mutableListOf<Pair<Int, Long>>()
            repeat(count) { id ->
                val modifiedDate = rng.nextLong(0L..10000L)
                db.execSQL("""
                    INSERT INTO notes (id, type, title, content, metadata, added_date, modified_date, status, pinned, 
                                       reminder_start, reminder_recurrence, reminder_next, reminder_count, reminder_done) 
                    VALUES ($id, 0, "", "", "", 0, $modifiedDate, 0, 0, NULL, NULL, NULL, NULL, NULL);
                """.trimIndent())
                notes += id to modifiedDate
            }
            notes.sortWith(compareByDescending<Pair<Int, Long>> { it.second }.thenBy { it.first })

            helper.runMigrationsAndValidate(DB_NAME, 5, true, NotesDatabase.MIGRATION_4_5)

            val cursor = db.query("""SELECT id, rank FROM notes ORDER BY rank ASC, id ASC""")
            cursor.moveToFirst()
            var rankIndex = FractionalIndex.INITIAL
            for ((id, _) in notes) {
                assertEquals(id, cursor.getInt(0))
                val rankBlob = cursor.getBlob(1)
                assertTrue(rankBlob.isNotEmpty())
                assertEquals(rankIndex, FractionalIndex.fromBytes(rankBlob))
                cursor.moveToNext()
                rankIndex = rankIndex.append()
            }
            cursor.close()

            db.close()
        }
    }

    @Test
    @Throws(IOException::class)
    fun migrateAll() {
        // Create earliest version of the database.
        helper.createDatabase(DB_NAME, 1).apply {
            close()
        }

        // Open latest version of the database. Room will validate the schema once all migrations execute.
        Room.databaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            NotesDatabase::class.java,
            DB_NAME
        ).addMigrations(*NotesDatabase.ALL_MIGRATIONS)
            .build().apply {
                openHelper.writableDatabase
                close()
            }
    }

    companion object {
        private const val DB_NAME = "migration-test"
    }
}

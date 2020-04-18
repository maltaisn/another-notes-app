/*
 * Copyright 2020 Nicolas Maltais
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

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.maltaisn.notes.model.entity.DeletedNote
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals


@RunWith(AndroidJUnit4::class)
class DeletedNotesDaoTest {

    private lateinit var database: NotesDatabase
    private lateinit var deletedNotesDao: DeletedNotesDao

    @Before
    fun createDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, NotesDatabase::class.java).build()
        deletedNotesDao = database.deletedNotesDao()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun readWriteTests()  = runBlocking {
        deletedNotesDao.insert(DeletedNote(0, "0", false))
        assertEquals(listOf("0"), deletedNotesDao.getNotSyncedUuids())

        deletedNotesDao.insertAll(listOf(
                DeletedNote(0, "1", false),
                DeletedNote(0, "2", false)))
        assertEquals(listOf("0", "1", "2"), deletedNotesDao.getNotSyncedUuids())
    }

    @Test
    fun getNotSyncedUuidsTest()  = runBlocking {
        deletedNotesDao.insertAll(listOf(
                DeletedNote(0, "0", false),
                DeletedNote(0, "1", true),
                DeletedNote(0, "2", false)))
        assertEquals(listOf("0", "2"), deletedNotesDao.getNotSyncedUuids())
    }

    @Test
    fun setSyncedFlagTest()  = runBlocking {
        deletedNotesDao.insertAll(listOf(
                DeletedNote(0, "0", false),
                DeletedNote(0, "1", true),
                DeletedNote(0, "2", false)))
        deletedNotesDao.setSyncedFlag(true)
        assertEquals(emptyList(), deletedNotesDao.getNotSyncedUuids())
    }

}

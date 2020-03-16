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
import com.maltaisn.notes.model.entity.ChangeEvent
import com.maltaisn.notes.model.entity.ChangeEventType
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals


@RunWith(AndroidJUnit4::class)
class ChangeEventsDaoTest {

    private lateinit var database: NotesDatabase
    private lateinit var changesDao: ChangeEventsDao

    @Before
    fun createDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, NotesDatabase::class.java).build()
        changesDao = database.changeEventsDao()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun readWriteTests()  = runBlocking {
        val event = ChangeEvent("1", ChangeEventType.ADDED)

        changesDao.insert(event)
        assertEquals(event, changesDao.getByUuid(event.uuid))

        assertEquals(listOf(event), changesDao.getAll())

        changesDao.clear()
        assertEquals(emptyList(), changesDao.getAll())
    }

}

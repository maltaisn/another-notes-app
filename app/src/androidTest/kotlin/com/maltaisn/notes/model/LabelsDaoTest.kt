/*
 * Copyright 2021 Nicolas Maltais
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
import com.maltaisn.notes.model.entity.Label
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertNull

@RunWith(AndroidJUnit4::class)
class LabelsDaoTest {

    private lateinit var database: NotesDatabase
    private lateinit var labelsDao: LabelsDao

    @Before
    fun createDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, NotesDatabase::class.java).build()
        labelsDao = database.labelsDao()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun readWriteTests() = runBlocking {
        val labelNoId = Label(Label.NO_ID, "label1")

        // Insert
        val id = labelsDao.insert(labelNoId)
        val label = labelNoId.copy(id = id)
        assertEquals(label, labelsDao.getById(id))
        assertEquals(label, labelsDao.getLabelByName("label1"))

        // Update with insert
        val updatedLabel0 = Label(1, "updated label1")
        labelsDao.insert(updatedLabel0)
        assertEquals(updatedLabel0, labelsDao.getById(1))
        assertEquals(updatedLabel0, labelsDao.getLabelByName("updated label1"))

        // Update directly
        val updatedLabel1 = Label(1, "updated label1 again")
        labelsDao.update(updatedLabel1)
        assertEquals(updatedLabel1, labelsDao.getById(id))
        assertEquals(updatedLabel1, labelsDao.getLabelByName("updated label1 again"))

        // Delete
        labelsDao.delete(updatedLabel1)
        assertNull(labelsDao.getById(id))
        assertNull(labelsDao.getLabelByName("updated label1 again"))
    }

    @Test
    fun clearTest() = runBlocking {
        labelsDao.insert(Label(1, "label1"))
        labelsDao.insert(Label(2, "label2"))
        labelsDao.insert(Label(3, "label3"))
        labelsDao.clear()
        assertEquals(emptyList(), labelsDao.getAll().first())
    }

}

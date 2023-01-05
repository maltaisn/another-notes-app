/*
 * Copyright 2023 Nicolas Maltais
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
import com.maltaisn.notes.model.entity.LabelRef
import com.maltaisn.notesshared.testNote
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
    private lateinit var notesDao: NotesDao

    @Before
    fun createDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, NotesDatabase::class.java).build()
        labelsDao = database.labelsDao()
        notesDao = database.notesDao()
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
    fun getAllTest() = runBlocking {
        val labels = listOf(
            Label(1, "label1"),
            Label(2, "label2"),
            Label(3, "label3"),
        )
        for (label in labels) {
            labelsDao.insert(label)
        }
        assertEquals(labels.toSet(), labelsDao.getAll().toSet())
    }

    @Test
    fun clearTest() = runBlocking {
        labelsDao.insert(Label(1, "label1"))
        labelsDao.insert(Label(2, "label2"))
        labelsDao.insert(Label(3, "label3"))
        labelsDao.clear()
        assertEquals(emptyList(), labelsDao.getAll())
    }

    @Test
    fun changeLabelRefsTest() = runBlocking {
        notesDao.insertAll(listOf(
            testNote(id = 1),
            testNote(id = 2),
            testNote(id = 3),
        ))
        for (i in 1L..5L) {
            labelsDao.insert(Label(i, "label$i"))
        }
        labelsDao.insertRefs(listOf(
            LabelRef(1, 1),
            LabelRef(1, 5),
            LabelRef(2, 1),
            LabelRef(2, 3),
            LabelRef(2, 4),
            LabelRef(3, 1),
            LabelRef(3, 4),
            LabelRef(3, 5),
        ))
        assertEquals(3, labelsDao.countRefs(1))
        assertEquals(2, labelsDao.countRefs(5))
        assertEquals(1, labelsDao.countRefs(3))
        assertEquals(0, labelsDao.countRefs(2))
        assertEquals(setOf(1L, 5L), labelsDao.getLabelIdsForNote(1).toSet())
        assertEquals(setOf(1L, 3L, 4L), labelsDao.getLabelIdsForNote(2).toSet())
        assertEquals(setOf(1L, 4L, 5L), labelsDao.getLabelIdsForNote(3).toSet())

        labelsDao.deleteRefs(listOf(
            LabelRef(1, 5),
            LabelRef(2, 3),
            LabelRef(2, 4),
            LabelRef(3, 4),
            LabelRef(3, 5),
        ))
        assertEquals(3, labelsDao.countRefs(1))
        assertEquals(0, labelsDao.countRefs(5))
        assertEquals(setOf(1L), labelsDao.getLabelIdsForNote(1).toSet())
        assertEquals(setOf(1L), labelsDao.getLabelIdsForNote(2).toSet())
        assertEquals(setOf(1L), labelsDao.getLabelIdsForNote(3).toSet())
    }

    @Test
    fun getAllByUsageTest() = runBlocking {
        // insert 5 notes, note 5 having no labels,
        // and each note having one more label, so that note 1 has 4 labels.
        notesDao.insert(testNote(id = 1))
        notesDao.insert(testNote(id = 2))
        notesDao.insert(testNote(id = 3))

        labelsDao.insert(Label(1, "label1"))
        labelsDao.insert(Label(2, "label2"))
        labelsDao.insert(Label(3, "label3"))
        labelsDao.insert(Label(4, "label4"))
        labelsDao.insert(Label(5, "label5"))

        labelsDao.insertRefs(listOf(
            LabelRef(1, 5),
            LabelRef(2, 5),
            LabelRef(3, 5),
            LabelRef(1, 3),
            LabelRef(2, 3),
            LabelRef(1, 2),
        ))

        assertEquals(listOf(
            labelsDao.getById(5),
            labelsDao.getById(3),
            labelsDao.getById(2),
            labelsDao.getById(1),
            labelsDao.getById(4),
        ), labelsDao.getAllByUsage().first())
    }
}

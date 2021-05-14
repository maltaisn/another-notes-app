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
import com.maltaisn.notes.dateFor
import com.maltaisn.notes.model.entity.BlankNoteMetadata
import com.maltaisn.notes.model.entity.Label
import com.maltaisn.notes.model.entity.LabelRef
import com.maltaisn.notes.model.entity.ListNoteMetadata
import com.maltaisn.notes.model.entity.Note
import com.maltaisn.notes.model.entity.NoteStatus
import com.maltaisn.notes.model.entity.NoteType
import com.maltaisn.notes.model.entity.PinnedStatus
import com.maltaisn.notes.model.entity.Reminder
import com.maltaisn.recurpicker.Recurrence
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class DefaultJsonExporterTest {

    private lateinit var database: NotesDatabase
    private lateinit var notesDao: NotesDao
    private lateinit var labelsDao: LabelsDao

    @Before
    fun createDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, NotesDatabase::class.java).build()
        notesDao = database.notesDao()
        labelsDao = database.labelsDao()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun testJsonExport() = runBlocking {
        val jsonExporter = DefaultJsonExporter(notesDao, labelsDao, Json {})

        notesDao.insert(Note(id = 1,
            type = NoteType.TEXT,
            title = "note",
            content = "content",
            metadata = BlankNoteMetadata,
            addedDate = dateFor("2020-01-01"),
            lastModifiedDate = dateFor("2020-02-01"),
            status = NoteStatus.ACTIVE,
            pinned = PinnedStatus.PINNED,
            reminder = Reminder(dateFor("2020-03-01"), Recurrence(Recurrence.Period.DAILY),
                dateFor("2020-03-02"), 1, false)
        ))
        notesDao.insert(Note(id = 9,
            type = NoteType.LIST,
            title = "list",
            content = "item 1\nitem 2",
            metadata = ListNoteMetadata(listOf(false, true)),
            addedDate = dateFor("2019-01-01"),
            lastModifiedDate = dateFor("2019-02-01"),
            status = NoteStatus.ARCHIVED,
            pinned = PinnedStatus.CANT_PIN,
            reminder = null
        ))

        labelsDao.insert(Label(1, "label0"))
        labelsDao.insert(Label(3, "label1"))
        labelsDao.insert(Label(10, "label2"))

        labelsDao.insertRefs(listOf(
            LabelRef(1, 1),
            LabelRef(1, 10),
            LabelRef(9, 1),
            LabelRef(9, 3),
        ))

        assertEquals("""
{"version":3,"notes":{"1":{"type":0,"title":"note","content":"content","metadata":"{\"type\":\"blank\"}",
"added":"2020-01-01T05:00:00.000Z","modified":"2020-02-01T05:00:00.000Z","status":0,"pinned":2,
"reminder":{"start":"2020-03-01T05:00:00.000Z","recurrence":"RRULE:FREQ=DAILY",
"next":"2020-03-02T05:00:00.000Z","count":1,"done":false},"labels":[1,10]},"9":{"type":1,
"title":"list","content":"item 1\nitem 2","metadata":"{\"type\":\"list\",\"checked\":[false,true]}",
"added":"2019-01-01T05:00:00.000Z","modified":"2019-02-01T05:00:00.000Z","status":1,"pinned":0,
"reminder":null,"labels":[1,3]}},"labels":{"1":{"name":"label0"},"3":{"name":"label1"},
"10":{"name":"label2"}}}
        """.trim().replace("\n", ""), jsonExporter.exportJsonData())
    }

}

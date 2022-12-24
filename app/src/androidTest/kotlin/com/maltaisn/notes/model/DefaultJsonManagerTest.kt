/*
 * Copyright 2022 Nicolas Maltais
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
import com.maltaisn.notes.model.DefaultJsonManager.ImportResult
import com.maltaisn.notes.model.entity.BlankNoteMetadata
import com.maltaisn.notes.model.entity.Label
import com.maltaisn.notes.model.entity.LabelRef
import com.maltaisn.notes.model.entity.ListNoteMetadata
import com.maltaisn.notes.model.entity.Note
import com.maltaisn.notes.model.entity.NoteStatus
import com.maltaisn.notes.model.entity.NoteType
import com.maltaisn.notes.model.entity.NoteWithLabels
import com.maltaisn.notes.model.entity.PinnedStatus
import com.maltaisn.notes.model.entity.Reminder
import com.maltaisn.notes.testNote
import com.maltaisn.recurpicker.Recurrence
import com.nhaarman.mockitokotlin2.mock
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.intellij.lang.annotations.Language
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class DefaultJsonManagerTest {

    private lateinit var database: NotesDatabase
    private lateinit var notesDao: NotesDao
    private lateinit var labelsDao: LabelsDao

    private lateinit var jsonManager: JsonManager

    @Before
    fun createDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, NotesDatabase::class.java).build()
        notesDao = database.notesDao()
        labelsDao = database.labelsDao()
        jsonManager = DefaultJsonManager(notesDao, labelsDao, Json {
            encodeDefaults = false
            ignoreUnknownKeys = true
        }, mock())
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun testJsonExport() = runBlocking {
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

        labelsDao.insert(Label(1, "label0", false))
        labelsDao.insert(Label(10, "label2", true))

        labelsDao.insertRefs(listOf(
            LabelRef(1, 1),
            LabelRef(1, 10),
        ))

        val jsonData = jsonManager.exportJsonData()
        assertEquals("""
{"version":4,"notes":{"1":{"type":0,"title":"note","content":"content","metadata":"{\"type\":\"blank\"}",
"added":"2020-01-01T05:00:00.000Z","modified":"2020-02-01T05:00:00.000Z","status":0,"pinned":2,
"reminder":{"start":"2020-03-01T05:00:00.000Z","recurrence":"RRULE:FREQ=DAILY",
"next":"2020-03-02T05:00:00.000Z","count":1,"done":false},"labels":[1,10]},"9":{"type":1,
"title":"list","content":"item 1\nitem 2","metadata":"{\"type\":\"list\",\"checked\":[false,true]}",
"added":"2019-01-01T05:00:00.000Z","modified":"2019-02-01T05:00:00.000Z","status":1,"pinned":0}},
"labels":{"1":{"name":"label0"},"10":{"name":"label2","hidden":true}}}
        """.trim().replace("\n", ""), jsonData)
    }

    @Test
    fun testJsonImportClean() = runBlocking {
        @Language("JSON") val jsonData = """{
  "version": 3,
  "notes": {
    "1": {
      "type": 0,
      "title": "note",
      "content": "content",
      "metadata": "{\"type\":\"blank\"}",
      "added": "2020-01-01T05:00:00.000Z",
      "modified": "2020-02-01T05:00:00.000Z",
      "status": 0,
      "pinned": 2,
      "reminder": {
        "start": "2020-03-01T05:00:00.000Z",
        "recurrence": "RRULE:FREQ=DAILY",
        "next": "2020-03-02T05:00:00.000Z",
        "count": 1,
        "done": false
      },
      "labels": [1, 10]
    },
    "9": {
      "type": 1,
      "title": "list",
      "content": "item 1\nitem 2",
      "metadata": "{\"type\":\"list\",\"checked\":[false,true]}",
      "added": "2019-01-01T05:00:00.000Z",
      "modified": "2019-02-01T05:00:00.000Z",
      "status": 1,
      "pinned": 0,
      "labels": [1, 3]
    }
  },
  "labels": {
    "1": {"name": "label0"},
    "3": {"name": "label1"},
    "10": {"name": "label2"}
  }
}
        """.trim().replace("\n", "")
        assertEquals(ImportResult.SUCCESS, jsonManager.importJsonData(jsonData))

        val label1 = Label(1, "label0")
        val label3 = Label(3, "label1")
        val label10 = Label(10, "label2")

        assertEquals(setOf(label1, label3, label10), labelsDao.getAll().toSet())

        assertEquals(setOf(
            NoteWithLabels(Note(id = 1,
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
            ), listOf(label1, label10)),
            NoteWithLabels(Note(id = 9,
                type = NoteType.LIST,
                title = "list",
                content = "item 1\nitem 2",
                metadata = ListNoteMetadata(listOf(false, true)),
                addedDate = dateFor("2019-01-01"),
                lastModifiedDate = dateFor("2019-02-01"),
                status = NoteStatus.ARCHIVED,
                pinned = PinnedStatus.CANT_PIN,
                reminder = null
            ), listOf(label1, label3)),
        ), notesDao.getAll().toSet())
    }

    @Test
    fun testJsonImportMerge() = runBlocking {
        // insert existing data
        val label1 = Label(1, "label0")
        val label3 = Label(3, "label3")
        val label10 = Label(10, "label10")
        notesDao.insertAll(listOf(
            // merge case: same ID, no reminder on old, labels merge.
            testNote(id = 1, added = dateFor("2020-01-01")),
            // merge case: different dates, ID clash.
            testNote(id = 9, added = dateFor("2021-01-01")),
            // merge case: different reminders
            testNote(id = 12, added = dateFor("2021-01-01"),
                reminder = Reminder(dateFor("2021-01-02"), null, dateFor("2021-01-03"), 1, true)),
        ))
        labelsDao.insert(label1)
        labelsDao.insert(label3)
        labelsDao.insert(label10)
        labelsDao.insertRefs(listOf(LabelRef(1, 1), LabelRef(1, 3)))

        @Language("JSON") val jsonData = """{
  "version": 3,
  "notes": {
    "1": {
      "type": 0,
      "title": "note",
      "content": "content",
      "metadata": "{\"type\":\"blank\"}",
      "added": "2020-01-01T05:00:00.000Z",
      "modified": "2020-01-01T05:00:00.000Z",
      "status": 0,
      "pinned": 2,
      "reminder": {
        "start": "2020-03-01T05:00:00.000Z",
        "recurrence": "RRULE:FREQ=DAILY",
        "next": "2020-03-02T05:00:00.000Z",
        "count": 1,
        "done": false
      },
      "labels": [1, 3, 9]
    },
    "9": {
      "type": 1,
      "title": "list",
      "content": "item 1\nitem 2",
      "metadata": "{\"type\":\"list\",\"checked\":[false,true]}",
      "added": "2019-01-01T05:00:00.000Z",
      "modified": "2019-02-01T05:00:00.000Z",
      "status": 1,
      "pinned": 0,
      "reminder": null,
      "labels": [
        1,
        9
      ]
    },
    "12": {
      "type": 0,
      "title": "note",
      "content": "content",
      "metadata": "{\"type\":\"blank\"}",
      "added": "2021-01-01T05:00:00.000Z",
      "modified": "2021-01-01T05:00:00.000Z",
      "status": 0,
      "pinned": 1,
      "reminder": {
        "start": "2021-01-02T05:00:00.000Z",
        "next": "2022-01-02T05:00:00.000Z",
        "count": 1,
        "done": false
      }
    }
  },
  "labels": {
    "1": {"name": "label0"},
    "3": {"name": "label11"},
    "9": {"name": "label10"}
  }
}
        """.trim().replace("\n", "")
        assertEquals(ImportResult.SUCCESS, jsonManager.importJsonData(jsonData))

        val label9 = Label(9, "label10 (2)")
        val label11 = Label(11, "label11")

        assertEquals(setOf(label1, label3, label9, label10, label11), labelsDao.getAll().toSet())

        assertEquals(setOf(
            NoteWithLabels(Note(id = 1,
                type = NoteType.TEXT,
                title = "note",
                content = "content",
                metadata = BlankNoteMetadata,
                addedDate = dateFor("2020-01-01"),
                lastModifiedDate = dateFor("2020-01-01"),
                status = NoteStatus.ACTIVE,
                pinned = PinnedStatus.PINNED,
                reminder = Reminder(dateFor("2020-03-01"), Recurrence(Recurrence.Period.DAILY),
                    dateFor("2020-03-02"), 1, false)
            ), listOf(label1, label3, label9, label11)),
            NoteWithLabels(
                testNote(id = 9, added = dateFor("2021-01-01")),
                emptyList()
            ),
            NoteWithLabels(
                testNote(id = 12, added = dateFor("2021-01-01"),
                    reminder = Reminder(dateFor("2021-01-02"), null, dateFor("2021-01-03"), 1, true)),
                emptyList()
            ),
            NoteWithLabels(Note(id = 13,
                type = NoteType.LIST,
                title = "list",
                content = "item 1\nitem 2",
                metadata = ListNoteMetadata(listOf(false, true)),
                addedDate = dateFor("2019-01-01"),
                lastModifiedDate = dateFor("2019-02-01"),
                status = NoteStatus.ARCHIVED,
                pinned = PinnedStatus.CANT_PIN,
                reminder = null
            ), listOf(label1, label9)),
            NoteWithLabels(
                testNote(id = 14, added = dateFor("2021-01-01"),
                    reminder = Reminder(dateFor("2021-01-02"), null, dateFor("2022-01-02"), 1, false)),
                emptyList()
            ),
        ), notesDao.getAll().toSet())
    }

    @Test
    fun testJsonImportBadData() = runBlocking {
        // wrong json structure
        assertEquals(ImportResult.BAD_FORMAT, jsonManager.importJsonData("clearly not json"))
        // invalid data (bad rrule)
        assertEquals(ImportResult.BAD_FORMAT, jsonManager.importJsonData("""
{"version":3,"notes":{"1":{"type":0,"title":"note","content":"content","metadata":"{\"type\":\"blank\"}",
"added":"2020-01-01T05:00:00.000Z","modified":"2020-02-01T05:00:00.000Z","status":0,"pinned":2,
"reminder":{"start":"2020-03-01T05:00:00.000Z","recurrence":"RRULE:FREQ=MILKY",
"next":"2020-03-02T05:00:00.000Z","count":1,"done":false}}}}
        """.trim().replace("\n", "")))
        // invalid data (missing field)
        assertEquals(ImportResult.BAD_FORMAT, jsonManager.importJsonData("""
{"version":3,"notes":{"1":{"type":0,"title":"note"}}}
        """.trim().replace("\n", "")))
        // invalid version number
        assertEquals(ImportResult.BAD_DATA, jsonManager.importJsonData("""{"version":0}"""))
    }

    @Test
    fun testJsonForwardCompatibility() = runBlocking {
        // importing data with unsupported values for existing fields should fail
        assertEquals(ImportResult.BAD_DATA, jsonManager.importJsonData("""
{"version":11,"notes":{"1":{"type":100,"title":"note","content":"content","metadata":"{\"type\":\"drawing\"}",
"added":"2020-01-01T05:00:00.000Z","modified":"2020-02-01T05:00:00.000Z","status":0,"pinned":2,
"path":"M10,10h10v10h-10Z"}},"data":"data"}
        """.trim().replace("\n", "")))
        assertEquals(ImportResult.FUTURE_VERSION, jsonManager.importJsonData("""
{"version":10,"notes":{"1":{"type":0,"title":"note","content":"content","metadata":"{\"type\":\"blank\"}",
"added":"2020-01-01T05:00:00.000Z","modified":"2020-02-01T05:00:00.000Z","status":0,"pinned":2,
"path":"M10,10h10v10h-10Z"}},"data":"data"}
        """.trim().replace("\n", "")))
    }
}

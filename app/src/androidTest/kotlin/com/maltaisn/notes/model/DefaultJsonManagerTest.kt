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
import android.os.Build
import android.security.keystore.KeyProperties
import android.security.keystore.KeyProtection
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.maltaisn.notes.model.JsonManager.ImportResult
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
import com.maltaisn.notesshared.dateFor
import com.maltaisn.notesshared.testNote
import com.maltaisn.recurpicker.Recurrence
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.intellij.lang.annotations.Language
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.security.KeyStore
import javax.crypto.spec.SecretKeySpec
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class DefaultJsonManagerTest {

    private lateinit var database: NotesDatabase
    private lateinit var notesDao: NotesDao
    private lateinit var labelsDao: LabelsDao

    private lateinit var prefsManager: PrefsManager
    private lateinit var jsonManager: JsonManager

    private fun shouldIgnoreEncryptionTest() = Build.VERSION.SDK_INT < 26

    @Before
    fun createDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, NotesDatabase::class.java).build()
        notesDao = database.notesDao()
        labelsDao = database.labelsDao()
        prefsManager = mock {
            on { encryptedImportKeyDerivationSalt } doReturn "Wk7ITADlYDBTP/o8GtupJyWemvDClrDNxXKhoMBuTIp8QjlAlPdVAWfAi+B3GWTn/YmqgBP3OCK20Vmcm9hQbzwNfnXsnChnPu462ALv+WKf8y2NirINyWr5jG/tAOaE6bGNL+ZE4ClppTdBt82Gl87q6FX0pFqhJtmrE+8jLmI"
            on { encryptedExportKeyDerivationSalt } doReturn "Wk7ITADlYDBTP/o8GtupJyWemvDClrDNxXKhoMBuTIp8QjlAlPdVAWfAi+B3GWTn/YmqgBP3OCK20Vmcm9hQbzwNfnXsnChnPu462ALv+WKf8y2NirINyWr5jG/tAOaE6bGNL+ZE4ClppTdBt82Gl87q6FX0pFqhJtmrE+8jLmI"
        }
        jsonManager = DefaultJsonManager(notesDao, labelsDao, Json {
            encodeDefaults = false
            ignoreUnknownKeys = true
        }, mock(), prefsManager)
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    private fun encryptionSetup(): SecretKeySpec {
        // Set key in KeyStore
        val key = SecretKeySpec(ByteArray(32) { 0x02 }, KeyProperties.KEY_ALGORITHM_AES)
        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)
        keyStore.setEntry(
            "export_key",
            KeyStore.SecretKeyEntry(key),
            KeyProtection.Builder(KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return key
    }

    @Test
    fun testInvalidJsonImport() = runBlocking {
        // wrong json structure
        assertEquals(ImportResult.BAD_FORMAT, jsonManager.importJsonData("clearly not json"))
    }

    @Test
    fun testUnencryptedJsonExport() = runBlocking {
        prefsManager.shouldEncryptExportedData = false
        notesDao.insert(Note(id = 1,
            type = NoteType.TEXT,
            title = "note",
            content = "content",
            metadata = BlankNoteMetadata,
            addedDate = dateFor("2020-01-01Z"),
            lastModifiedDate = dateFor("2020-02-01Z"),
            status = NoteStatus.ACTIVE,
            pinned = PinnedStatus.PINNED,
            reminder = Reminder(dateFor("2020-03-01Z"), Recurrence(Recurrence.Period.DAILY),
                dateFor("2020-03-02Z"), 1, false)
        ))
        notesDao.insert(Note(id = 9,
            type = NoteType.LIST,
            title = "list",
            content = "item 1\nitem 2",
            metadata = ListNoteMetadata(listOf(false, true)),
            addedDate = dateFor("2019-01-01Z"),
            lastModifiedDate = dateFor("2019-02-01Z"),
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
"added":"2020-01-01T00:00:00.000Z","modified":"2020-02-01T00:00:00.000Z","status":0,"pinned":2,
"reminder":{"start":"2020-03-01T00:00:00.000Z","recurrence":"RRULE:FREQ=DAILY",
"next":"2020-03-02T00:00:00.000Z","count":1,"done":false},"labels":[1,10]},"9":{"type":1,
"title":"list","content":"item 1\nitem 2","metadata":"{\"type\":\"list\",\"checked\":[false,true]}",
"added":"2019-01-01T00:00:00.000Z","modified":"2019-02-01T00:00:00.000Z","status":1,"pinned":0}},
"labels":{"1":{"name":"label0"},"10":{"name":"label2","hidden":true}}}
        """.trim().replace("\n", ""), jsonData)
    }

    @Test
    fun testUnencryptedJsonImportClean() = runBlocking {
        @Language("JSON") val jsonData = """{
    "version": 3,
    "notes": {
      "1": {
        "type": 0,
        "title": "note",
        "content": "content",
        "metadata": "{\"type\":\"blank\"}",
        "added": "2020-01-01T00:00:00.000Z",
        "modified": "2020-02-01T00:00:00.000Z",
        "status": 0,
        "pinned": 2,
        "reminder": {
          "start": "2020-03-01T00:00:00.000Z",
          "recurrence": "RRULE:FREQ=DAILY",
          "next": "2020-03-02T00:00:00.000Z",
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
        "added": "2019-01-01T00:00:00.000Z",
        "modified": "2019-02-01T00:00:00.000Z",
        "status": 1,
        "pinned": 0,
        "labels": [1, 3]
      }
    },
    "labels": {
      "1": {"name": "label0", "hidden": true},
      "3": {"name": "label1"},
      "10": {"name": "label2"}
    }
  }
        """.trim().replace("\n", "")
        assertEquals(ImportResult.SUCCESS, jsonManager.importJsonData(jsonData))

        val label1 = Label(1, "label0", hidden = true)
        val label3 = Label(3, "label1")
        val label10 = Label(10, "label2")

        assertEquals(setOf(label1, label3, label10), labelsDao.getAll().toSet())

        assertEquals(setOf(
            NoteWithLabels(Note(id = 1,
                type = NoteType.TEXT,
                title = "note",
                content = "content",
                metadata = BlankNoteMetadata,
                addedDate = dateFor("2020-01-01Z"),
                lastModifiedDate = dateFor("2020-02-01Z"),
                status = NoteStatus.ACTIVE,
                pinned = PinnedStatus.PINNED,
                reminder = Reminder(dateFor("2020-03-01Z"), Recurrence(Recurrence.Period.DAILY),
                    dateFor("2020-03-02Z"), 1, false)
            ), listOf(label1, label10)),
            NoteWithLabels(Note(id = 9,
                type = NoteType.LIST,
                title = "list",
                content = "item 1\nitem 2",
                metadata = ListNoteMetadata(listOf(false, true)),
                addedDate = dateFor("2019-01-01Z"),
                lastModifiedDate = dateFor("2019-02-01Z"),
                status = NoteStatus.ARCHIVED,
                pinned = PinnedStatus.CANT_PIN,
                reminder = null
            ), listOf(label1, label3)),
        ), notesDao.getAll().toSet())
    }

    @Test
    fun testUnencryptedJsonImportMerge() = runBlocking {
        // insert existing data
        val label1 = Label(1, "label0")
        val label3 = Label(3, "label3")
        val label10 = Label(10, "label10")
        notesDao.insertAll(listOf(
            // merge case: same ID, no reminder on old, labels merge.
            testNote(id = 1, added = dateFor("2020-01-01Z")),
            // merge case: different dates, ID clash.
            testNote(id = 9, added = dateFor("2021-01-01Z")),
            // merge case: different reminders
            testNote(id = 12, added = dateFor("2021-01-01Z"),
                reminder = Reminder(dateFor("2021-01-02Z"), null, dateFor("2021-01-03Z"), 1, true)),
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
        "added": "2020-01-01T00:00:00.000Z",
        "modified": "2020-01-01T00:00:00.000Z",
        "status": 0,
        "pinned": 2,
        "reminder": {
          "start": "2020-03-01T00:00:00.000Z",
          "recurrence": "RRULE:FREQ=DAILY",
          "next": "2020-03-02T00:00:00.000Z",
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
        "added": "2019-01-01T00:00:00.000Z",
        "modified": "2019-02-01T00:00:00.000Z",
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
        "added": "2021-01-01T00:00:00.000Z",
        "modified": "2021-01-01T00:00:00.000Z",
        "status": 0,
        "pinned": 1,
        "reminder": {
          "start": "2021-01-03T00:00:00.000Z",
          "next": "2022-01-03T00:00:00.000Z",
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
                addedDate = dateFor("2020-01-01Z"),
                lastModifiedDate = dateFor("2020-01-01Z"),
                status = NoteStatus.ACTIVE,
                pinned = PinnedStatus.PINNED,
                reminder = Reminder(dateFor("2020-03-01Z"), Recurrence(Recurrence.Period.DAILY),
                    dateFor("2020-03-02Z"), 1, false)
            ), listOf(label1, label3, label9, label11)),
            NoteWithLabels(
                testNote(id = 9, added = dateFor("2021-01-01Z")),
                emptyList()
            ),
            NoteWithLabels(
                testNote(id = 12, added = dateFor("2021-01-01Z"),
                    reminder = Reminder(dateFor("2021-01-02Z"), null, dateFor("2021-01-03Z"), 1, true)),
                emptyList()
            ),
            NoteWithLabels(Note(id = 13,
                type = NoteType.LIST,
                title = "list",
                content = "item 1\nitem 2",
                metadata = ListNoteMetadata(listOf(false, true)),
                addedDate = dateFor("2019-01-01Z"),
                lastModifiedDate = dateFor("2019-02-01Z"),
                status = NoteStatus.ARCHIVED,
                pinned = PinnedStatus.CANT_PIN,
                reminder = null
            ), listOf(label1, label9)),
            NoteWithLabels(
                testNote(id = 14, added = dateFor("2021-01-01Z"),
                    reminder = Reminder(dateFor("2021-01-03Z"), null, dateFor("2022-01-03Z"), 1, false)),
                emptyList()
            ),
        ), notesDao.getAll().toSet())
    }

    @Test
    fun testUnencryptedJsonImportBadData() = runBlocking {
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
    fun testUnencryptedJsonForwardCompatibility() = runBlocking {
        // importing data with unsupported values for existing fields should fail
        assertEquals(ImportResult.BAD_DATA, jsonManager.importJsonData("""
{"version":11,"notes":{"1":{"type":100,"title":"note","content":"content","metadata":"{\"type\":\"drawing\"}",
"added":"2020-01-01T00:00:00.000Z","modified":"2020-02-01T00:00:00.000Z","status":0,"pinned":2,
"path":"M10,10h10v10h-10Z"}},"data":"data"}
        """.trim().replace("\n", "")))
        assertEquals(ImportResult.FUTURE_VERSION, jsonManager.importJsonData("""
{"version":10,"notes":{"1":{"type":0,"title":"note","content":"content","metadata":"{\"type\":\"blank\"}",
"added":"2020-01-01T00:00:00.000Z","modified":"2020-02-01T00:00:00.000Z","status":0,"pinned":2,
"path":"M10,10h10v10h-10Z"}},"data":"data"}
        """.trim().replace("\n", "")))
    }

    @Test
    fun testEncryptedJsonExport() = runBlocking {
        if (shouldIgnoreEncryptionTest()) {
            return@runBlocking
        }

        prefsManager.shouldEncryptExportedData = true
        val key = encryptionSetup()
        // Since the GCM nonce is chosen at random, comparing with a static test vector isn't easily possible.
        // Instead, we import the exported data and check if it contains the expected values.
        notesDao.insert(Note(id = 1,
            type = NoteType.TEXT,
            title = "note",
            content = "content",
            metadata = BlankNoteMetadata,
            addedDate = dateFor("2020-01-01Z"),
            lastModifiedDate = dateFor("2020-02-01Z"),
            status = NoteStatus.ACTIVE,
            pinned = PinnedStatus.PINNED,
            reminder = Reminder(dateFor("2020-03-01Z"), Recurrence(Recurrence.Period.DAILY),
                dateFor("2020-03-02Z"), 1, false)
        ))
        notesDao.insert(Note(id = 9,
            type = NoteType.LIST,
            title = "list",
            content = "item 1\nitem 2",
            metadata = ListNoteMetadata(listOf(false, true)),
            addedDate = dateFor("2019-01-01Z"),
            lastModifiedDate = dateFor("2019-02-01Z"),
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

        notesDao.clear()
        labelsDao.clear()

        assertEquals(ImportResult.SUCCESS, jsonManager.importJsonData(jsonData, importKey = key))

        val label1 = Label(1, "label0", false)
        val label10 = Label(10, "label2", true)
        assertEquals(setOf(label1, label10), labelsDao.getAll().toSet())

        assertEquals(setOf(
            NoteWithLabels(Note(id = 1,
                type = NoteType.TEXT,
                title = "note",
                content = "content",
                metadata = BlankNoteMetadata,
                addedDate = dateFor("2020-01-01Z"),
                lastModifiedDate = dateFor("2020-02-01Z"),
                status = NoteStatus.ACTIVE,
                pinned = PinnedStatus.PINNED,
                reminder = Reminder(dateFor("2020-03-01Z"), Recurrence(Recurrence.Period.DAILY),
                    dateFor("2020-03-02Z"), 1, false)
            ), listOf(label1, label10)),
            NoteWithLabels(Note(id = 9,
                type = NoteType.LIST,
                title = "list",
                content = "item 1\nitem 2",
                metadata = ListNoteMetadata(listOf(false, true)),
                addedDate = dateFor("2019-01-01Z"),
                lastModifiedDate = dateFor("2019-02-01Z"),
                status = NoteStatus.ARCHIVED,
                pinned = PinnedStatus.CANT_PIN,
                reminder = null
            ), listOf()),
        ), notesDao.getAll().toSet())
    }

    @Test
    fun testEncryptedJsonImportClean() = runBlocking {
        if (shouldIgnoreEncryptionTest()) {
            return@runBlocking
        }

        val key = encryptionSetup()
        // The test data is identical to the one used in testUnencryptedJsonImportClean
        @Language("JSON") val jsonData = """{
    "salt": "Wk7ITADlYDBTP/o8GtupJyWemvDClrDNxXKhoMBuTIp8QjlAlPdVAWfAi+B3GWTn/YmqgBP3OCK20Vmcm9hQbzwNfnXsnChnPu462ALv+WKf8y2NirINyWr5jG/tAOaE6bGNL+ZE4ClppTdBt82Gl87q6FX0pFqhJtmrE+8jLmI",
    "nonce":"B+kFILwu8GLF1noQ",
    "ciphertext":"DwSQ5XQfkqTjhJf58JbrDZAMLhGQfnYWS4zWcrXwkkvQDAlwmE77vyatw1xem/iKqCk2oq24c1+tlOSXEiczT1JY12W6YiLN9o9v3xkYYhIrNuPTSMgtG2n8BoEpvop6Wa1ZJNlq323WnoDvlBzOUovmyzomCqrlQ8+d6xgpfBi2YPDtg+QRpUg0mCz9CBBDQuDTMdWg0UD+AucChEKwXaG0VRAKPtRFgf/qALeC4r8oPwsS3UnLsLELZGWauG8QUl7lGUzR0Ayqruk7PaEG4tOHuN3jcPBwOZHKwDZUti/Ybzn4ClKuBN2gFpTqsSYM8vhpBDz+iJ7fwLiuLAX6yvJLwiex4TACi/ZvcNTjxk2mhSaqwObbY09CbGvYgPb20N+PGdOxSxoz1LnFT6lH0XqUEgjDcIR9av8yNWx41sWNBn9Q3i43zrRe19ygzGCAgU/defKtF7pA27f905UOKY2182qqxtUIBd82Bicgd2wt3j6t7/3o9neZTawGg03pyGQt+u1S6OEt3rcmpjwEr+9PG8alCkqHQEBjqPlpSCU0aBDCV0J01Ck6MTE+qAXYEYjHdCgVpvoQ96MNGEJl3ngkhweZcF+j+Y30LG4SnGHeym18m9yzuYtOm3D6nJ0AMiM/0cB1Qkaes8Naxl/Uesusc41000Y17qNBqhbMy16KEERw3xwnzdKlfE4rSo6L/o8XLolPpHvECmQR3VPZX835O+dAilHtThqYpBMU4AktJBf1ywvH2wNoiDWYGsuTpgZ5C97kJfvk0TF7bAPQtG/KiJxDy9G/OMGsHk3D9m8x5yhvdyw5r2+N5nD/DqY0bVsyqg7+FFzD+mPk60v29PuoBautxkqyoHhkQlEIM+Z6/D6eNO6hz50+SkplJVT8f0bURMmRPcKdVaaX/1yH5ztiO3WZsyIuRpx9cCTjgX51bGuvs1gveEjkw4f/squCem/XD0GBxFoOQjIfeVs5PXsgqiSCCSMjwF1sHY71dfBbTNAiR2PsK47LlrPMsrTJBkKqZptHZy+R+u1sZ9TL/I1kvqP0yoncAPUvzcTANzPyWY1zZL3Qrv5kB/5GtNwDJ33oblHfz/MU6vE/lR5sdo1y/tU5WVLz1OC3VL15h37w0dbgcsFvSZD/pGcyCW4OVYHpIXivnO+VXZmDzjBPnap35PAa5ggRjNkgGtaU8g9NGy4xjq9bLKPvKPK+U8moIbCOQYxENEsUJBjWf/X/r+aXLFoddEVOc/DYLoBTb24O95pi5SxLLCbEcKTmfnYL4iKCsMIMaNXMPM16DBOif4BhyUH1oZZz/E4swyhe3J8vxhUPqwI2njQ"
}
        """.trim().replace("\n", "")
        assertEquals(ImportResult.SUCCESS, jsonManager.importJsonData(jsonData, importKey = key))

        val label1 = Label(1, "label0", hidden = true)
        val label3 = Label(3, "label1")
        val label10 = Label(10, "label2")

        assertEquals(setOf(label1, label3, label10), labelsDao.getAll().toSet())

        assertEquals(setOf(
            NoteWithLabels(Note(id = 1,
                type = NoteType.TEXT,
                title = "note",
                content = "content",
                metadata = BlankNoteMetadata,
                addedDate = dateFor("2020-01-01Z"),
                lastModifiedDate = dateFor("2020-02-01Z"),
                status = NoteStatus.ACTIVE,
                pinned = PinnedStatus.PINNED,
                reminder = Reminder(dateFor("2020-03-01Z"), Recurrence(Recurrence.Period.DAILY),
                    dateFor("2020-03-02Z"), 1, false)
            ), listOf(label1, label10)),
            NoteWithLabels(Note(id = 9,
                type = NoteType.LIST,
                title = "list",
                content = "item 1\nitem 2",
                metadata = ListNoteMetadata(listOf(false, true)),
                addedDate = dateFor("2019-01-01Z"),
                lastModifiedDate = dateFor("2019-02-01Z"),
                status = NoteStatus.ARCHIVED,
                pinned = PinnedStatus.CANT_PIN,
                reminder = null
            ), listOf(label1, label3)),
        ), notesDao.getAll().toSet())
    }

    @Test
    fun testEncryptedInvalidJsonImport() = runBlocking {
        if (shouldIgnoreEncryptionTest()) {
            return@runBlocking
        }

        val key = encryptionSetup()
        // wrong json structure
        @Language("JSON") val jsonData = """{
    "salt": "Wk7ITADlYDBTP/o8GtupJyWemvDClrDNxXKhoMBuTIp8QjlAlPdVAWfAi+B3GWTn/YmqgBP3OCK20Vmcm9hQbzwNfnXsnChnPu462ALv+WKf8y2NirINyWr5jG/tAOaE6bGNL+ZE4ClppTdBt82Gl87q6FX0pFqhJtmrE+8jLmI",
    "nonce":"BdnxU+ElFPIGlKJJ",
    "ciphertext":"j395X4746QzYPWEvE7nz2V0MNYzkIk+5HNrq9tsKrKI"
}
        """.trim().replace("\n", "")
        assertEquals(ImportResult.BAD_FORMAT, jsonManager.importJsonData(jsonData, importKey = key))
    }

    @Test
    fun testEncryptedJsonImportWithIncorrectKey() = runBlocking {
        if (shouldIgnoreEncryptionTest()) {
            return@runBlocking
        }

        @Language("JSON") val jsonDataWithoutKey = """{
    "salt": "Wk7ITADlYDBTP/o8GtupJyWemvDClrDNxXKhoMBuTIp8QjlAlPdVAWfAi+B3GWTn/YmqgBP3OCK20Vmcm9hQbzwNfnXsnChnPu462ALv+WKf8y2NirINyWr5jG/tAOaE6bGNL+ZE4ClppTdBt82Gl87q6FX0pFqhJtmrE+8jLmI",
    "nonce":"B+kFILwu8GLF1noQ",
    "ciphertext":"DwSQ5XQfkqTjhJf58JbrDZAMLhGQfnYWS4zWcrXwkkvQDAlwmE77vyatw1xem/iKqCk2oq24c1+tlOSXEiczT1JY12W6YiLN9o9v3xkYYhIrNuPTSMgtG2n8BoEpvop6Wa1ZJNlq323WnoDvlBzOUovmyzomCqrlQ8+d6xgpfBi2YPDtg+QRpUg0mCz9CBBDQuDTMdWg0UD+AucChEKwXaG0VRAKPtRFgf/qALeC4r8oPwsS3UnLsLELZGWauG8QUl7lGUzR0Ayqruk7PaEG4tOHuN3jcPBwOZHKwDZUti/Ybzn4ClKuBN2gFpTqsSYM8vhpBDz+iJ7fwLiuLAX6yvJLwiex4TACi/ZvcNTjxk2mhSaqwObbY09CbGvYgPb20N+PGdOxSxoz1LnFT6lH0XqUEgjDcIR9av8yNWx41sWNBn9Q3i43zrRe19ygzGCAgU/defKtF7pA27f905UOKY2182qqxtUIBd82Bicgd2wt3j6t7/3o9neZTawGg03pyGQt+u1S6OEt3rcmpjwEr+9PG8alCkqHQEBjqPlpSCU0aBDCV0J01Ck6MTE+qAXYEYjHdCgVpvoQ96MNGEJl3ngkhweZcF+j+Y30LG4SnGHeym18m9yzuYtOm3D6nJ0AMiM/0cB1Qkaes8Naxl/Uesusc41000Y17qNBqhbMy16KEERw3xwnzdKlfE4rSo6L/o8XLolPpHvECmQR3VPZX835O+dAilHtThqYpBMU4AktJBf1ywvH2wNoiDWYGsuTpgZ5C97kJfvk0TF7bAPQtG/KiJxDy9G/OMGsHk3D9m8x5yhvdyw5r2+N5nD/DqY0bVsyqg7+FFzD+mPk60v29PuoBautxkqyoHhkQlEIM+Z6/D6eNO6hz50+SkplJVT8f0bURMmRPcKdVaaX/1yH5ztiO3WZsyIuRpx9cCTjgX51bGuvs1gveEjkw4f/squCem/XD0GBxFoOQjIfeVs5PXsgqiSCCSMjwF1sHY71dfBbTNAiR2PsK47LlrPMsrTJBkKqZptHZy+R+u1sZ9TL/I1kvqP0yoncAPUvzcTANzPyWY1zZL3Qrv5kB/5GtNwDJ33oblHfz/MU6vE/lR5sdo1y/tU5WVLz1OC3VL15h37w0dbgcsFvSZD/pGcyCW4OVYHpIXivnO+VXZmDzjBPnap35PAa5ggRjNkgGtaU8g9NGy4xjq9bLKPvKPK+U8moIbCOQYxENEsUJBjWf/X/r+aXLFoddEVOc/DYLoBTb24O95pi5SxLLCbEcKTmfnYL4iKCsMIMaNXMPM16DBOif4BhyUH1oZZz/E4swyhe3J8vxhUPqwI2njQ"
}
        """.trim().replace("\n", "")
        assertEquals(ImportResult.KEY_MISSING_OR_INCORRECT, jsonManager.importJsonData(jsonDataWithoutKey))

        val key = SecretKeySpec(ByteArray(32), KeyProperties.KEY_ALGORITHM_AES)
        @Language("JSON") val jsonDataIncorrectKey = """{
    "salt": "Wk7ITADlYDBTP/o8GtupJyWemvDClrDNxXKhoMBuTIp8QjlAlPdVAWfAi+B3GWTn/YmqgBP3OCK20Vmcm9hQbzwNfnXsnChnPu462ALv+WKf8y2NirINyWr5jG/tAOaE6bGNL+ZE4ClppTdBt82Gl87q6FX0pFqhJtmrE+8jLmI",
    "nonce":"B+kFILwu8GLF1noQ",
    "ciphertext":"DwSQ5XQfkqTjhJf58JbrDZAMLhGQfnYWS4zWcrXwkkvQDAlwmE77vyatw1xem/iKqCk2oq24c1+tlOSXEiczT1JY12W6YiLN9o9v3xkYYhIrNuPTSMgtG2n8BoEpvop6Wa1ZJNlq323WnoDvlBzOUovmyzomCqrlQ8+d6xgpfBi2YPDtg+QRpUg0mCz9CBBDQuDTMdWg0UD+AucChEKwXaG0VRAKPtRFgf/qALeC4r8oPwsS3UnLsLELZGWauG8QUl7lGUzR0Ayqruk7PaEG4tOHuN3jcPBwOZHKwDZUti/Ybzn4ClKuBN2gFpTqsSYM8vhpBDz+iJ7fwLiuLAX6yvJLwiex4TACi/ZvcNTjxk2mhSaqwObbY09CbGvYgPb20N+PGdOxSxoz1LnFT6lH0XqUEgjDcIR9av8yNWx41sWNBn9Q3i43zrRe19ygzGCAgU/defKtF7pA27f905UOKY2182qqxtUIBd82Bicgd2wt3j6t7/3o9neZTawGg03pyGQt+u1S6OEt3rcmpjwEr+9PG8alCkqHQEBjqPlpSCU0aBDCV0J01Ck6MTE+qAXYEYjHdCgVpvoQ96MNGEJl3ngkhweZcF+j+Y30LG4SnGHeym18m9yzuYtOm3D6nJ0AMiM/0cB1Qkaes8Naxl/Uesusc41000Y17qNBqhbMy16KEERw3xwnzdKlfE4rSo6L/o8XLolPpHvECmQR3VPZX835O+dAilHtThqYpBMU4AktJBf1ywvH2wNoiDWYGsuTpgZ5C97kJfvk0TF7bAPQtG/KiJxDy9G/OMGsHk3D9m8x5yhvdyw5r2+N5nD/DqY0bVsyqg7+FFzD+mPk60v29PuoBautxkqyoHhkQlEIM+Z6/D6eNO6hz50+SkplJVT8f0bURMmRPcKdVaaX/1yH5ztiO3WZsyIuRpx9cCTjgX51bGuvs1gveEjkw4f/squCem/XD0GBxFoOQjIfeVs5PXsgqiSCCSMjwF1sHY71dfBbTNAiR2PsK47LlrPMsrTJBkKqZptHZy+R+u1sZ9TL/I1kvqP0yoncAPUvzcTANzPyWY1zZL3Qrv5kB/5GtNwDJ33oblHfz/MU6vE/lR5sdo1y/tU5WVLz1OC3VL15h37w0dbgcsFvSZD/pGcyCW4OVYHpIXivnO+VXZmDzjBPnap35PAa5ggRjNkgGtaU8g9NGy4xjq9bLKPvKPK+U8moIbCOQYxENEsUJBjWf/X/r+aXLFoddEVOc/DYLoBTb24O95pi5SxLLCbEcKTmfnYL4iKCsMIMaNXMPM16DBOif4BhyUH1oZZz/E4swyhe3J8vxhUPqwI2njQ"
}
        """.trim().replace("\n", "")
        assertEquals(ImportResult.KEY_MISSING_OR_INCORRECT,
            jsonManager.importJsonData(jsonDataIncorrectKey, importKey = key))
    }

    @Test
    fun testEncryptedJsonImportWithIncorrectNonce() = runBlocking {
        if (shouldIgnoreEncryptionTest()) {
            return@runBlocking
        }

        val key = encryptionSetup()
        @Language("JSON") val jsonDataWithoutNonce = """{
    "salt": "Wk7ITADlYDBTP/o8GtupJyWemvDClrDNxXKhoMBuTIp8QjlAlPdVAWfAi+B3GWTn/YmqgBP3OCK20Vmcm9hQbzwNfnXsnChnPu462ALv+WKf8y2NirINyWr5jG/tAOaE6bGNL+ZE4ClppTdBt82Gl87q6FX0pFqhJtmrE+8jLmI",
    "ciphertext":"DwSQ5XQfkqTjhJf58JbrDZAMLhGQfnYWS4zWcrXwkkvQDAlwmE77vyatw1xem/iKqCk2oq24c1+tlOSXEiczT1JY12W6YiLN9o9v3xkYYhIrNuPTSMgtG2n8BoEpvop6Wa1ZJNlq323WnoDvlBzOUovmyzomCqrlQ8+d6xgpfBi2YPDtg+QRpUg0mCz9CBBDQuDTMdWg0UD+AucChEKwXaG0VRAKPtRFgf/qALeC4r8oPwsS3UnLsLELZGWauG8QUl7lGUzR0Ayqruk7PaEG4tOHuN3jcPBwOZHKwDZUti/Ybzn4ClKuBN2gFpTqsSYM8vhpBDz+iJ7fwLiuLAX6yvJLwiex4TACi/ZvcNTjxk2mhSaqwObbY09CbGvYgPb20N+PGdOxSxoz1LnFT6lH0XqUEgjDcIR9av8yNWx41sWNBn9Q3i43zrRe19ygzGCAgU/defKtF7pA27f905UOKY2182qqxtUIBd82Bicgd2wt3j6t7/3o9neZTawGg03pyGQt+u1S6OEt3rcmpjwEr+9PG8alCkqHQEBjqPlpSCU0aBDCV0J01Ck6MTE+qAXYEYjHdCgVpvoQ96MNGEJl3ngkhweZcF+j+Y30LG4SnGHeym18m9yzuYtOm3D6nJ0AMiM/0cB1Qkaes8Naxl/Uesusc41000Y17qNBqhbMy16KEERw3xwnzdKlfE4rSo6L/o8XLolPpHvECmQR3VPZX835O+dAilHtThqYpBMU4AktJBf1ywvH2wNoiDWYGsuTpgZ5C97kJfvk0TF7bAPQtG/KiJxDy9G/OMGsHk3D9m8x5yhvdyw5r2+N5nD/DqY0bVsyqg7+FFzD+mPk60v29PuoBautxkqyoHhkQlEIM+Z6/D6eNO6hz50+SkplJVT8f0bURMmRPcKdVaaX/1yH5ztiO3WZsyIuRpx9cCTjgX51bGuvs1gveEjkw4f/squCem/XD0GBxFoOQjIfeVs5PXsgqiSCCSMjwF1sHY71dfBbTNAiR2PsK47LlrPMsrTJBkKqZptHZy+R+u1sZ9TL/I1kvqP0yoncAPUvzcTANzPyWY1zZL3Qrv5kB/5GtNwDJ33oblHfz/MU6vE/lR5sdo1y/tU5WVLz1OC3VL15h37w0dbgcsFvSZD/pGcyCW4OVYHpIXivnO+VXZmDzjBPnap35PAa5ggRjNkgGtaU8g9NGy4xjq9bLKPvKPK+U8moIbCOQYxENEsUJBjWf/X/r+aXLFoddEVOc/DYLoBTb24O95pi5SxLLCbEcKTmfnYL4iKCsMIMaNXMPM16DBOif4BhyUH1oZZz/E4swyhe3J8vxhUPqwI2njQ"
}
        """.trim().replace("\n", "")
        assertEquals(ImportResult.BAD_FORMAT, jsonManager.importJsonData(jsonDataWithoutNonce, importKey = key))

        @Language("JSON") val jsonDataIncorrectNonce = """{
    "salt": "Wk7ITADlYDBTP/o8GtupJyWemvDClrDNxXKhoMBuTIp8QjlAlPdVAWfAi+B3GWTn/YmqgBP3OCK20Vmcm9hQbzwNfnXsnChnPu462ALv+WKf8y2NirINyWr5jG/tAOaE6bGNL+ZE4ClppTdBt82Gl87q6FX0pFqhJtmrE+8jLmI",
    "nonce":"AAAAAAAAAAAAAAAA",
    "ciphertext":"DwSQ5XQfkqTjhJf58JbrDZAMLhGQfnYWS4zWcrXwkkvQDAlwmE77vyatw1xem/iKqCk2oq24c1+tlOSXEiczT1JY12W6YiLN9o9v3xkYYhIrNuPTSMgtG2n8BoEpvop6Wa1ZJNlq323WnoDvlBzOUovmyzomCqrlQ8+d6xgpfBi2YPDtg+QRpUg0mCz9CBBDQuDTMdWg0UD+AucChEKwXaG0VRAKPtRFgf/qALeC4r8oPwsS3UnLsLELZGWauG8QUl7lGUzR0Ayqruk7PaEG4tOHuN3jcPBwOZHKwDZUti/Ybzn4ClKuBN2gFpTqsSYM8vhpBDz+iJ7fwLiuLAX6yvJLwiex4TACi/ZvcNTjxk2mhSaqwObbY09CbGvYgPb20N+PGdOxSxoz1LnFT6lH0XqUEgjDcIR9av8yNWx41sWNBn9Q3i43zrRe19ygzGCAgU/defKtF7pA27f905UOKY2182qqxtUIBd82Bicgd2wt3j6t7/3o9neZTawGg03pyGQt+u1S6OEt3rcmpjwEr+9PG8alCkqHQEBjqPlpSCU0aBDCV0J01Ck6MTE+qAXYEYjHdCgVpvoQ96MNGEJl3ngkhweZcF+j+Y30LG4SnGHeym18m9yzuYtOm3D6nJ0AMiM/0cB1Qkaes8Naxl/Uesusc41000Y17qNBqhbMy16KEERw3xwnzdKlfE4rSo6L/o8XLolPpHvECmQR3VPZX835O+dAilHtThqYpBMU4AktJBf1ywvH2wNoiDWYGsuTpgZ5C97kJfvk0TF7bAPQtG/KiJxDy9G/OMGsHk3D9m8x5yhvdyw5r2+N5nD/DqY0bVsyqg7+FFzD+mPk60v29PuoBautxkqyoHhkQlEIM+Z6/D6eNO6hz50+SkplJVT8f0bURMmRPcKdVaaX/1yH5ztiO3WZsyIuRpx9cCTjgX51bGuvs1gveEjkw4f/squCem/XD0GBxFoOQjIfeVs5PXsgqiSCCSMjwF1sHY71dfBbTNAiR2PsK47LlrPMsrTJBkKqZptHZy+R+u1sZ9TL/I1kvqP0yoncAPUvzcTANzPyWY1zZL3Qrv5kB/5GtNwDJ33oblHfz/MU6vE/lR5sdo1y/tU5WVLz1OC3VL15h37w0dbgcsFvSZD/pGcyCW4OVYHpIXivnO+VXZmDzjBPnap35PAa5ggRjNkgGtaU8g9NGy4xjq9bLKPvKPK+U8moIbCOQYxENEsUJBjWf/X/r+aXLFoddEVOc/DYLoBTb24O95pi5SxLLCbEcKTmfnYL4iKCsMIMaNXMPM16DBOif4BhyUH1oZZz/E4swyhe3J8vxhUPqwI2njQ"
}
        """.trim().replace("\n", "")
        assertEquals(ImportResult.KEY_MISSING_OR_INCORRECT,
            jsonManager.importJsonData(jsonDataIncorrectNonce, importKey = key))
    }

    @Test

    fun testEncryptedJsonForwardCompatibility() = runBlocking {
        if (shouldIgnoreEncryptionTest()) {
            return@runBlocking
        }

        val key = encryptionSetup()
        // importing data with unsupported values for existing fields should fail
        @Language("JSON") val jsonData1 = """{
    "salt": "Wk7ITADlYDBTP/o8GtupJyWemvDClrDNxXKhoMBuTIp8QjlAlPdVAWfAi+B3GWTn/YmqgBP3OCK20Vmcm9hQbzwNfnXsnChnPu462ALv+WKf8y2NirINyWr5jG/tAOaE6bGNL+ZE4ClppTdBt82Gl87q6FX0pFqhJtmrE+8jLmI",
    "nonce":"dbIIGz5sG3qZO8/R",
    "ciphertext":"4xCyvX+WHiji0+hiGP59ykDMwVgNfYGgPRRkvg2OtFfCLhOka19A+5n5SpMNS9HSoDzbzQXORp4ijQY+R9krxkV94QvU1PcpB07mDZcslsBzLhuLTR7a0AvIO+xpCodMuq8cHDmWfGTyGZmPLlTEjAZ60tSwuHPRAPA4dM9GlwfDeL2PdXaxIsckdtipAlA8RK6jner+vbS8B3DkSY/g3v/Uo1IcHIl9cVCoCz7DKHJZfRTjlXeqVuu0ZwuD8W7ihOjzzbDABWgnTZOgk6p+1Jn5cG/O71TPNivq6cdNupugoOdyImrzUp3+HpLHxe+/Rb1puRJ1BBc7s/4nSPVM85c7nam52qyA"
}
        """.trim().replace("\n", "")
        assertEquals(ImportResult.BAD_DATA, jsonManager.importJsonData(jsonData1, importKey = key))
        @Language("JSON") val jsonData2 = """{
    "salt": "Wk7ITADlYDBTP/o8GtupJyWemvDClrDNxXKhoMBuTIp8QjlAlPdVAWfAi+B3GWTn/YmqgBP3OCK20Vmcm9hQbzwNfnXsnChnPu462ALv+WKf8y2NirINyWr5jG/tAOaE6bGNL+ZE4ClppTdBt82Gl87q6FX0pFqhJtmrE+8jLmI",
    "nonce":"Qm8INVqCd49zvCJG",
    "ciphertext":"PpUonn9kBwWS3WjAgtJdUlDy2All1N7lfRMYYw8MSDDUPYZAVzeu7rOH38j2Qz5IqWlWbam4RBKP+NihH12BHkI4iPKjSHjtb+XKSNJ0niSet+5SQBmqMhU74O0/YqkuR0aBHU7Rs6TRJYR9CthXtjTvnbCWAw2wOmriOvKRw9pN3VARXf7usw1UKf1/7BNoXU5zBLhjwLZMNvCCWbBotmGM06i/Gy6mg85kBXbLIhp6BAABwiM1uCow7ivgnid50ei1M5Z+13G92QV9jcsmlXAFO/OKrCc4MsIMGAp6G/e0+6jeuEE7W6i7DWsJmlYZomSd05K6t6Pzer3ViC6u3RHE7/Q"
}
        """.trim().replace("\n", "")
        assertEquals(ImportResult.FUTURE_VERSION, jsonManager.importJsonData(jsonData2, importKey = key))
    }
}

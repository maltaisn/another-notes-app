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

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.maltaisn.notes.testNote
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import kotlin.test.assertEquals
import kotlin.test.assertFalse

@RunWith(AndroidJUnit4::class)
class DefaultArchiveExporterTest {

    private lateinit var database: NotesDatabase
    private lateinit var notesDao: NotesDao

    private lateinit var archiveExporter: ArchiveExporter

    @Before
    fun createDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, NotesDatabase::class.java).build()
        notesDao = database.notesDao()
        archiveExporter = DefaultArchiveExporter(notesDao)
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    private suspend fun writeZip(): ByteArray {
        val output = ByteArrayOutputStream()
        archiveExporter.exportArchive(output, "untitled")
        output.close()
        return output.toByteArray()
    }

    private fun readZip(zip: ByteArray): Map<String, String> = ZipInputStream(ByteArrayInputStream(zip)).use {
        val notes = mutableMapOf<String, String>()
        var entry: ZipEntry? = it.nextEntry
        while (entry != null) {
            assertFalse(entry.isDirectory)
            val bytes = it.readBytes()
            notes[entry.name] = bytes.toString(Charsets.UTF_8)
            it.closeEntry()
            entry = it.nextEntry
        }
        return notes
    }

    private suspend fun exportAndReadback() = readZip(writeZip())

    @Test
    fun testExportArchive() = runBlocking {
        val note1 = testNote(title = "title 1", content = "content 1")
        val note2 = testNote(title = "title 2", content = "content 2")
        notesDao.insert(note1)
        notesDao.insert(note2)

        val notes = exportAndReadback()
        assertEquals(2, notes.size)
        assertEquals(note1.content, notes["title 1.txt"])
        assertEquals(note2.content, notes["title 2.txt"])
    }

    @Test
    fun testSanitizeFilenames() = runBlocking {
        val titles = listOf(
            " \t\u0000",
            "f?ck $:f",
            ".. . . ..",
            "① 送り仮名 助詞",
            "abc".repeat(100),
        )
        for (title in titles) {
            notesDao.insert(testNote(title = title))
        }

        val notes = exportAndReadback()
        assertEquals(setOf(
            "untitled.txt",
            "f_ck \$_f.txt",
            "untitled (1).txt",
            "1 送り仮名 助詞.txt",
            "${titles.last().take(DefaultArchiveExporter.MAX_NAME_LENGTH)}.txt",
        ), notes.keys)
    }

    @Test
    fun testDeduplicateFilenames() = runBlocking {
        val titles = listOf(
            "a",
            "a (1)",
            "a",
            "a (1)",
            "a (2)",
        )
        for (title in titles) {
            notesDao.insert(testNote(title = title))
        }

        val notes = exportAndReadback()
        assertEquals(setOf(
            "a.txt",
            "a (1).txt",
            "a (2).txt",
            "a (1) (1).txt",
            "a (2) (1).txt",
        ), notes.keys)
    }
}

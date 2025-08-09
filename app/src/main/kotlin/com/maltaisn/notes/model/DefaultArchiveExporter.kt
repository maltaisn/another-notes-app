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

import java.io.OutputStream
import java.text.Normalizer
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject

class DefaultArchiveExporter @Inject constructor(
    private val notesDao: NotesDao,
) : ArchiveExporter {

    private fun sanitizeFileName(name: String, untitledName: String): String {
        // Normalize to remove weird diacritics and non-standard Unicode
        return Normalizer.normalize(name, Normalizer.Form.NFKC)
            // Remove characters invalid in Windows and most filesystems
            .replace(INVALID_CHARS_REGEX, "_")
            // Also trim spaces and dots at ends (Windows issue)
            .trim { it.isWhitespace() || it == '.' || it == '\u0000' }
            // Limit length to avoid path length issues
            .take(MAX_NAME_LENGTH)
            .ifBlank { untitledName }
    }

    private fun getUniqueName(baseName: String, usedNames: MutableSet<String>): String {
        var name = baseName
        var count = 1
        while (name in usedNames) {
            name = "$baseName ($count)"
            count++
        }
        return name
    }

    override suspend fun exportArchive(output: OutputStream, untitledName: String) {
        val notes = notesDao.getAll()

        val zipOut = ZipOutputStream(output).apply {
            setLevel(9) // Max compression
        }

        val usedNames = mutableSetOf<String>()
        for (note in notes) {
            val baseName = sanitizeFileName(note.note.title, untitledName)
            val name = getUniqueName(baseName, usedNames)
            usedNames.add(name)

            val entry = ZipEntry("$name.txt")
            zipOut.putNextEntry(entry)
            zipOut.write(note.note.asText(includeTitle = false).toByteArray(Charsets.UTF_8))
            zipOut.closeEntry()
        }

        zipOut.close()
    }

    companion object {
        private val INVALID_CHARS_REGEX = """[\\/:*?"<>|]""".toRegex()

        const val MAX_NAME_LENGTH = 100
    }
}
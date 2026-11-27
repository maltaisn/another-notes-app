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

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Writes automatic exports to a folder chosen with the system folder picker.
 * Each export is saved to a new file named after the date and time of the export,
 * and only the [MAX_EXPORTS] most recent exports are kept.
 */
object AutoExportFolder {

    /** Number of automatic exports kept in the folder. Older ones are deleted. */
    const val MAX_EXPORTS = 7

    private const val MIME_TYPE = "application/json"

    // Only files matching this exact pattern are ever deleted, other files in the folder are left alone.
    private val EXPORT_NAME_REGEX = """^\d{4}-\d{2}-\d{2}_\d{2}-\d{2}-\d{2}( \(\d+\))?\.json$""".toRegex()

    /** Returns whether [uri] is a folder (tree) URI, as opposed to a single file URI from older versions. */
    fun isFolderUri(uri: Uri) = uri.pathSegments.firstOrNull() == "tree"

    fun exportName(time: Long): String =
        SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.ROOT).format(Date(time)) + ".json"

    /**
     * Write [data] to a new file in folder [treeUri], then delete old exports past [MAX_EXPORTS].
     * Throws an exception if the export file couldn't be written.
     */
    fun export(cr: ContentResolver, treeUri: Uri, data: String, time: Long = System.currentTimeMillis()) {
        val folderUri = DocumentsContract.buildDocumentUriUsingTree(treeUri,
            DocumentsContract.getTreeDocumentId(treeUri))
        val fileUri = checkNotNull(DocumentsContract.createDocument(cr, folderUri, MIME_TYPE, exportName(time))) {
            "Could not create export file"
        }
        checkNotNull(cr.openOutputStream(fileUri, "wt")) { "Could not open export file" }.use {
            it.write(data.toByteArray())
        }

        // Pruning failure shouldn't make the export count as failed.
        try {
            deleteOldExports(cr, treeUri)
        } catch (_: Exception) {
            // Ignore
        }
    }

    private fun deleteOldExports(cr: ContentResolver, treeUri: Uri) {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri,
            DocumentsContract.getTreeDocumentId(treeUri))
        val files = mutableMapOf<String, String>()  // name -> document ID
        cr.query(childrenUri, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                files[cursor.getString(1) ?: continue] = cursor.getString(0) ?: continue
            }
        }
        for (name in exportsToDelete(files.keys)) {
            DocumentsContract.deleteDocument(cr,
                DocumentsContract.buildDocumentUriUsingTree(treeUri, files.getValue(name)))
        }
    }

    /**
     * Returns the names among [names] that are automatic exports older than the [MAX_EXPORTS] most recent.
     * Names start with a sortable timestamp, so sorting them by name sorts them by date.
     */
    fun exportsToDelete(names: Collection<String>): List<String> =
        names.filter { EXPORT_NAME_REGEX.matches(it) }.sortedDescending().drop(MAX_EXPORTS)
}

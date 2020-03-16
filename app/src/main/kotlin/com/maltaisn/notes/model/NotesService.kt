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

@file:UseSerializers(DateTimeConverter::class, ChangeEventTypeConverter::class)

package com.maltaisn.notes.model

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import com.maltaisn.notes.model.converter.ChangeEventTypeConverter
import com.maltaisn.notes.model.converter.DateTimeConverter
import com.maltaisn.notes.model.entity.ChangeEventType
import com.maltaisn.notes.model.entity.Note
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.json.*
import java.io.IOException
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton


@Singleton
open class NotesService @Inject constructor(
        private val fbAuth: FirebaseAuth,
        private val fbFunctions: FirebaseFunctions) {

    private val json = Json(JsonConfiguration.Stable)

    /**
     * Send local data to sync with server, and return remote data to sync with local.
     * Throws [IOException] if sync fails for any reason.
     */
    suspend fun syncNotes(localData: SyncData): SyncData {
        if (fbAuth.currentUser == null) {
            // Not signed in, no sync
            throw IOException("User not authenticated")
        }

        // Send local changes to server, and receive remote changes
        val localDataJson = json.toJson(SyncData.serializer(), localData)
        val result = callSyncFunction(jsonElementToStructure(localDataJson))

        val remoteDataJson = structureToJsonElement(result)
        return json.fromJson(SyncData.serializer(), remoteDataJson)
    }

    open suspend fun callSyncFunction(data: Any?): Any? =
            fbFunctions.getHttpsCallable("sync")
                    .call(data)
                    .await()?.data
                    ?: throw IOException("Sync failed")

    @Serializable
    data class ChangeEventData(val uuid: String,
                               val note: Note?,
                               val type: ChangeEventType)

    @Serializable
    data class SyncData(val lastSync: Date,
                        val events: List<ChangeEventData>)


    private fun jsonElementToStructure(element: JsonElement?): Any? = when (element) {
        is JsonObject -> element.mapValues { (_, value) -> jsonElementToStructure(value) }
        is JsonArray -> element.map { jsonElementToStructure(it) }
        is JsonLiteral -> element.body
        null, JsonNull -> null
    }

    @Suppress("UNCHECKED_CAST")
    private fun structureToJsonElement(obj: Any?): JsonElement = when (obj) {
        is Map<*, *> -> JsonObject((obj as Map<String, Any>).mapValues { (_, value) -> structureToJsonElement(value) })
        is List<*> -> JsonArray(obj.map { structureToJsonElement(it) })
        is Number -> JsonLiteral(obj)
        is Boolean -> JsonLiteral(obj)
        is String -> JsonLiteral(obj)
        null -> JsonNull
        else -> error("Invalid object")
    }

}

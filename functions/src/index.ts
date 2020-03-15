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

import * as functions from 'firebase-functions'
import * as admin from 'firebase-admin'
import {HttpsError} from 'firebase-functions/lib/providers/https'
import {
    ActiveNote,
    ChangeEvent,
    ChangeEventType,
    decodeOrElse,
    NoteStatus,
    SyncData,
    TActiveNote,
    TChangeEvent,
    TNote,
    TSyncData
} from './types'
import {base64DecodeNote, base64EncodeNote} from './encoding'


admin.initializeApp({
    credential: admin.credential.applicationDefault(),
    databaseURL: 'https://notes-ffde4.firebaseio.com'
})

/**
 * Called by client to sync local notes with remote notes. Client sends {@link SyncData},
 * a list of local change events since last sync, and server returns a list of remote
 * change events since last sync date contained in passed data.
 * User must be authenticated.
 */
export const sync = functions.https.onCall(async (data, context) => {
    if (!context.auth) {
        throw new HttpsError('unauthenticated', 'Authentication required')
    }
    const userUid = context.auth.uid

    // Decode and validate sync data.
    const syncData = decodeOrElse(TSyncData, data, () => {
        throw new HttpsError('invalid-argument', 'Invalid sync data')
    })

    const remoteChanges = await syncRemoteChangeEvents(userUid, syncData)
    await syncLocalChangeEvents(userUid, syncData)

    // Encode and return remote change events.
    // Object is also cleaned to remove null and undefined values.
    return remoteChanges.map((changeEvent) =>
        cleanObject(TChangeEvent.encode(changeEvent)))
})

/**
 * Get all notes modified after last sync, excluding new notes contained in syncData,
 * since client already has those.
 */
async function syncRemoteChangeEvents(userUid: string, syncData: SyncData): Promise<ChangeEvent[]> {
    const remoteChanges: ChangeEvent[] = []
    const localChangedUuids = new Set(syncData.events.map((event: ChangeEvent) => event.uuid))
    try {
        const snapshot = await admin.database()
            .ref(`/users/${userUid}/notes`)
            .orderByChild('modified')
            .startAt(syncData.lastSync.toISOString())
            .once('value')
        snapshot.forEach((childSnapshot) => {
            const note = decodeOrElse(TNote, childSnapshot.val(), () => {
                throw new HttpsError('internal', 'Invalid server note data')
            })
            if (!localChangedUuids.has(note.uuid)) {
                // Add change event, either adding or deleting a note.
                // Server never sends changes with UPDATED type since it has no way
                // of knowing whether client has a note or not.
                if (note.status === NoteStatus.Deleted) {
                    remoteChanges.push({
                        uuid: note.uuid,
                        note: null,
                        type: ChangeEventType.Deleted
                    })
                } else {
                    // Decode note first
                    const decoded = base64DecodeNote(note)
                    remoteChanges.push({
                        uuid: note.uuid,
                        note: decoded,
                        type: ChangeEventType.Added
                    })
                }
            }
        })
    } catch (error) {
        console.log('Could not get notes from server', error.message)
        throw new HttpsError('internal', 'Could not get notes')
    }
    return remoteChanges
}

/**
 * Update remote notes from a list of local change events contained in syncData.
 */
async function syncLocalChangeEvents(userUid: string, syncData: SyncData) {
    try {
        const snapshot = admin.database().ref(`/users/${userUid}/notes`)
        for (const changeEvent of syncData.events) {
            const noteSnapshot = snapshot.child(changeEvent.uuid)
            if (changeEvent.type === ChangeEventType.Deleted) {
                // Note was removed locally, remove on server.
                await noteSnapshot.remove()

            } else {
                // Note was added or updated locally, update on server.
                // Note must be cleaned because firebase doesn't take undefined values.
                const encoded = base64EncodeNote(changeEvent.note!) as ActiveNote
                const obj = TActiveNote.encode(encoded)
                cleanObject(obj)
                await noteSnapshot.set(obj)
            }
        }

    } catch (error) {
        console.log('Could not update notes in database', error.message)
        throw new HttpsError('internal', 'Could not set notes')
    }
}

function cleanObject(obj: { [s: string]: any }): { [s: string]: any } {
    for (const [key, value] of Object.entries(obj)) {
        if (value === null || value === undefined) {
            delete obj[key]
        } else if (typeof obj === 'object') {
            cleanObject(value)
        }
    }
    return obj
}

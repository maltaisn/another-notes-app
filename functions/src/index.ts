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
import {decodeOrElse, Note, SyncData, TDateString, TNote, TSyncData} from './types'
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
 *
 * Note that while notes are automatically deleted from trash on client,
 * the server keeps them until client sends the deletion event.
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
    const syncTime = new Date()

    // Sync data
    const remoteSyncData = await syncRemoteChangeEvents(userUid, syncData, syncTime)
    await syncLocalChangeEvents(userUid, syncData, syncTime)

    // Encode and return remote sync data.
    return TSyncData.encode(remoteSyncData)
})

/**
 * Get all notes modified after last sync, excluding new notes contained in syncData,
 * since client already has those.
 */
async function syncRemoteChangeEvents(userUid: string, syncData: SyncData,
                                      syncTime: Date): Promise<SyncData> {
    // Create a set of all locally changed or deleted notes UUID from sync data.
    // Notes with these UUID aren't sent back to client since client overwrites them.
    const localChangedUuids = new Set()
    syncData.changedNotes.forEach(note => localChangedUuids.add(note.uuid))
    syncData.deletedUuids.forEach(uuid => localChangedUuids.add(uuid))

    const changedNotes: Note[] = []
    const deletedUuids: string[] = []
    try {
        // Date to sync notes from. + 1 is to avoid getting notes from last sync.
        const startDate = new Date(syncData.lastSync.getTime() + 1).toISOString()

        // Get remote changes
        const notesSnapshot = await admin.database()
            .ref(`/users/${userUid}/notes`)
            .orderByChild('synced')
            .startAt(startDate)
            .once('value')
        notesSnapshot.forEach((childSnapshot) => {
            const note = decodeOrElse(TNote, childSnapshot.val(), () => {
                throw new HttpsError('internal', 'Invalid server note data')
            })
            delete note.synced

            if (!localChangedUuids.has(note.uuid)) {
                // Add decoded note to list.
                const decoded = base64DecodeNote(note)
                changedNotes.push(decoded)
            }
        })

        // Get remote deletions
        const deletedNotesSnapshot = await admin.database()
            .ref(`/users/${userUid}/deletedNotes`)
            .orderByValue()
            .startAt(startDate)
            .once('value')
        deletedNotesSnapshot.forEach((childSnapshot) => {
            const uuid = childSnapshot.key!
            if (!localChangedUuids.has(uuid)) {
                // Add deleted note UUID to list.
                deletedUuids.push(uuid)
            }
        })

    } catch (error) {
        console.log('Could not get notes from server', error.message)
        throw new HttpsError('internal', 'Could not get notes')
    }

    return {
        lastSync: syncTime,
        changedNotes: changedNotes,
        deletedUuids: deletedUuids
    }
}

/**
 * Update remote notes from a list of local change events contained in syncData.
 */
async function syncLocalChangeEvents(userUid: string, syncData: SyncData, syncTime: Date) {
    try {
        // Add local changes
        const notesSnapshot = admin.database().ref(`/users/${userUid}/notes`)
        for (const note of syncData.changedNotes) {
            const encoded = base64EncodeNote(note)
            encoded.synced = syncTime
            const obj = TNote.encode(encoded)
            await notesSnapshot.child(note.uuid).set(obj)
        }

        // Add local deletions
        const deletedNotesSnapshot = admin.database().ref(`/users/${userUid}/deletedNotes`)
        for (const uuid of syncData.deletedUuids) {
            await notesSnapshot.child(uuid).remove()
            await deletedNotesSnapshot.child(uuid).set(TDateString.encode(syncTime))
        }

    } catch (error) {
        console.log('Could not update notes in database', error.message)
        throw new HttpsError('internal', 'Could not set notes')
    }
}

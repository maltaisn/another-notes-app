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

"use strict";

const functions = require("firebase-functions");
const admin = require("firebase-admin");

admin.initializeApp({
    credential: admin.credential.applicationDefault(),
    databaseURL: "https://notes-ffde4.firebaseio.com"
});

const HttpsError = functions.https.HttpsError;

/**
 * Get all notes after a date and greater than an ID.
 */
exports.sync = functions.https.onCall(async (data, context) => {
    if (!context.auth) {
        throw new HttpsError('unauthenticated', "Authentication required");
    }

    const date = data.query.date;
    const id = data.query.id;

    // Validate date format.
    if (!date.match(/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}Z$/)) {
        console.log(`Invalid date format ${date}.`);
        throw new HttpsError('invalid-argument', "Invalid date format");

    } else if (isNaN(Date.parse(date))) {
        console.log(`Invalid date value ${date}.`);
        throw new HttpsError('invalid-argument', "Invalid date value");
    }

    // Validate ID.
    if (isNaN(id) || id <= 0) {
        console.log(`Invalid note ID ${id}.`);
        throw new HttpsError('invalid-argument', "Invalid note ID");
    }

    // Query user notes after the specified date.
    // Only one child key can be queried, so ID will be filtered later.
    let query = admin.database().ref(`/users/${context.auth.uid}/notes`)
        .orderByChild("modified")
        .startAt(date);

    // Send result
    try {
        const snapshot = await query.once("value");
        let notes = [];

        // Add all notes to the result.
        // If date is exactly equal to specified date, compare ID.
        snapshot.forEach((childSnapshot) => {
            const note = childSnapshot.val();
            if (note.modified > date || note.id > id) {
                notes.push(note);
            }
        });

        // Sort result by ID.
        notes.sort((a, b) => a.id - b.id);

        return notes;

    } catch (error) {
        console.log("Could not get notes", error.message);
        throw new HttpsError('internal', "Could not get notes");
    }
});

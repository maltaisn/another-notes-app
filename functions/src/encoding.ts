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


import {ActiveNote, Note} from './types'

// The server encodes all note sensitive data (title, content, metadata) to base 64
// as to prevent accicental eavesdropping but this is by no mean a security measure.

export function base64EncodeNote(note: Note): Note {
    if (!(note as ActiveNote).title) {
        return note
    }
    const copy = Object.assign({}, note) as ActiveNote
    copy.title = Buffer.from(copy.title).toString('base64')
    copy.content = Buffer.from(copy.content).toString('base64')
    if (copy.metadata) {
        copy.metadata = Buffer.from(copy.metadata).toString('base64')
    }
    return copy
}

export function base64DecodeNote(note: Note): Note {
    if (!(note as ActiveNote).title) {
        return note
    }
    const copy = Object.assign({}, note) as ActiveNote
    copy.title = Buffer.from(copy.title, 'base64').toString()
    copy.content = Buffer.from(copy.content, 'base64').toString()
    if (copy.metadata) {
        copy.metadata = Buffer.from(copy.metadata, 'base64').toString()
    }
    return copy
}

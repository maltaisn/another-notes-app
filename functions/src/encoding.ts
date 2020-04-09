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


import {Note} from './types'

// The server encodes all note sensitive data (title, content, metadata) to base 64
// as to prevent accidental eavesdropping. This is a privacy measure, not a security one.

const ENCODING = 'base64'

export function base64EncodeNote(note: Note): Note {
    const copy = Object.assign({}, note)
    copy.title = Buffer.from(copy.title).toString(ENCODING)
    copy.content = Buffer.from(copy.content).toString(ENCODING)
    copy.metadata = Buffer.from(copy.metadata).toString(ENCODING)
    return copy
}

export function base64DecodeNote<T>(note: Note): Note {
    const copy = Object.assign({}, note)
    copy.title = Buffer.from(copy.title, ENCODING).toString()
    copy.content = Buffer.from(copy.content, ENCODING).toString()
    copy.metadata = Buffer.from(copy.metadata, ENCODING).toString()
    return copy
}

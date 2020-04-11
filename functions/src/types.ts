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

import * as t from 'io-ts'
import {either, fold} from 'fp-ts/lib/Either'
import {pipe} from 'fp-ts/lib/pipeable'
import {PathReporter} from 'io-ts/lib/PathReporter'

class EnumType<A> extends t.Type<A> {
    public readonly _tag = 'EnumType'

    constructor(name: string, is: EnumType<A>['is'],
                validate: EnumType<A>['validate'],
                encode: EnumType<A>['encode']) {
        super(name, is, validate, encode)
    }
}

/**
 * Create a type for an enum. This allows number values in JSON to be
 * decoded correctly to an enum type, and encoded back to a number.
 */
const enumType = <A>(e: any, name: string): EnumType<A> => {
    const is = (u: unknown): u is A => Object.keys(e).some(k => e[k] === u)
    return new EnumType<A>(name, is, (u, c) => (is(u)
        ? t.success(u) : t.failure(u, c)), t.identity)
}

/**
 * Type to encode and decode date strings in ISO format to date objects.
 */
export const TDateString = new t.Type<Date, string, unknown>(
    'DateString',
    (u): u is Date => u instanceof Date,
    (u, c) =>
        either.chain(t.string.validate(u, c), s => {
            if (!s.match(/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{3}Z$/)) {
                return t.failure(u, c, 'Invalid date format')
            }
            const d = new Date(s)
            return isNaN(d.getTime()) ? t.failure(u, c, 'Invalid date value') : t.success(d)
        }),
    a => {
        return a.toISOString()
    }
)
export type DateString = t.TypeOf<typeof TDateString>

/**
 * Status of a note, i.e. its location.
 */
export enum NoteStatus { Active = 0, Archived = 1, Trashed = 2, Deleted = 3 }

export const TNoteStatus = enumType(NoteStatus, 'NoteStatus')

/**
 * Note type, defines the content of the "metadata" field.
 */
enum NoteType { Text = 0, List = 1 }

export const TNoteType = enumType(NoteType, 'NoteType')

/**
 * Base class for a note. This is used to represent deleted notes,
 * which are kept for syncing but have no content.
 */
export const TNote = t.type({
    uuid: t.string,
    type: t.number,
    title: t.string,
    content: t.string,
    metadata: t.string,
    added: TDateString,
    modified: TDateString,
    status: t.number,

    // This field is used to determine whether note data should be sent to the client or not,
    // depending on the "lastSync" date value passed. "modified" field wasn't sufficient for this
    // purpose since it depends on user's local time which isn't always reliable.
    synced: t.union([TDateString, t.undefined])
})
export type Note = t.TypeOf<typeof TNote>

/**
 * Sync data sent by the client and the server, containing a date of last sync
 * and a list of events that happened since that time.
 */
export const TSyncData = t.type({
    lastSync: TDateString,
    changedNotes: t.union([t.array(TNote), t.undefined]),
    deletedUuids: t.union([t.array(t.string), t.undefined])
})
export type SyncData = t.TypeOf<typeof TSyncData>


/**
 * Try to decode a value of a given type, calling onError if decoding fails.
 */
export const decodeOrElse = function <A, O, I>(type: t.Type<A, O, I>, value: I,
                                               onError: (e: string) => A): A {
    const r = type.decode(value)
    return pipe(r, fold(() => onError(PathReporter.report(r).join(', ')), t.identity))
}

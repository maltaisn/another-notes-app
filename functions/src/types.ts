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
    a => a.toISOString()
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
 * Type for a change event.
 */
export enum ChangeEventType { Added = 0, Updated = 1, Deleted = 2 }

export const TChangeEventType = enumType(ChangeEventType, 'ChangeEventType')

/**
 * Base class for a note. This is used to represent deleted notes,
 * which are kept for syncing but have no content.
 */
export const TNote = t.type({
    uuid: t.string,
    modified: TDateString,
    status: t.number
})
export type Note = t.TypeOf<typeof TNote>

/**
 * An active note i.e. not deleted, with content.
 */
export const TActiveNote = t.intersection([
    TNote,
    t.type({
        type: t.number,
        title: t.string,
        content: t.string,
        metadata: t.union([t.string, t.undefined]),
        added: TDateString
    })
])
export type ActiveNote = t.TypeOf<typeof TActiveNote>

/**
 * A change event describing a change in notes that happened locally or remotely.
 */
export const TChangeEvent = t.type({
    uuid: t.string,
    note: t.union([TActiveNote, TNote, t.null, t.undefined]),
    type: TChangeEventType
})
export type ChangeEvent = t.TypeOf<typeof TChangeEvent>

/**
 * Sync data sent by the client and the server, containing a date of last sync
 * and a list of events that happened since that time.
 */
export const TSyncData = t.type({
    lastSync: TDateString,
    events: t.array(TChangeEvent)
})
export type SyncData = t.TypeOf<typeof TSyncData>


export const decodeOrElse = function <A, O, I>(type: t.Type<A, O, I>, value: I,
                                               onError: (e: t.Errors) => A): A {
    return pipe(type.decode(value), fold(onError, t.identity))
}

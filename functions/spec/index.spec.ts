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

import * as functions from '../src/index'
import * as chai from 'chai'
import * as chaiAsPromised from 'chai-as-promised'
import * as admin from 'firebase-admin'
import * as config from '../config.json'
import * as testData from './test-data.json'
import {ActiveNote, ChangeEvent, ChangeEventType, Note} from '../src/types'
import {base64DecodeNote, base64EncodeNote} from '../src/encoding'

chai.use(chaiAsPromised)
chai.config.truncateThreshold = 0

const expect = chai.expect

const test = require('firebase-functions-test')(config.test.config)

const callableContext = {
    auth: {
        uid: config.test.userUid
    }
}

describe('cloud functions', () => {
    const userDb = admin.database().ref(`users/${config.test.userUid}/notes`)

    before(() => {
        chai.should()
    })

    beforeEach(async () => {
        // Delete test user notes on server
        await userDb.remove()
    })

    after(async () => {
        await userDb.remove()
        test.cleanup()
    })

    describe('sync notes', () => {
        it('should fail not authenticated', async () => {
            return expect(functions.sync.run({}, {}))
                .to.eventually.be.rejectedWith('Authentication required')
        })

        describe('invalid sync data', () => {
            it('should fail no sync data', async () => {
                return expect(functions.sync.run({}, callableContext))
                    .to.eventually.be.rejectedWith('Invalid sync data')
            })

            it('should fail wrong last sync date', async () => {
                return expect(functions.sync.run({
                    lastSync: '2020/01/01 10:10:10.100 GMT',
                    events: []
                }, callableContext)).to.eventually.be.rejectedWith('Invalid sync data')
            })

            it('should fail wrong event', async () => {
                return expect(functions.sync.run({
                    lastSync: '1970-01-01T00:00:00.000Z',
                    events: [{
                        uuid: '0',
                        note: {wrong: 'yes'},
                        type: 100
                    }]
                }, callableContext)).to.eventually.be.rejectedWith('Invalid sync data')
            })
        })

        describe('return remote events', async () => {
            it('should return no events (no remote events)', async () => {
                return expect(functions.sync.run({
                    lastSync: '1970-01-01T00:00:00.000Z',
                    events: []
                }, callableContext)).to.eventually.deep.equal([])
            })

            it('should return no events (already synced)', async () => {
                await setTestNote(testData.notes.test)
                return expect(functions.sync.run({
                    lastSync: '2030-01-01T00:00:00.000Z',
                    events: []
                }, callableContext)).to.eventually.deep.equal([])
            })

            it('should return single add event', async () => {
                await setTestNote(testData.notes.test)
                return expect(functions.sync.run({
                    lastSync: '2010-01-01T00:00:00.000Z',
                    events: []
                }, callableContext)).to.eventually.deep.equal(
                    [createTestEvent(testData.notes.test, ChangeEventType.Added)])
            })

            it('should return single delete event', async () => {
                await setTestNote(testData.notes.testDeleted)
                return expect(functions.sync.run({
                    lastSync: '2010-01-01T00:00:00.000Z',
                    events: []
                }, callableContext)).to.eventually.deep.equal([{
                    uuid: '0',
                    type: ChangeEventType.Deleted
                }])
            })
        })

        describe('send local events', async () => {
            it('should add no events', async () => {
                await functions.sync.run({
                    lastSync: '1970-01-01T00:00:00.000Z',
                    events: []
                }, callableContext)
                const snapshot = await userDb.once('value')
                expect(snapshot.numChildren()).to.be.equal(0)
            })

            it('should add event', async () => {
                await functions.sync.run({
                    lastSync: '1970-01-01T00:00:00.000Z',
                    events: [createTestEvent(testData.notes.test, ChangeEventType.Added)]
                }, callableContext)
                return expect(getTestNote('0'))
                    .to.be.eventually.deep.equal(testData.notes.test)
            })

            it('should update event', async () => {
                await setTestNote(testData.notes.test)
                await functions.sync.run({
                    lastSync: '1970-01-01T00:00:00.000Z',
                    events: [createTestEvent(testData.notes.testUpdated, ChangeEventType.Updated)]
                }, callableContext)
                return expect(getTestNote('0'))
                    .to.be.eventually.deep.equal(testData.notes.testUpdated)
            })

            it('should delete event', async () => {
                await setTestNote(testData.notes.test)
                await functions.sync.run({
                    lastSync: '1970-01-01T00:00:00.000Z',
                    events: [createTestEvent(testData.notes.testDeleted, ChangeEventType.Deleted)]
                }, callableContext)
                const snapshot = await userDb.once('value')
                expect(snapshot.numChildren()).to.be.equal(0)
            })
        })

        describe('send local event and return remote', async () => {
            it('should update but not return event', async () => {
                await setTestNote(testData.notes.test)
                await expect(functions.sync.run({
                    lastSync: '1970-01-01T00:00:00.000Z',
                    events: [createTestEvent(testData.notes.testUpdated, ChangeEventType.Updated)]
                }, callableContext)).to.be.eventually.eql([])
                return expect(getTestNote('0'))
                    .to.be.eventually.deep.equal(testData.notes.testUpdated)
            })

            it('should update and return event', async () => {
                await setTestNote(testData.notes.test)
                await expect(functions.sync.run({
                    lastSync: '1970-01-01T00:00:00.000Z',
                    events: [createTestEvent(testData.notes.testList, ChangeEventType.Added)]
                }, callableContext)).to.be.eventually.deep.equal(
                    [createTestEvent(testData.notes.test, ChangeEventType.Added)])
                return expect(getTestNote('1'))
                    .to.be.eventually.deep.equal(testData.notes.testList)
            })
        })

        describe('user data', async () => {
            it('should be base64 encoded and decoded', async () => {
                const testNote = testData.notes.testList
                await functions.sync.run({
                    lastSync: '1970-01-01T00:00:00.000Z',
                    events: [createTestEvent(testNote, ChangeEventType.Added)]
                }, callableContext)
                const note = await getTestNote(testNote.uuid, false) as ActiveNote
                expect(note.title).to.be.equal(Buffer.from(testNote.title).toString('base64'))
                expect(note.content).to.be.equal(Buffer.from(testNote.content).toString('base64'))
                expect(note.metadata).to.be.equal(Buffer.from(testNote.metadata).toString('base64'))
            })
        })
    })

    function createTestEvent(note: any, type: ChangeEventType): ChangeEvent {
        return {
            uuid: note.uuid,
            note: type === ChangeEventType.Deleted ? null : note,
            type: type
        }
    }

    async function getTestNote(uuid: string, decode = true): Promise<Note> {
        const val = await userDb.child(uuid).once('value')
        if (decode) {
            return base64DecodeNote(val.val())
        }
        return val.val()
    }

    async function setTestNote(note: any): Promise<void> {
        const encoded = base64EncodeNote(note)
        return userDb.child(encoded.uuid).set(encoded)
    }
})

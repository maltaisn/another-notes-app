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
import {Note} from '../src/types'
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
    const userDb = admin.database().ref(`users/${config.test.userUid}`)
    const notesDb = userDb.child('notes')
    const deletedNotesDb = userDb.child('deletedNotes')

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
        // Note: for testing purposes, single character "UUID" are being used.
        // In practice, real UUID should be used.

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
                    lastSync: '2020/01/01 10:10:10.100 GMT'
                }, callableContext)).to.eventually.be.rejectedWith('Invalid sync data')
            })

            it('should fail wrong changed note', async () => {
                return expect(functions.sync.run({
                    lastSync: '1970-01-01T00:00:00.000Z',
                    changedNotes: [{
                        uuid: '0',
                        type: 100
                    }]
                }, callableContext)).to.eventually.be.rejectedWith('Invalid sync data')
            })

            it('should fail wrong deleted uuid', async () => {
                return expect(functions.sync.run({
                    lastSync: '1970-01-01T00:00:00.000Z',
                    deletedUuids: [1234]
                }, callableContext)).to.eventually.be.rejectedWith('Invalid sync data')
            })
        })

        describe('return remote events', async () => {
            it('should return new sync date', async () => {
                const dateStr = (await functions.sync.run({
                    lastSync: '1970-01-01T00:00:00.000Z'
                }, callableContext)).lastSync
                expect(new Date(dateStr).getTime())
                    .to.approximately(new Date().getTime(), 1000)
            })

            it('should return no events (no remote events)', async () => {
                const syncData = await functions.sync.run({
                    lastSync: '1970-01-01T00:00:00.000Z'
                }, callableContext)
                expect(syncData.changedNotes).to.be.empty
                expect(syncData.deletedUuids).to.be.empty
            })

            it('should return no events (already synced)', async () => {
                await setTestNote(testData.notes.test);
                const syncData = await functions.sync.run({
                    lastSync: '2030-01-01T00:00:00.000Z'
                }, callableContext)
                expect(syncData.changedNotes).to.be.empty
                expect(syncData.deletedUuids).to.be.empty
            })

            it('should return single updated', async () => {
                await setTestNote(testData.notes.test)
                const syncData = await functions.sync.run({
                    lastSync: '2010-01-01T00:00:00.000Z'
                }, callableContext)
                expectNotesToBeEqual(syncData.changedNotes[0], testData.notes.test)
                expect(syncData.deletedUuids).to.be.empty
            })

            it('should return single deleted', async () => {
                await deletedNotesDb.child('0').set('2020-01-01T00:00:00.000Z')
                const syncData = await functions.sync.run({
                    lastSync: '2010-01-01T00:00:00.000Z'
                }, callableContext)
                expect(syncData.changedNotes).to.be.empty
                expect(syncData.deletedUuids).to.deep.equal(['0'])
            })
        })

        describe('send local events', async () => {
            it('should add no events', async () => {
                await functions.sync.run({
                    lastSync: '1970-01-01T00:00:00.000Z'
                }, callableContext)
                const snapshot = await userDb.once('value')
                expect(snapshot.numChildren()).to.be.equal(0)
            })

            it('should add note', async () => {
                await functions.sync.run({
                    lastSync: '1970-01-01T00:00:00.000Z',
                    changedNotes: [testData.notes.test]
                }, callableContext)
                expectNotesToBeEqual(await getTestNote('0'), testData.notes.test)
            })

            it('should update note', async () => {
                await setTestNote(testData.notes.test)
                await functions.sync.run({
                    lastSync: '1970-01-01T00:00:00.000Z',
                    changedNotes: [testData.notes.testUpdated]
                }, callableContext)
                expectNotesToBeEqual(await getTestNote('0'), testData.notes.testUpdated)
            })

            it('should delete note', async () => {
                await setTestNote(testData.notes.test)
                await functions.sync.run({
                    lastSync: '1970-01-01T00:00:00.000Z',
                    deletedUuids: ['0']
                }, callableContext)
                const notesSnapshot = await notesDb.once('value')
                expect(notesSnapshot.numChildren()).to.be.equal(0)
                const deletedNotesSnapshot = await deletedNotesDb.once('value')
                expect(deletedNotesSnapshot.numChildren()).to.be.equal(1)
            })
        })

        describe('send local event and return remote', async () => {
            it('should update but not return event (same uuid)', async () => {
                await setTestNote(testData.notes.test)
                const syncData = await functions.sync.run({
                    lastSync: '1970-01-01T00:00:00.000Z',
                    changedNotes: [testData.notes.testUpdated]
                }, callableContext)
                expect(syncData.changedNotes).to.be.empty
                expect(syncData.deletedUuids).to.be.empty
                expectNotesToBeEqual(await getTestNote('0'), testData.notes.testUpdated)
            })

            it('should delete but not return event (same uuid)', async () => {
                await setTestNote(testData.notes.test)
                const syncData = await functions.sync.run({
                    lastSync: '1970-01-01T00:00:00.000Z',
                    deletedUuids: ['0']
                }, callableContext)
                expect(syncData.changedNotes).to.be.empty
                expect(syncData.deletedUuids).to.be.empty
                const notesSnapshot = await notesDb.once('value')
                expect(notesSnapshot.numChildren()).to.be.equal(0)
                const deletedNotesSnapshot = await deletedNotesDb.once('value')
                expect(deletedNotesSnapshot.numChildren()).to.be.equal(1)
            })

            it('should update and return event', async () => {
                await setTestNote(testData.notes.test)
                const syncData = await functions.sync.run({
                    lastSync: '1970-01-01T00:00:00.000Z',
                    changedNotes: [testData.notes.testList]
                }, callableContext)
                expectNotesToBeEqual(syncData.changedNotes[0], testData.notes.test)
                expect(syncData.deletedUuids).to.be.empty
                expectNotesToBeEqual(await getTestNote('1'), testData.notes.testList)
            })
        })

        describe('user data', async () => {
            it('should be base64 encoded and decoded', async () => {
                const testNote = testData.notes.testList
                await functions.sync.run({
                    lastSync: '1970-01-01T00:00:00.000Z',
                    changedNotes: [testNote]
                }, callableContext)
                const note = await getTestNote(testNote.uuid, false)
                expect(note.title).to.be.equal(Buffer.from(testNote.title).toString('base64'))
                expect(note.content).to.be.equal(Buffer.from(testNote.content).toString('base64'))
                expect(note.metadata).to.be.equal(Buffer.from(testNote.metadata).toString('base64'))
            })
        })
    })

    async function getTestNote(uuid: string, decode = true): Promise<Note> {
        const val = await notesDb.child(uuid).once('value')
        if (decode) {
            return base64DecodeNote(val.val())
        }
        return val.val()
    }

    async function setTestNote(note: any): Promise<void> {
        const encoded = base64EncodeNote(note)
        return notesDb.child(encoded.uuid).set(encoded)
    }

    /**
     * Expect notes to be equal ignoring the 'synced' field.
     */
    function expectNotesToBeEqual(actual: any, expected: any) {
        // Set the synced date on a copy of the expected note.
        const expectedNoSynced = Object.assign({}, expected)
        const actualNoSynced = Object.assign({}, actual)
        delete expectedNoSynced.synced
        delete actualNoSynced.synced
        expect(actualNoSynced).to.be.deep.equal(expectedNoSynced)
    }

})

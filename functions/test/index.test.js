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

const fs = require("fs");

const configJson = fs.readFileSync("../config.json");
const config = JSON.parse(configJson);

const test = require("firebase-functions-test")({
    databaseURL: "https://notes-ffde4.firebaseio.com",
    projectId: "notes-ffde4",
}, config.test.googleAppCredentials);

const admin = require("firebase-admin");

const chai = require("chai");
const chaiAsPromised = require("chai-as-promised");
chai.use(chaiAsPromised);

const testNotesJson = fs.readFileSync("./data.json");
const testNotes = JSON.parse(testNotesJson).notes;

const callableContext = {
    auth: {uid: config.test.userUid}
};


describe("cloud functions", () => {
    let functions;

    before(() => {
        functions = require("../index.js");
        chai.should();
    });

    after(() => {
        test.cleanup();
    });

    describe("sync notes", () => {
        it("should fail not authenticated", () => {
            const data = {query: {id: 1, date: "2021-01-01T00:00:00Z"}};
            return chai.expect(functions.sync.run(data, {}))
                .to.eventually.be.rejectedWith("Authentication required");
        });

        it("should fail no data", () => {
            const data = {query: {}};
            return chai.expect(functions.sync.run(data, callableContext))
                .to.eventually.be.rejected;
        });

        it("should fail on bad ID", () => {
            const data = {query: {id: -1, date: "2021-01-01T00:00:00Z"}};
            return chai.expect(functions.sync.run(data, callableContext))
                .to.eventually.be.rejectedWith("Invalid note ID");
        });

        it("should fail on bad date format", () => {
            const data = {query: {id: 1, date: "2021-01-01T00:00:00 GMT-04:00"}};
            return chai.expect(functions.sync.run(data, callableContext))
                .to.eventually.be.rejectedWith("Invalid date format");
        });

        it("should fail on bad date value", () => {
            const data = {query: {id: 1, date: "0000-00-00T00:00:00Z"}};
            return chai.expect(functions.sync.run(data, callableContext))
                .to.eventually.be.rejectedWith("Invalid date value");
        });

        it("should return no notes", () => {
            const data = {query: {id: 1, date: "2021-01-01T00:00:00Z"}};
            return chai.expect(functions.sync.run(data, callableContext))
                .to.eventually.deep.eql([]);
        });

        it("should return all notes after date 1", () => {
            const data = {query: {id: 1, date: "2020-02-02T12:10:00Z"}};
            return chai.expect(functions.sync.run(data, callableContext))
                .to.eventually.deep.eql([testNotes["3"], testNotes["4"]]);
        });

        it("should return all notes after date and ID 1", () => {
            const data = {query: {id: 1, date: "2020-02-02T12:10:00Z"}};
            return chai.expect(functions.sync.run(data, callableContext))
                .to.eventually.deep.eql([testNotes["3"], testNotes["4"]]);
        });

        it("should return all notes after date and ID 4", () => {
            const data = {query: {id: 4, date: "2020-02-02T12:10:00Z"}};
            return chai.expect(functions.sync.run(data, callableContext))
                .to.eventually.deep.eql([testNotes["3"]]);
        });
    });
});

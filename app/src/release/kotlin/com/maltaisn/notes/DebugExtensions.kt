/*
 * Copyright 2023 Nicolas Maltais
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


/**
 * For checks only performed in debug mode.
 * A failing check should be handled correctly in release mode.
 */
@Suppress("UNUSED_PARAMETER")
fun debugCheck(value: Boolean, message: () -> String = { "" }) = Unit

@Suppress("UNUSED_PARAMETER")
fun debugRequire(value: Boolean, message: () -> String = { "" }) = Unit

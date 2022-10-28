/*
 * Copyright (C) 2022 The ORT Project Authors (See <https://github.com/oss-review-toolkit/ort-server/blob/main/NOTICE>)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 * License-Filename: LICENSE
 */

package org.ossreviewtoolkit.server.dao.tables.runs.shared

import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.LongEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.LongIdTable

import org.ossreviewtoolkit.server.model.runs.RemoteArtifact

/**
 * A table to represent bundle information about a remote artifact.
 */
object RemoteArtifactsTable : LongIdTable("remote_artifacts") {
    val url = text("url")
    val hashValue = text("hash_value")
    val hashAlgorithm = text("hash_algorithm")
}

class RemoteArtifactDao(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<RemoteArtifactDao>(RemoteArtifactsTable)

    var url by RemoteArtifactsTable.url
    var hashValue by RemoteArtifactsTable.hashValue
    var hashAlgorithm by RemoteArtifactsTable.hashAlgorithm

    fun mapToModel() = RemoteArtifact(id.value, url, hashValue, hashAlgorithm)
}

/*
 * Copyright (C) 2023 The ORT Project Authors (See <https://github.com/oss-review-toolkit/ort-server/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.server.dao.tables.runs.repository

import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.LongEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.and

import org.ossreviewtoolkit.server.model.runs.repository.PathExclude

/**
 * A table to represent a path exclude, used within a [PackageConfiguration][PackageConfigurationsTable] and
 * [RepositoryConfiguration][RepositoryConfigurationsTable].
 */
object PathExcludesTable : LongIdTable("path_excludes") {
    val pattern = text("pattern")
    val reason = text("reason")
    val comment = text("comment")
}

class PathExcludeDao(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<PathExcludeDao>(PathExcludesTable) {
        fun findByPathExclude(pathExclude: PathExclude): PathExcludeDao? =
            find {
                PathExcludesTable.pattern eq pathExclude.pattern and
                        (PathExcludesTable.reason eq pathExclude.reason) and
                        (PathExcludesTable.comment eq pathExclude.comment)
            }.singleOrNull()

        fun getOrPut(pathExclude: PathExclude): PathExcludeDao =
            findByPathExclude(pathExclude) ?: new {
                pattern = pathExclude.pattern
                reason = pathExclude.reason
                comment = pathExclude.comment
            }
    }

    var pattern by PathExcludesTable.pattern
    var reason by PathExcludesTable.reason
    var comment by PathExcludesTable.comment

    fun mapToModel() = PathExclude(pattern, reason, comment)
}
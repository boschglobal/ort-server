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

package org.ossreviewtoolkit.server.dao.tables

import kotlinx.serialization.Serializable

import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.LongEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.and

import org.ossreviewtoolkit.server.dao.tables.runs.shared.RemoteArtifactDao
import org.ossreviewtoolkit.server.dao.tables.runs.shared.RemoteArtifactsTable
import org.ossreviewtoolkit.server.dao.tables.runs.shared.VcsInfoDao
import org.ossreviewtoolkit.server.dao.tables.runs.shared.VcsInfoTable
import org.ossreviewtoolkit.server.dao.utils.jsonb
import org.ossreviewtoolkit.server.model.runs.scanner.ArtifactProvenance
import org.ossreviewtoolkit.server.model.runs.scanner.RepositoryProvenance
import org.ossreviewtoolkit.server.model.runs.scanner.Snippet
import org.ossreviewtoolkit.server.model.runs.scanner.TextLocation

/**
 * A table to represent a snippet.
 */
object SnippetsTable : LongIdTable("snippets") {
    val purl = text("purl")
    val artifactId = reference("artifact_id", RemoteArtifactsTable).nullable()
    val vcsId = reference("vcs_id", VcsInfoTable).nullable()
    val path = text("path")
    val startLine = integer("start_line")
    val endLine = integer("end_line")
    val license = text("license")
    val score = float("score")
    val additionalData = jsonb<AdditionalSnippetData>("additional_data").nullable()
}

class SnippetDao(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<SnippetDao>(SnippetsTable) {
        fun findBySnippet(snippet: Snippet): SnippetDao? =
            find {
                SnippetsTable.purl eq snippet.purl and
                        (SnippetsTable.path eq snippet.location.path) and
                        (SnippetsTable.startLine eq snippet.location.startLine) and
                        (SnippetsTable.endLine eq snippet.location.endLine) and
                        (SnippetsTable.license eq snippet.spdxLicense) and
                        (SnippetsTable.score eq snippet.score)
            }.singleOrNull {
                it.mapToModel().provenance == snippet.provenance && it.additionalData?.data == snippet.additionalData
            }

        fun getOrPut(snippet: Snippet): SnippetDao =
            findBySnippet(snippet) ?: new {
                this.purl = snippet.purl
                this.path = snippet.location.path
                this.startLine = snippet.location.startLine
                this.endLine = snippet.location.endLine
                this.score = snippet.score
                this.license = snippet.spdxLicense
                this.additionalData = AdditionalSnippetData(snippet.additionalData)
                this.vcs = (snippet.provenance as? RepositoryProvenance)?.vcsInfo?.let { VcsInfoDao.getOrPut(it) }
                this.artifact = (snippet.provenance as? ArtifactProvenance)?.sourceArtifact?.let {
                    RemoteArtifactDao.getOrPut(it)
                }
            }
    }

    var artifact by RemoteArtifactDao optionalReferencedOn SnippetsTable.artifactId
    var vcs by VcsInfoDao optionalReferencedOn SnippetsTable.vcsId

    var purl by SnippetsTable.purl
    var path by SnippetsTable.path
    var startLine by SnippetsTable.startLine
    var endLine by SnippetsTable.endLine
    var license by SnippetsTable.license
    var score by SnippetsTable.score
    var additionalData by SnippetsTable.additionalData

    fun mapToModel(): Snippet {
        val provenance = if (artifact != null) {
            ArtifactProvenance(sourceArtifact = artifact!!.mapToModel())
        } else {
            val vcsInfo = vcs!!.mapToModel()
            RepositoryProvenance(
                vcsInfo = vcsInfo,
                resolvedRevision = vcsInfo.revision
            )
        }

        return Snippet(
            purl = purl,
            provenance = provenance,
            location = TextLocation(
                path = path,
                startLine = startLine,
                endLine = endLine
            ),
            score = score,
            spdxLicense = license,
            additionalData = additionalData?.data.orEmpty()
        )
    }
}

@Serializable
data class AdditionalSnippetData(
    var data: Map<String, String>
)
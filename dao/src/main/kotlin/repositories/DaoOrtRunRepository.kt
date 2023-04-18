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

package org.ossreviewtoolkit.server.dao.repositories

import kotlinx.datetime.Clock

import org.jetbrains.exposed.sql.and

import org.ossreviewtoolkit.server.dao.blockingQuery
import org.ossreviewtoolkit.server.dao.entityQuery
import org.ossreviewtoolkit.server.dao.tables.OrtRunDao
import org.ossreviewtoolkit.server.dao.tables.OrtRunsTable
import org.ossreviewtoolkit.server.dao.tables.RepositoryDao
import org.ossreviewtoolkit.server.dao.utils.apply
import org.ossreviewtoolkit.server.dao.utils.toDatabasePrecision
import org.ossreviewtoolkit.server.model.JobConfigurations
import org.ossreviewtoolkit.server.model.OrtRun
import org.ossreviewtoolkit.server.model.OrtRunStatus
import org.ossreviewtoolkit.server.model.repositories.OrtRunRepository
import org.ossreviewtoolkit.server.model.util.ListQueryParameters
import org.ossreviewtoolkit.server.model.util.OptionalValue

import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger(DaoOrtRunRepository::class.java)

class DaoOrtRunRepository : OrtRunRepository {
    override fun create(repositoryId: Long, revision: String, jobConfigurations: JobConfigurations): OrtRun =
        blockingQuery {
            val nextIndex = (listForRepository(repositoryId).maxByOrNull { it.index }?.index ?: 0) + 1

            OrtRunDao.new {
                this.index = nextIndex
                this.repository = RepositoryDao[repositoryId]
                this.revision = revision
                this.createdAt = Clock.System.now().toDatabasePrecision()
                this.jobConfigurations = jobConfigurations
                this.status = OrtRunStatus.CREATED
            }.mapToModel()
        }.getOrThrow()

    override fun get(id: Long): OrtRun? = entityQuery { OrtRunDao[id].mapToModel() }

    override fun getByIndex(repositoryId: Long, ortRunIndex: Long): OrtRun? = blockingQuery {
        OrtRunDao.find { OrtRunsTable.repositoryId eq repositoryId and (OrtRunsTable.index eq ortRunIndex) }
            .firstOrNull()?.mapToModel()
    }.getOrThrow()

    override fun listForRepository(repositoryId: Long, parameters: ListQueryParameters): List<OrtRun> = blockingQuery {
        OrtRunDao.find { OrtRunsTable.repositoryId eq repositoryId }
            .apply(OrtRunsTable, parameters)
            .map { it.mapToModel() }
    }.getOrElse {
        logger.error("Cannot list repository for id $repositoryId.", it)
        emptyList()
    }

    override fun update(id: Long, status: OptionalValue<OrtRunStatus>): OrtRun = blockingQuery {
        val ortRun = OrtRunDao[id]

        status.ifPresent { ortRun.status = it }

        OrtRunDao[id].mapToModel()
    }.getOrThrow()

    override fun delete(id: Long) = blockingQuery { OrtRunDao[id].delete() }.getOrThrow()
}

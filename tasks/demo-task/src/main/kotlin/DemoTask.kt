/*
 * Copyright (C) 2024 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

package org.eclipse.apoapsis.ortserver.tasks.demo

import org.eclipse.apoapsis.ortserver.model.OrtRunFilters
import org.eclipse.apoapsis.ortserver.model.OrtRunStatus
import org.eclipse.apoapsis.ortserver.model.repositories.OrtRunRepository
import org.eclipse.apoapsis.ortserver.model.util.ComparisonOperator
import org.eclipse.apoapsis.ortserver.model.util.FilterOperatorAndValue
import org.eclipse.apoapsis.ortserver.tasks.spi.Task

import org.slf4j.LoggerFactory

/**
 * A demo implementation of a task to show how tasks can be packaged and access functionality from ORT Server.
 *
 * This task implementation accesses the database via an injected repository. It just logs the number of currently
 * active ORT runs.
 */
class DemoTask(
    private val repository: OrtRunRepository
) : Task {
    companion object {
        private val filters = OrtRunFilters(
            status = FilterOperatorAndValue(
                ComparisonOperator.IN,
                setOf(OrtRunStatus.ACTIVE)
            )
        )

        private val logger = LoggerFactory.getLogger(DemoTask::class.java)
    }

    override suspend fun execute() {
        val runs = repository.list(filters = filters)
        val repositories = runs.data.map { it.repositoryId }.toSet()

        logger.info("Found {} active ORT runs on these repositories: {}.", runs.totalCount, repositories)
    }
}

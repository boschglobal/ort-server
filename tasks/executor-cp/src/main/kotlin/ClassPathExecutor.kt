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

package org.eclipse.apoapsis.ortserver.tasks.executors.cp

import java.util.ServiceLoader

import kotlin.time.measureTime

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext

import org.eclipse.apoapsis.ortserver.tasks.spi.TaskFactory

import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("ClassPathExecutor")

/**
 * A task execution implementation that runs as a standalone application and executes all tasks (concurrently) that
 * can be found via a service loader on the classpath. The service loader searches for [TaskFactory] implementations;
 * those are used to create the task objects to be executed.
 *
 * This implementation could be used for packaging tasks into separate container images, which could then be run for
 * instance as Kubernetes cron jobs.
 */
suspend fun main() {
    logger.info("Starting task execution.")

    val taskFactories = ServiceLoader.load(TaskFactory::class.java).toList()
    logger.info("Found {} task factories on classpath.", taskFactories.size)

    measureTime {
        val (succeeded, failed) = withContext(Dispatchers.IO) {
            taskFactories.map {
                async { runCatching { it.createTask().execute() } }
            }.awaitAll().partition { it.isSuccess }
        }

        logger.info("{} task(s) executed successfully.", succeeded.size)
        failed.forEach { logger.error("Task execution failed.", it.exceptionOrNull()) }
    }.also { duration -> logger.info("All tasks executed in {}.", duration) }
}

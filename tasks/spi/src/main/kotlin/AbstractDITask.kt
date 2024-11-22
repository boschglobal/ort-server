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

package org.eclipse.apoapsis.ortserver.tasks.spi

import com.typesafe.config.ConfigFactory

import org.eclipse.apoapsis.ortserver.config.ConfigManager

import org.koin.core.Koin
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.module.Module
import org.koin.dsl.koinApplication
import org.koin.dsl.module

import org.slf4j.LoggerFactory

/**
 * An abstract base class for [Task] implementations that require dependency injection.
 *
 * A concrete implementation must provide a list of Koin modules to be taken into account. The class then starts an
 * isolated Koin context containing these modules. It expects that one of the objects in the context implements the
 * [Task] interface. It obtains this object and calls its [Task.execute] method.
 *
 * Using this class, it is possible quite easily to get access to various components and infrastructure of ORT Server,
 * including the database repositories, or the transport components. When setting up the context for dependency
 * injection, a [ConfigManager] instance is included as well; so all configuration settings are available to the task.
 */
abstract class AbstractDITask(
    /** A name for this task. */
    val name: String
) : Task {
    companion object {
        private val logger = LoggerFactory.getLogger(AbstractDITask::class.java)

        /** A default name to be used for tasks if no explicit name was provided. */
        private const val DEFAULT_TASK_NAME = "Unnamed task"

        /**
         * Create a new instance of a [AbstractDITask] that depends on the given [modules]. Optionally, the task can be
         * given a [name].
         */
        fun create(modules: Collection<Module>, name: String = DEFAULT_TASK_NAME): AbstractDITask =
            object : AbstractDITask(name) {
                override fun requiredModules(): Collection<Module> = modules
            }

        /**
         * Create a [Module] that contains a [ConfigManager] instance initialized with the current configuration.
         * That way, executed tasks have access to the configuration settings. The [ConfigManager] is also needed by
         * some modules of ORT Server.
         */
        private fun configModule(): Module = module {
            single { ConfigManager.create(ConfigFactory.load()) }
        }
    }

    override suspend fun execute() {
        logger.info("Executing task '{}'.", name)

        val app = koinApplication {
            modules(listOf(configModule()) + requiredModules())
        }

        runCatching {
            TaskRunnerComponent(app.koin).execute()
        }.onFailure { exception ->
            logger.error("Exception when executing task '{}':", name, exception)
        }

        app.close()
    }

    /**
     * Return a collection of the [Module]s required by this task. These can be custom modules or modules provided by
     * ORT Server.
     */
    protected abstract fun requiredModules(): Collection<Module>
}

/**
 * An internally used component that is used to obtain the task from the current context and execute it.
 */
private class TaskRunnerComponent(
    /** The isolated Koin context. */
    private val koin: Koin
) : KoinComponent {
    override fun getKoin(): Koin = koin

    /**
     * Execute the task in the current context.
     */
    suspend fun execute() {
        get<Task>().execute()
    }
}

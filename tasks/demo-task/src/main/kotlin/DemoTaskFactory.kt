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

import org.eclipse.apoapsis.ortserver.dao.databaseModule
import org.eclipse.apoapsis.ortserver.dao.repositories.DaoOrtRunRepository
import org.eclipse.apoapsis.ortserver.model.repositories.OrtRunRepository
import org.eclipse.apoapsis.ortserver.tasks.spi.AbstractDITask
import org.eclipse.apoapsis.ortserver.tasks.spi.Task
import org.eclipse.apoapsis.ortserver.tasks.spi.TaskFactory

import org.koin.dsl.module

class DemoTaskFactory : TaskFactory {
    private val module = module {
        single<Task> { DemoTask(get()) }
        single<OrtRunRepository> { DaoOrtRunRepository(get()) }
    }

    override fun createTask(): Task =
        AbstractDITask.create(listOf(databaseModule(), module), "Active runs logging task")
}

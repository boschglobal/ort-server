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

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

import org.eclipse.apoapsis.ortserver.config.ConfigManager

import org.koin.core.module.Module
import org.koin.dsl.module

class AbstractDITaskTest : StringSpec({
    "A task should be executed with correct dependencies" {
        val successFlag = AtomicBoolean()

        val taskModule = flagModule(successFlag)

        val diTask = AbstractDITask.create(listOf(taskModule))
        diTask.execute()

        successFlag.get() shouldBe true
    }

    "Isolated Koin contexts should be created to execute multiple tasks" {
        val successFlag1 = AtomicBoolean()
        val successFlag2 = AtomicBoolean()

        val taskModule1 = flagModule(successFlag1)
        val taskModule2 = flagModule(successFlag2)

        val diTask1 = AbstractDITask.create(listOf(taskModule1))
        val diTask2 = AbstractDITask.create(listOf(taskModule2))
        diTask1.execute()
        diTask2.execute()

        successFlag1.get() shouldBe true
        successFlag2.get() shouldBe true
    }

    "Tasks should be given names" {
        val taskName = "Test task"

        val diTask = AbstractDITask.create(emptyList(), taskName)

        diTask.name shouldBe taskName
    }

    "Exceptions during task execution should be handled" {
        val taskModule = module {
            single<Task> {
                object : Task {
                    override suspend fun execute() {
                        throw IOException("Test exception")
                    }
                }
            }
        }

        val diTask = AbstractDITask.create(listOf(taskModule))

        // Can only check that no exception is thrown.
        diTask.execute()
    }

    "A ConfigManager should be available in the context" {
        val successFlag = AtomicBoolean()

        val module = module {
            single { successFlag }
            single<Task> { ConfigTestTask(get(), get()) }
        }

        val diTask = AbstractDITask.create(listOf(module))
        diTask.execute()

        successFlag.get() shouldBe true
    }
})

/**
 * A test task implementation used to check whether dependencies are correctly injected and the task execution
 * works. The task sets an injected flag to *true* when it is invoked.
 */
private class TestTask(
    private val resultFlag: AtomicBoolean
) : Task {
    override suspend fun execute() {
        resultFlag.compareAndSet(false, true) shouldBe true
    }
}

/**
 * A task implementation used to check whether a correctly initialized [ConfigManager] is available in the context.
 * If this is the case, it sets a flag.
 */
private class ConfigTestTask(
    private val configManager: ConfigManager,
    private val resultFlag: AtomicBoolean
) : Task {
    override suspend fun execute() {
        if (configManager.getInt("test.answer") == 42) {
            resultFlag.set(true)
        }
    }
}

/**
 * Create a [Module] containing a [TestTask] instance that is injected the given [successFlag].
 */
private fun flagModule(successFlag: AtomicBoolean): Module = module {
    single { successFlag }
    single<Task> { TestTask(get()) }
}

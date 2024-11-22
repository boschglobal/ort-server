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

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.nulls.shouldNotBeNull

import java.io.IOException
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

import kotlinx.coroutines.delay
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

import org.eclipse.apoapsis.ortserver.tasks.spi.Task
import org.eclipse.apoapsis.ortserver.tasks.spi.TaskFactory

class ClassPathExecutorTest : StringSpec({
    "Task factories should be found on the classpath, and tasks should be executed in parallel" {
        main()

        val info1 = nextExecutionInfo()
        val info2 = nextExecutionInfo()

        setOf(info1, info2).map(TaskExecutionInfo::taskName) shouldContainExactlyInAnyOrder listOf("Task 1", "Task 2")

        // Test for concurrent execution.
        info1.startTime shouldBeLessThan info2.endTime
        info2.startTime shouldBeLessThan info1.endTime
    }
})

/**
 * A data class to store and propagate information about the execution of a task.
 */
private data class TaskExecutionInfo(
    /** The name of the task that was executed. */
    val taskName: String,

    /** Execution start time. */
    val startTime: Instant,

    /** Execution end time. */
    val endTime: Instant
)

/** A queue to transport execution information from tasks to the test class. */
private val executionInfoQueue = LinkedBlockingQueue<TaskExecutionInfo>()

/**
 * Fetch a [TaskExecutionInfo] from the queue. Fail if there is no result within a timeout.
 */
private fun nextExecutionInfo(): TaskExecutionInfo =
    executionInfoQueue.poll(3, TimeUnit.SECONDS).shouldNotBeNull()

/** The name of a task that will fail with an exception. */
private const val EXCEPTION_TASK_NAME = "ExceptionTask"

/**
 * A test task implementation that simulates an execution that takes a while and stores execution information in
 * the queue.
 */
private class TestTask(val name: String) : Task {
    override suspend fun execute() {
        if (name == EXCEPTION_TASK_NAME) {
            throw IOException("Test exception: Thrown by task '$name'.")
        }

        val startTime = Clock.System.now()

        delay(1000)

        executionInfoQueue.add(
            TaskExecutionInfo(
                taskName = name,
                startTime = startTime,
                endTime = Clock.System.now()
            )
        )
    }
}

/**
 * A task factory implementation that creates a [TestTask] with a specific name.
 */
internal class TaskFactory1 : TaskFactory {
    override fun createTask(): Task = TestTask("Task 1")
}

/**
 * Another task factory implementation that creates a [TestTask] with another name.
 */
internal class TaskFactory2 : TaskFactory {
    override fun createTask(): Task = TestTask("Task 2")
}

/**
 * A task factory implementation that creates a [TestTask] that will fail with an exception.
 */
internal class ExceptionTaskFactory : TaskFactory {
    override fun createTask(): Task = TestTask(EXCEPTION_TASK_NAME)
}

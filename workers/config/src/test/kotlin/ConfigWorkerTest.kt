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

package org.ossreviewtoolkit.server.workers.config

import io.kotest.assertions.fail
import io.kotest.common.runBlocking
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.beInstanceOf

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify

import kotlinx.datetime.Clock

import org.ossreviewtoolkit.server.config.ConfigException
import org.ossreviewtoolkit.server.config.ConfigManager
import org.ossreviewtoolkit.server.config.Context
import org.ossreviewtoolkit.server.dao.test.mockkTransaction
import org.ossreviewtoolkit.server.model.JobConfigurations
import org.ossreviewtoolkit.server.model.repositories.OrtRunRepository
import org.ossreviewtoolkit.server.model.runs.OrtIssue
import org.ossreviewtoolkit.server.model.util.asPresent
import org.ossreviewtoolkit.server.workers.common.RunResult
import org.ossreviewtoolkit.server.workers.common.context.WorkerContext
import org.ossreviewtoolkit.server.workers.common.context.WorkerContextFactory

class ConfigWorkerTest : StringSpec({
    beforeSpec {
        mockkObject(ConfigValidator)
    }

    afterSpec {
        unmockkAll()
    }

    "The ORT run configuration should be validated successfully" {
        val (contextFactory, context) = mockContext()
        context.mockConfigManager()

        val resolvedConfig = mockk<JobConfigurations>()
        mockValidator(context, ConfigValidationResultSuccess(resolvedConfig, validationIssues))

        val ortRunRepository = mockk<OrtRunRepository> {
            every {
                update(RUN_ID, any(), any(), any(), any())
            } returns mockk()
        }

        mockkTransaction {
            val worker = ConfigWorker(mockk(), ortRunRepository, contextFactory)
            worker.testRun() shouldBe RunResult.Success

            verify {
                ortRunRepository.update(
                    id = RUN_ID,
                    resolvedConfig = resolvedConfig.asPresent(),
                    resolvedConfigContext = RESOLVED_CONTEXT.asPresent(),
                    issues = validationIssues.asPresent()
                )
            }
        }
    }

    "A failed validation should be handled" {
        val (contextFactory, context) = mockContext()
        context.mockConfigManager()

        mockValidator(context, ConfigValidationResultFailure(validationIssues))

        val ortRunRepository = mockk<OrtRunRepository> {
            every {
                update(RUN_ID, any(), any(), any(), any())
            } returns mockk()
        }

        mockkTransaction {
            val worker = ConfigWorker(mockk(), ortRunRepository, contextFactory)
            when (val result = worker.testRun()) {
                is RunResult.Failed -> result.error should beInstanceOf<IllegalArgumentException>()
                else -> fail("Unexpected result: $result")
            }

            verify {
                ortRunRepository.update(
                    id = RUN_ID,
                    resolvedConfigContext = RESOLVED_CONTEXT.asPresent(),
                    issues = validationIssues.asPresent()
                )
            }
        }
    }

    "Exceptions during validation should be handled" {
        val (contextFactory, context) = mockContext()

        val configException = ConfigException("test exception", null)
        val configManager = context.mockConfigManager()
        every { configManager.getFileAsString(any()) } throws configException

        val worker = ConfigWorker(mockk(), mockk(), contextFactory)
        when (val result = worker.testRun()) {
            is RunResult.Failed -> result.error shouldBe configException
            else -> fail("Unexpected result: $result")
        }
    }
})

/** The ID of a test run. */
private const val RUN_ID = 20230802103508L

/** A resolved context. */
private const val RESOLVED_CONTEXT = "theResolvedConfigurationContext"

/** Simulated script to perform parameter validation and transformation. */
private const val PARAMETERS_SCRIPT = "Script to validate parameters"

/** A list with validation issues to be returned by the mock validator. */
private val validationIssues = listOf(
    OrtIssue(Clock.System.now(), "ConfigWorkerTest", "Test message", "TEST")
)

/**
 * Create a mock context factory together with a mock context that is returned by the factory.
 */
private fun mockContext(): Pair<WorkerContextFactory, WorkerContext> {
    val context = mockk<WorkerContext>()
    val factory = mockk<WorkerContextFactory> {
        every { createContext(RUN_ID) } returns context
    }

    return factory to context
}

/**
 * Create a mock [ConfigValidator] and prepare the factory function to return it for the given [context]. The mock
 * is configured to return the given [result] for the test script.
 */
private fun mockValidator(context: WorkerContext, result: ConfigValidationResult): ConfigValidator {
    val validator = mockk<ConfigValidator> {
        every { validate(PARAMETERS_SCRIPT) } returns result
    }

    every { ConfigValidator.create(context) } returns validator

    return validator
}

/**
 * Create a mock [ConfigManager] and prepare this [WorkerContext] mock to return it. The mock is configured to
 * return the test validation script.
 */
private fun WorkerContext.mockConfigManager(): ConfigManager {
    val configManager = mockk<ConfigManager> {
        every { context } returns Context(RESOLVED_CONTEXT)
        every { getFileAsString(ConfigWorker.VALIDATION_SCRIPT_PATH) } returns PARAMETERS_SCRIPT
    }

    every { configManager(resolveContext = true) } returns configManager

    return configManager
}

/**
 * Helper function to invoke the test worker with test parameters in a coroutine context.
 */
private fun ConfigWorker.testRun(): RunResult = runBlocking { run(RUN_ID) }
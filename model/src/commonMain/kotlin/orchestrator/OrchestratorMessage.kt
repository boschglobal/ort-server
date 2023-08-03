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

package org.ossreviewtoolkit.server.model.orchestrator

import kotlinx.serialization.Serializable

import org.ossreviewtoolkit.server.model.OrtRun

/**
 * Base class for the hierarchy of messages that can be processed by the Orchestrator component.
 */
@Serializable
sealed class OrchestratorMessage

/**
 * A message notifying the Orchestrator about a result produced by the Config worker.
 */
@Serializable
data class ConfigWorkerResult(
    /** The ID of the ORT run that was processed by the worker. */
    val ortRunId: Long
) : OrchestratorMessage()

/**
 * A message notifying the Orchestrator about a failed job of the Config worker.
 */
@Serializable
data class ConfigWorkerError(
    /** The ID of the ORT run on which the worker failed. */
    val ortRunId: Long
) : OrchestratorMessage()

/**
 * A message notifying the Orchestrator about a result produced by the Analyzer Worker.
 */
@Serializable
data class AnalyzerWorkerResult(
    /** The ID of the Analyzer job, as it is stored in the database. */
    val jobId: Long
) : OrchestratorMessage()

/**
 * A message notifying the Orchestrator about a failed Analyzer worker job.
 */
@Serializable
data class AnalyzerWorkerError(
    /** The ID of the Analyzer job, as it is stored in the database. */
    val jobId: Long
) : OrchestratorMessage()

/**
 * A message notifying the Orchestrator about a result produced by the Advisor Worker.
 */
@Serializable
data class AdvisorWorkerResult(
    /** The ID of the Advisor job, as it is stored in the database. */
    val jobId: Long
) : OrchestratorMessage()

/**
 * A message notifying the Orchestrator about a failed Advisor worker job.
 */
@Serializable
data class AdvisorWorkerError(
    /** The ID of the Advisor job, as it is stored in the database. */
    val jobId: Long
) : OrchestratorMessage()

/**
 * A message notifying the Orchestrator about a result produced by the Scanner Worker.
 */
@Serializable
data class ScannerWorkerResult(
    /** The ID of the Scanner job, as it is stored in the database. */
    val jobId: Long
) : OrchestratorMessage()

/**
 * A message notifying the Orchestrator about a failed Scanner worker job.
 */
@Serializable
data class ScannerWorkerError(
    /** The ID of the Scanner job, as it is stored in the database. */
    val jobId: Long
) : OrchestratorMessage()

/**
 * A message notifying the Orchestrator about a result produced by the Evaluator Worker.
 */
@Serializable
data class EvaluatorWorkerResult(
    /** The ID of the Evaluator job, as it is stored in the database. */
    val jobId: Long
) : OrchestratorMessage()

/**
 * A message notifying the Orchestrator about a failed Evaluator worker job.
 */
@Serializable
data class EvaluatorWorkerError(
    /** The ID of the Evaluator job, as it is stored in the database. */
    val jobId: Long
) : OrchestratorMessage()

/**
 * A message notifying the Orchestrator about a result produced by the Reporter Worker.
 */
@Serializable
data class ReporterWorkerResult(
    /** The ID of the Reporter job, as it is stored in the database. */
    val jobId: Long
) : OrchestratorMessage()

/**
 * A message notifying the Orchestrator about a failed Reporter worker job.
 */
@Serializable
data class ReporterWorkerError(
    /** The ID of the Reporter job, as it is stored in the database. */
    val jobId: Long
) : OrchestratorMessage()

/**
 * A message notifying the Orchestrator about a new ORT run.
 */
@Serializable
data class CreateOrtRun(val ortRun: OrtRun) : OrchestratorMessage()

/**
 * A message notifying the Orchestrator about a (critical) error of a worker. This error means that there was a
 * fatal crash during job processing which even prevents the affected endpoint from sending a proper error message.
 * Therefore, only limited error information is available.
 */
data class WorkerError(
    /** The name of the endpoint where the error has happened. */
    val endpointName: String,
) : OrchestratorMessage()

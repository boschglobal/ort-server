/*
 * Copyright (C) 2022 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

package org.eclipse.apoapsis.ortserver.workers.analyzer

import org.eclipse.apoapsis.ortserver.components.pluginmanager.PluginService
import org.eclipse.apoapsis.ortserver.dao.dbQuery
import org.eclipse.apoapsis.ortserver.model.InfrastructureService
import org.eclipse.apoapsis.ortserver.model.runs.Identifier
import org.eclipse.apoapsis.ortserver.model.runs.ShortestDependencyPath
import org.eclipse.apoapsis.ortserver.services.ortrun.OrtRunService
import org.eclipse.apoapsis.ortserver.services.ortrun.mapToModel
import org.eclipse.apoapsis.ortserver.transport.EndpointComponent
import org.eclipse.apoapsis.ortserver.workers.common.JobIgnoredException
import org.eclipse.apoapsis.ortserver.workers.common.RunResult
import org.eclipse.apoapsis.ortserver.workers.common.context.WorkerContextFactory
import org.eclipse.apoapsis.ortserver.workers.common.env.EnvironmentService
import org.eclipse.apoapsis.ortserver.workers.common.validateForProcessing

import org.jetbrains.exposed.sql.Database

import org.ossreviewtoolkit.model.Severity

import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger(AnalyzerWorker::class.java)

internal class AnalyzerWorker(
    private val db: Database,
    private val downloader: AnalyzerDownloader,
    private val runner: AnalyzerRunner,
    private val ortRunService: OrtRunService,
    private val contextFactory: WorkerContextFactory,
    private val environmentService: EnvironmentService,
    private val pluginService: PluginService
) {
    suspend fun run(jobId: Long, traceId: String): RunResult = runCatching {
        var job = getValidAnalyzerJob(jobId)
        val ortRun = ortRunService.getOrtRun(job.ortRunId)
            ?: throw IllegalArgumentException("The ORT run '${job.ortRunId}' does not exist.")
        val repository = ortRunService.getHierarchyForOrtRun(ortRun.id)?.repository
            ?: throw IllegalArgumentException("The repository '${ortRun.repositoryId}' does not exist.")

        job = ortRunService.startAnalyzerJob(job.id)
            ?: throw IllegalArgumentException("The analyzer job with id '$jobId' could not be started.")
        logger.debug("Analyzer job with id '{}' started at {}.", job.id, job.startedAt)

        contextFactory.withContext(job.ortRunId) { context ->
            if (job.configuration.keepAliveWorker) {
                EndpointComponent.generateKeepAliveFile()
            }

            val envConfigFromJob = job.configuration.environmentConfig
            val repositoryServices =
                environmentService.findInfrastructureServicesForRepository(context, envConfigFromJob)
            if (repositoryServices.isNotEmpty()) {
                logger.info(
                    "Generating a .netrc file with credentials from infrastructure services '{}' to download the " +
                            "repository.",
                    repositoryServices.map(InfrastructureService::name)
                )

                environmentService.setupAuthentication(context, repositoryServices)
            }

            val downloadResult = downloader.downloadRepository(
                repository.url,
                ortRun.revision,
                ortRun.path.orEmpty(),
                job.configuration.submoduleFetchStrategy
            )

            if (downloadResult.initRevision != ortRun.revision) {
                logger.info(
                    "Updating revision of ORT run from '${ortRun.revision}' to '${downloadResult.initRevision}'."
                )
                ortRunService.updateRevision(ortRun.id, downloadResult.initRevision)
            }

            ortRunService.updateResolvedRevision(ortRun.id, downloadResult.resolvedRevision)

            // Set the default package managers if none are configured, because the runner might be executed in a
            // separate process which cannot access the database.
            val jobConfiguration = job.configuration.takeIf { it.enabledPackageManagers.orEmpty().isNotEmpty() }
                ?: job.configuration.copy(enabledPackageManagers = getDefaultPackageManagers(pluginService))

            val resolvedEnvConfig = environmentService.setUpEnvironment(
                context,
                downloadResult.directory,
                envConfigFromJob,
                repositoryServices
            )
            val ortResult = runner.run(context, downloadResult.directory, jobConfiguration, resolvedEnvConfig)

            ortRunService.storeRepositoryInformation(ortRun.id, ortResult.repository)
            ortRunService.storeResolvedPackageCurations(job.ortRunId, ortResult.resolvedConfiguration.packageCurations)

            val analyzerRun = ortResult.analyzer
                ?: throw AnalyzerException("ORT Analyzer failed to create a result.")

            val issues = analyzerRun.result.getAllIssues().values.flatten()

            logger.info(
                "Analyzer job '${job.id}' for repository '${repository.url}' with revision ${ortRun.revision} " +
                        "finished with '${issues.size}' issues."
            )

            val shortestPathsByIdentifier = mutableMapOf<Identifier, MutableList<ShortestDependencyPath>>()

            analyzerRun.result.projects.forEach { project ->
                getIdentifierToShortestPathsMap(
                    project.id.mapToModel(),
                    ortResult.dependencyNavigator.getShortestPaths(project)
                ).forEach { (identifier, path) ->
                    shortestPathsByIdentifier.getOrPut(identifier) { mutableListOf() } += path
                }
            }

            db.dbQuery {
                getValidAnalyzerJob(jobId)
                ortRunService.storeAnalyzerRun(analyzerRun.mapToModel(jobId), shortestPathsByIdentifier)
            }

            if (issues.any { it.severity >= Severity.WARNING }) {
                RunResult.FinishedWithIssues
            } else {
                RunResult.Success
            }
        }
    }.getOrElse {
        when (it) {
            is JobIgnoredException -> {
                logger.warn("Message with traceId '$traceId' ignored: ${it.message}")
                RunResult.Ignored
            }

            else -> {
                logger.error("Error while processing message with traceId '$traceId': ${it.message}")
                RunResult.Failed(it)
            }
        }
    }

    private fun getValidAnalyzerJob(jobId: Long) =
        ortRunService.getAnalyzerJob(jobId).validateForProcessing(jobId)
}

private class AnalyzerException(message: String) : Exception(message)

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

@file:Suppress("TooManyFunctions")

package org.eclipse.apoapsis.ortserver.api.v1

import org.eclipse.apoapsis.ortserver.api.v1.AdvisorJob as ApiAdvisorJob
import org.eclipse.apoapsis.ortserver.api.v1.AdvisorJobConfiguration as ApiAdvisorJobConfiguration
import org.eclipse.apoapsis.ortserver.api.v1.AnalyzerJob as ApiAnalyzerJob
import org.eclipse.apoapsis.ortserver.api.v1.AnalyzerJobConfiguration as ApiAnalyzerJobConfiguration
import org.eclipse.apoapsis.ortserver.api.v1.EnvironmentConfig as ApiEnvironmentConfig
import org.eclipse.apoapsis.ortserver.api.v1.EnvironmentVariableDeclaration as ApiEnvironmentVariableDeclaration
import org.eclipse.apoapsis.ortserver.api.v1.EvaluatorJob as ApiEvaluatorJob
import org.eclipse.apoapsis.ortserver.api.v1.EvaluatorJobConfiguration as ApiEvaluatorJobConfiguration
import org.eclipse.apoapsis.ortserver.api.v1.InfrastructureService as ApiInfrastructureService
import org.eclipse.apoapsis.ortserver.api.v1.JobConfigurations as ApiJobConfigurations
import org.eclipse.apoapsis.ortserver.api.v1.JobStatus as ApiJobStatus
import org.eclipse.apoapsis.ortserver.api.v1.Jobs as ApiJobs
import org.eclipse.apoapsis.ortserver.api.v1.Organization as ApiOrganization
import org.eclipse.apoapsis.ortserver.api.v1.OrtIssue as ApiOrtIssue
import org.eclipse.apoapsis.ortserver.api.v1.OrtRun as ApiOrtRun
import org.eclipse.apoapsis.ortserver.api.v1.OrtRunStatus as ApiOrtRunStatus
import org.eclipse.apoapsis.ortserver.api.v1.PackageManagerConfiguration as ApiPackageManagerConfiguration
import org.eclipse.apoapsis.ortserver.api.v1.PluginConfiguration as ApiPluginConfiguration
import org.eclipse.apoapsis.ortserver.api.v1.Product as ApiProduct
import org.eclipse.apoapsis.ortserver.api.v1.ProviderPluginConfiguration as ApiProviderPluginConfiguration
import org.eclipse.apoapsis.ortserver.api.v1.ReporterAsset as ApiReporterAsset
import org.eclipse.apoapsis.ortserver.api.v1.ReporterJob as ApiReporterJob
import org.eclipse.apoapsis.ortserver.api.v1.ReporterJobConfiguration as ApiReporterJobConfiguration
import org.eclipse.apoapsis.ortserver.api.v1.Repository as ApiRepository
import org.eclipse.apoapsis.ortserver.api.v1.RepositoryType as ApiRepositoryType
import org.eclipse.apoapsis.ortserver.api.v1.ScannerJob as ApiScannerJob
import org.eclipse.apoapsis.ortserver.api.v1.ScannerJobConfiguration as ApiScannerJobConfiguration
import org.eclipse.apoapsis.ortserver.api.v1.Secret as ApiSecret
import org.eclipse.apoapsis.ortserver.model.AdvisorJob
import org.eclipse.apoapsis.ortserver.model.AdvisorJobConfiguration
import org.eclipse.apoapsis.ortserver.model.AnalyzerJob
import org.eclipse.apoapsis.ortserver.model.AnalyzerJobConfiguration
import org.eclipse.apoapsis.ortserver.model.EnvironmentConfig
import org.eclipse.apoapsis.ortserver.model.EnvironmentVariableDeclaration
import org.eclipse.apoapsis.ortserver.model.EvaluatorJob
import org.eclipse.apoapsis.ortserver.model.EvaluatorJobConfiguration
import org.eclipse.apoapsis.ortserver.model.InfrastructureService
import org.eclipse.apoapsis.ortserver.model.InfrastructureServiceDeclaration
import org.eclipse.apoapsis.ortserver.model.JobConfigurations
import org.eclipse.apoapsis.ortserver.model.JobStatus
import org.eclipse.apoapsis.ortserver.model.Jobs
import org.eclipse.apoapsis.ortserver.model.Organization
import org.eclipse.apoapsis.ortserver.model.OrtRun
import org.eclipse.apoapsis.ortserver.model.OrtRunStatus
import org.eclipse.apoapsis.ortserver.model.PluginConfiguration
import org.eclipse.apoapsis.ortserver.model.Product
import org.eclipse.apoapsis.ortserver.model.ProviderPluginConfiguration
import org.eclipse.apoapsis.ortserver.model.ReporterAsset
import org.eclipse.apoapsis.ortserver.model.ReporterJob
import org.eclipse.apoapsis.ortserver.model.ReporterJobConfiguration
import org.eclipse.apoapsis.ortserver.model.Repository
import org.eclipse.apoapsis.ortserver.model.RepositoryType
import org.eclipse.apoapsis.ortserver.model.ScannerJob
import org.eclipse.apoapsis.ortserver.model.ScannerJobConfiguration
import org.eclipse.apoapsis.ortserver.model.Secret
import org.eclipse.apoapsis.ortserver.model.runs.OrtIssue
import org.eclipse.apoapsis.ortserver.model.runs.PackageManagerConfiguration
import org.eclipse.apoapsis.ortserver.model.util.OptionalValue

fun AdvisorJob.mapToApi() =
    ApiAdvisorJob(
        id,
        createdAt,
        startedAt,
        finishedAt,
        configuration.mapToApi(),
        status.mapToApi()
    )

fun AdvisorJobConfiguration.mapToApi() =
    ApiAdvisorJobConfiguration(advisors, skipExcluded, config?.mapValues { it.value.mapToApi() })

fun ApiAdvisorJobConfiguration.mapToModel() =
    AdvisorJobConfiguration(advisors, skipExcluded, config?.mapValues { it.value.mapToModel() })

fun AnalyzerJob.mapToApi() =
    ApiAnalyzerJob(
        id,
        createdAt,
        startedAt,
        finishedAt,
        configuration.mapToApi(),
        status.mapToApi()
    )

fun AnalyzerJobConfiguration.mapToApi() =
    ApiAnalyzerJobConfiguration(
        allowDynamicVersions,
        disabledPackageManagers,
        enabledPackageManagers,
        environmentConfig?.mapToApi(),
        packageCurationProviders.map { it.mapToApi() },
        packageManagerOptions?.mapValues { it.value.mapToApi() },
        skipExcluded
    )

fun ApiAnalyzerJobConfiguration.mapToModel() =
    AnalyzerJobConfiguration(
        allowDynamicVersions,
        disabledPackageManagers,
        enabledPackageManagers,
        environmentConfig?.mapToModel(),
        packageCurationProviders.map { it.mapToModel() },
        packageManagerOptions?.mapValues { it.value.mapToModel() },
        skipExcluded
    )

fun EvaluatorJob.mapToApi() =
    ApiEvaluatorJob(
        id,
        createdAt,
        startedAt,
        finishedAt,
        configuration.mapToApi(),
        status.mapToApi()
    )

fun EvaluatorJobConfiguration.mapToApi() =
    ApiEvaluatorJobConfiguration(
        copyrightGarbageFile,
        licenseClassificationsFile,
        packageConfigurationProviders.map { it.mapToApi() },
        resolutionsFile,
        ruleSet
    )

fun ApiEvaluatorJobConfiguration.mapToModel() =
    EvaluatorJobConfiguration(
        copyrightGarbageFile,
        licenseClassificationsFile,
        packageConfigurationProviders.map { it.mapToModel() },
        resolutionsFile,
        ruleSet
    )

fun JobStatus.mapToApi() = ApiJobStatus.valueOf(name)

fun ApiJobStatus.mapToModel() = JobStatus.valueOf(name)

fun JobConfigurations.mapToApi() =
    ApiJobConfigurations(
        analyzer.mapToApi(),
        advisor?.mapToApi(),
        scanner?.mapToApi(),
        evaluator?.mapToApi(),
        reporter?.mapToApi(),
        parameters
    )

fun ApiJobConfigurations.mapToModel() =
    JobConfigurations(
        analyzer.mapToModel(),
        advisor?.mapToModel(),
        scanner?.mapToModel(),
        evaluator?.mapToModel(),
        reporter?.mapToModel(),
        parameters
    )

fun Jobs.mapToApi() =
    ApiJobs(analyzer?.mapToApi(), advisor?.mapToApi(), scanner?.mapToApi(), evaluator?.mapToApi(), reporter?.mapToApi())

fun Organization.mapToApi() = ApiOrganization(id, name, description)

fun OrtIssue.mapToApi() = ApiOrtIssue(timestamp, source, message, severity)

fun OrtRun.mapToApi(jobs: ApiJobs) =
    ApiOrtRun(
        id = id,
        index = index,
        repositoryId,
        revision,
        createdAt,
        finishedAt,
        jobConfigs.mapToApi(),
        resolvedJobConfigs?.mapToApi(),
        jobs,
        status.mapToApi(),
        labels,
        issues = issues.map { it.mapToApi() },
        jobConfigContext,
        resolvedJobConfigContext
    )

fun OrtRunStatus.mapToApi() = ApiOrtRunStatus.valueOf(name)

fun Product.mapToApi() = ApiProduct(id, name, description)

fun Repository.mapToApi() = ApiRepository(id, type.mapToApi(), url)

fun RepositoryType.mapToApi() = ApiRepositoryType.valueOf(name)

fun ApiRepositoryType.mapToModel() = RepositoryType.forName(name)

fun OptionalValue<ApiRepositoryType>.mapToModel() = map { it.mapToModel() }

fun ReporterJob.mapToApi() =
    ApiReporterJob(
        id,
        createdAt,
        startedAt,
        finishedAt,
        configuration.mapToApi(),
        status.mapToApi()
    )

fun ReporterJobConfiguration.mapToApi() =
    ApiReporterJobConfiguration(
        copyrightGarbageFile,
        formats,
        licenseClassificationsFile,
        packageConfigurationProviders.map { it.mapToApi() },
        resolutionsFile,
        assetFiles.map { it.mapToApi() },
        assetDirectories.map { it.mapToApi() },
        config?.mapValues { it.value.mapToApi() }
    )

fun ApiReporterJobConfiguration.mapToModel() =
    ReporterJobConfiguration(
        copyrightGarbageFile,
        formats,
        licenseClassificationsFile,
        packageConfigurationProviders.map { it.mapToModel() },
        resolutionsFile,
        assetFiles.map { it.mapToModel() },
        assetDirectories.map { it.mapToModel() },
        config?.mapValues { it.value.mapToModel() }
    )

fun ScannerJob.mapToApi() =
    ApiScannerJob(
        id,
        createdAt,
        startedAt,
        finishedAt,
        configuration.mapToApi(),
        status.mapToApi()
    )

fun ScannerJobConfiguration.mapToApi() = ApiScannerJobConfiguration(
    createMissingArchives,
    detectedLicenseMappings,
    ignorePatterns,
    projectScanners,
    scanners,
    skipConcluded,
    skipExcluded,
    config?.mapValues { it.value.mapToApi() }
)

fun ApiScannerJobConfiguration.mapToModel() = ScannerJobConfiguration(
    createMissingArchives,
    detectedLicenseMappings,
    ignorePatterns,
    projectScanners,
    scanners,
    skipConcluded,
    skipExcluded,
    config?.mapValues { it.value.mapToModel() }
)

fun Secret.mapToApi() = ApiSecret(name, description)

fun InfrastructureService.mapToApi() =
    ApiInfrastructureService(name, url, description, usernameSecret.name, passwordSecret.name, excludeFromNetrc)

fun ApiInfrastructureService.mapToModel() =
    InfrastructureServiceDeclaration(name, url, description, usernameSecretRef, passwordSecretRef, excludeFromNetrc)

fun InfrastructureServiceDeclaration.mapToApi() =
    ApiInfrastructureService(name, url, description, usernameSecret, passwordSecret, excludeFromNetrc)

fun ApiEnvironmentVariableDeclaration.mapToModel() = EnvironmentVariableDeclaration(name, secretName)

fun EnvironmentVariableDeclaration.mapToApi() = ApiEnvironmentVariableDeclaration(name, secretName)

fun EnvironmentConfig.mapToApi() =
    ApiEnvironmentConfig(
        infrastructureServices = infrastructureServices.map { it.mapToApi() },
        environmentDefinitions = environmentDefinitions,
        environmentVariables = environmentVariables.map { it.mapToApi() },
        strict = strict
    )

fun ApiEnvironmentConfig.mapToModel() =
    EnvironmentConfig(
        infrastructureServices = infrastructureServices.map { it.mapToModel() },
        environmentDefinitions = environmentDefinitions,
        environmentVariables = environmentVariables.map { it.mapToModel() },
        strict = strict
    )

fun PackageManagerConfiguration.mapToApi() =
    ApiPackageManagerConfiguration(mustRunAfter = mustRunAfter, options = options)

fun ApiPackageManagerConfiguration.mapToModel() =
    PackageManagerConfiguration(mustRunAfter = mustRunAfter, options = options)

fun PluginConfiguration.mapToApi() = ApiPluginConfiguration(options = options, secrets = secrets)

fun ApiPluginConfiguration.mapToModel() = PluginConfiguration(options = options, secrets = secrets)

fun ProviderPluginConfiguration.mapToApi() =
    ApiProviderPluginConfiguration(
        type = type,
        id = id,
        enabled = enabled,
        options = options,
        secrets = secrets
    )

fun ApiProviderPluginConfiguration.mapToModel() =
    ProviderPluginConfiguration(
        type = type,
        id = id,
        enabled = enabled,
        options = options,
        secrets = secrets
    )

fun ReporterAsset.mapToApi() =
    ApiReporterAsset(sourcePath, targetFolder, targetName)

fun ApiReporterAsset.mapToModel() =
    ReporterAsset(sourcePath, targetFolder, targetName)

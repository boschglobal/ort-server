/*
 * Copyright (C) 2023 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

package org.eclipse.apoapsis.ortserver.services.ortrun

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

import org.eclipse.apoapsis.ortserver.dao.blockingQuery
import org.eclipse.apoapsis.ortserver.dao.dbQuery
import org.eclipse.apoapsis.ortserver.dao.repositories.ortrun.OrtRunDao
import org.eclipse.apoapsis.ortserver.dao.tables.NestedRepositoriesTable
import org.eclipse.apoapsis.ortserver.dao.tables.shared.VcsInfoDao
import org.eclipse.apoapsis.ortserver.model.AdvisorJob
import org.eclipse.apoapsis.ortserver.model.AnalyzerJob
import org.eclipse.apoapsis.ortserver.model.EvaluatorJob
import org.eclipse.apoapsis.ortserver.model.Hierarchy
import org.eclipse.apoapsis.ortserver.model.NotifierJob
import org.eclipse.apoapsis.ortserver.model.OrtRun
import org.eclipse.apoapsis.ortserver.model.OrtRunFilters
import org.eclipse.apoapsis.ortserver.model.ReporterJob
import org.eclipse.apoapsis.ortserver.model.ScannerJob
import org.eclipse.apoapsis.ortserver.model.repositories.AdvisorJobRepository
import org.eclipse.apoapsis.ortserver.model.repositories.AdvisorRunRepository
import org.eclipse.apoapsis.ortserver.model.repositories.AnalyzerJobRepository
import org.eclipse.apoapsis.ortserver.model.repositories.AnalyzerRunRepository
import org.eclipse.apoapsis.ortserver.model.repositories.EvaluatorJobRepository
import org.eclipse.apoapsis.ortserver.model.repositories.EvaluatorRunRepository
import org.eclipse.apoapsis.ortserver.model.repositories.NotifierJobRepository
import org.eclipse.apoapsis.ortserver.model.repositories.NotifierRunRepository
import org.eclipse.apoapsis.ortserver.model.repositories.OrtRunRepository
import org.eclipse.apoapsis.ortserver.model.repositories.ReporterJobRepository
import org.eclipse.apoapsis.ortserver.model.repositories.ReporterRunRepository
import org.eclipse.apoapsis.ortserver.model.repositories.RepositoryConfigurationRepository
import org.eclipse.apoapsis.ortserver.model.repositories.RepositoryRepository
import org.eclipse.apoapsis.ortserver.model.repositories.ResolvedConfigurationRepository
import org.eclipse.apoapsis.ortserver.model.repositories.ScannerJobRepository
import org.eclipse.apoapsis.ortserver.model.repositories.ScannerRunRepository
import org.eclipse.apoapsis.ortserver.model.resolvedconfiguration.ResolvedConfiguration
import org.eclipse.apoapsis.ortserver.model.runs.AnalyzerRun
import org.eclipse.apoapsis.ortserver.model.runs.EvaluatorRun
import org.eclipse.apoapsis.ortserver.model.runs.Identifier
import org.eclipse.apoapsis.ortserver.model.runs.Issue
import org.eclipse.apoapsis.ortserver.model.runs.ShortestDependencyPath
import org.eclipse.apoapsis.ortserver.model.runs.advisor.AdvisorRun
import org.eclipse.apoapsis.ortserver.model.runs.notifier.NotifierRun
import org.eclipse.apoapsis.ortserver.model.runs.reporter.ReporterRun
import org.eclipse.apoapsis.ortserver.model.runs.scanner.ProvenanceResolutionResult
import org.eclipse.apoapsis.ortserver.model.runs.scanner.ScanResult
import org.eclipse.apoapsis.ortserver.model.runs.scanner.ScannerRun
import org.eclipse.apoapsis.ortserver.model.util.ListQueryParameters
import org.eclipse.apoapsis.ortserver.model.util.ListQueryResult
import org.eclipse.apoapsis.ortserver.model.util.asPresent
import org.eclipse.apoapsis.ortserver.services.ReportNotFoundException
import org.eclipse.apoapsis.ortserver.services.ReportStorageService
import org.eclipse.apoapsis.ortserver.services.ResourceNotFoundException

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.insert

import org.ossreviewtoolkit.model.FileList
import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.Repository
import org.ossreviewtoolkit.model.ResolvedPackageCurations
import org.ossreviewtoolkit.model.ScanResult as OrtScanResult
import org.ossreviewtoolkit.model.config.PackageConfiguration
import org.ossreviewtoolkit.model.config.RepositoryConfiguration
import org.ossreviewtoolkit.model.config.Resolutions
import org.ossreviewtoolkit.model.utils.getKnownProvenancesWithoutVcsPath
import org.ossreviewtoolkit.scanner.utils.FileListResolver
import org.ossreviewtoolkit.scanner.utils.filterScanResultsByVcsPaths
import org.ossreviewtoolkit.scanner.utils.getVcsPathsForProvenances

import org.slf4j.LoggerFactory

/**
 * A service to interact with ORT runs.
 */
@Suppress("LongParameterList", "TooManyFunctions")
class OrtRunService(
    private val db: Database,
    private val advisorJobRepository: AdvisorJobRepository,
    private val advisorRunRepository: AdvisorRunRepository,
    private val analyzerJobRepository: AnalyzerJobRepository,
    private val analyzerRunRepository: AnalyzerRunRepository,
    private val evaluatorJobRepository: EvaluatorJobRepository,
    private val evaluatorRunRepository: EvaluatorRunRepository,
    private val ortRunRepository: OrtRunRepository,
    private val reporterJobRepository: ReporterJobRepository,
    private val reporterRunRepository: ReporterRunRepository,
    private val notifierJobRepository: NotifierJobRepository,
    private val notifierRunRepository: NotifierRunRepository,
    private val repositoryConfigurationRepository: RepositoryConfigurationRepository,
    private val repositoryRepository: RepositoryRepository,
    private val resolvedConfigurationRepository: ResolvedConfigurationRepository,
    private val scannerJobRepository: ScannerJobRepository,
    private val scannerRunRepository: ScannerRunRepository,
    private val fileListResolver: FileListResolver,
    private val reportStorageService: ReportStorageService
) {
    companion object {
        private const val RUN_ID_LABEL = "runId"
    }

    private val logger = LoggerFactory.getLogger(OrtRunService::class.java)

    suspend fun listOrtRuns(
        parameters: ListQueryParameters = ListQueryParameters.DEFAULT,
        filters: OrtRunFilters? = null
    ): ListQueryResult<OrtRun> = db.dbQuery {
        ortRunRepository.list(parameters, filters)
    }

    /**
     * Delete the ORT run with the [ortRunId] and all its reports from storage and dependent database entities.
     * In case a report does not exist in storage, although it should, the operation continues because
     * the report might have been manually deleted from storage. However, if there is a technical issue
     * during the deletion of a report from storage, the function fails and the ORT run is not deleted,
     * allowing to retry the delete operation.
     */
    suspend fun deleteOrtRun(ortRunId: Long) {
        reporterJobRepository.getForOrtRun(ortRunId)?.filenames?.forEach { filename ->
            runCatching {
                reportStorageService.deleteReport(ortRunId, filename)
            }.onFailure { e ->
                if (e is ReportNotFoundException) {
                    logger.warn("Report $filename for ORT run $ortRunId not found in storage. Continuing.")
                } else {
                    throw e
                }
            }
        }

        if (ortRunRepository.delete(ortRunId) == 0) {
            throw ResourceNotFoundException("ORT run with id '$ortRunId' not found.")
        }
    }

    /**
     * Delete all ORT runs that are older than the given [before] timestamp. Runs are deleted from the database and
     * their reports are deleted from storage.
     */
    suspend fun deleteRunsCreatedBefore(before: Instant) {
        val runIds = ortRunRepository.findRunsBefore(before)

        logger.info("Deleting ${runIds.size} ORT runs older than $before.")

        var failureCount = 0
        runIds.forEach { runId ->
            runCatching {
                deleteOrtRun(runId)
            }.onFailure { failureCount++ }
        }

        logger.info("Deleted ${runIds.size - failureCount} old ORT runs successfully.")
        if (failureCount > 0) {
            logger.warn("Failed to delete $failureCount old ORT runs.")
        }
    }

    /**
     * Create an empty [ScannerRun]. This function is supposed to be called before the ORT scanner is invoked, so that
     * data can be associated to the scanner run while the ORT scanner is running.
     */
    fun createScannerRun(scannerJobId: Long) = db.blockingQuery { scannerRunRepository.create(scannerJobId) }

    /**
     * Finalize the provided scanner run by storing the [ScannerRun.startTime], [ScannerRun.endTime],
     * [ScannerRun.environment], and [ScannerRun.config]. If the scanner run caused [issues], they are stored as well.
     * This function can be called only once for a scanner run and throws an exception if it is called multiple times
     * for the same scanner run.
     */
    fun finalizeScannerRun(scannerRun: ScannerRun, issues: Collection<Issue>) {
        val startTime = requireNotNull(scannerRun.startTime)
        val endTime = requireNotNull(scannerRun.endTime)
        val environment = requireNotNull(scannerRun.environment)
        val config = requireNotNull(scannerRun.config)

        scannerRunRepository.update(
            id = scannerRun.id,
            startTime = startTime,
            endTime = endTime,
            environment = environment,
            config = config,
            scanners = scannerRun.scanners,
            issues = scannerRun.issues
        )

        getScannerJob(scannerRun.scannerJobId)?.also { job ->
            ortRunRepository.update(job.ortRunId, issues = issues.asPresent())
        }
    }

    /**
     * Return the [AdvisorJob] with the provided [id] or `null` if the job does not exist.
     */
    fun getAdvisorJob(id: Long) = db.blockingQuery { advisorJobRepository.get(id) }

    /**
     * Return the [AdvisorJob] for the provided [ortRunId] or `null` if the job does not exist.
     */
    fun getAdvisorJobForOrtRun(ortRunId: Long) = db.blockingQuery { advisorJobRepository.getForOrtRun(ortRunId) }

    /**
     * Return the [AdvisorRun] for the provided [ortRunId] or `null` if the run does not exist.
     */
    fun getAdvisorRunForOrtRun(ortRunId: Long) = db.blockingQuery {
        getAdvisorJobForOrtRun(ortRunId)?.let { advisorRunRepository.getByJobId(it.id) }
    }

    /**
     * Return the [AnalyzerJob] with the provided [id] or `null` if the job does not exist.
     */
    fun getAnalyzerJob(id: Long) = db.blockingQuery { analyzerJobRepository.get(id) }

    /**
     * Return the [AnalyzerJob] for the provided [ortRunId] or `null` if the job does not exist.
     */
    fun getAnalyzerJobForOrtRun(ortRunId: Long) = db.blockingQuery { analyzerJobRepository.getForOrtRun(ortRunId) }

    /**
     * Return the [AnalyzerRun] for the provided [ortRunId] or `null` if the run does not exist.
     */
    fun getAnalyzerRunForOrtRun(ortRunId: Long) = db.blockingQuery {
        getAnalyzerJobForOrtRun(ortRunId)?.let { analyzerRunRepository.getByJobId(it.id) }
    }

    /**
     * Return the [EvaluatorJob] with the provided [id] or `null` if the job does not exist.
     */
    fun getEvaluatorJob(id: Long) = db.blockingQuery { evaluatorJobRepository.get(id) }

    /**
     * Return the [EvaluatorJob] for the provided [ortRunId] or `null` if the job does not exist.
     */
    fun getEvaluatorJobForOrtRun(ortRunId: Long) = db.blockingQuery { evaluatorJobRepository.getForOrtRun(ortRunId) }

    /**
     * Return the [EvaluatorRun] for the provided [ortRunId] or `null` if the run does not exist.
     */
    fun getEvaluatorRunForOrtRun(ortRunId: Long) = db.blockingQuery {
        getEvaluatorJobForOrtRun(ortRunId)?.let { evaluatorRunRepository.getByJobId(it.id) }
    }

    /**
     * Return the download links for the reports of the ORT run with the given [ortRunId].
     */
    fun getDownloadLinksForOrtRun(ortRunId: Long) = db.blockingQuery {
        reporterJobRepository.getNonExpiredReports(ortRunId)
    }

    /**
     * Return the [Hierarchy] for the provided [ortRunId] or `null` if the run does not exist.
     */
    fun getHierarchyForOrtRun(ortRunId: Long) = db.blockingQuery {
        getOrtRun(ortRunId)?.let { repositoryRepository.getHierarchy(it.repositoryId) }
    }

    /**
     * Return the [Repository] information for the provided [OrtRun]. If this information is not available act
     * accordingly based on the [failIfMissing] flag: If it is *true*, throw an exception; otherwise, return an
     * empty [Repository] object.
     */
    fun getOrtRepositoryInformation(ortRun: OrtRun, failIfMissing: Boolean = true) = db.blockingQuery {
        val vcsId = ortRun.vcsId
        val vcsProcessedId = ortRun.vcsProcessedId
        val nestedRepositoryIds = ortRun.nestedRepositoryIds

        @Suppress("ComplexCondition")
        if ((vcsId == null || vcsProcessedId == null || nestedRepositoryIds == null) && !failIfMissing) {
            return@blockingQuery Repository.EMPTY
        }

        requireNotNull(vcsId) {
            "VCS information is missing from ORT run '${ortRun.id}'."
        }

        requireNotNull(vcsProcessedId) {
            "VCS processed information is missing from ORT run '${ortRun.id}'."
        }

        requireNotNull(nestedRepositoryIds) {
            "Nested repositories information is missing from ORT run '${ortRun.id}'."
        }

        val vcsInfo = VcsInfoDao[vcsId].mapToModel()
        val vcsProcessedInfo = VcsInfoDao[vcsProcessedId].mapToModel()
        val nestedRepositories =
            nestedRepositoryIds.map { Pair(it.key, VcsInfoDao[it.value].mapToModel().mapToOrt()) }.toMap()

        val repositoryConfig =
            ortRun.repositoryConfigId?.let { repositoryConfigurationRepository.get(it)?.mapToOrt() }
                ?: RepositoryConfiguration()

        Repository(
            vcs = vcsInfo.mapToOrt(),
            vcsProcessed = vcsProcessedInfo.mapToOrt(),
            nestedRepositories = nestedRepositories,
            config = repositoryConfig
        )
    }

    /**
     * Return the [OrtRun] with the provided [id] or `null` if the ORT run does not exist.
     */
    fun getOrtRun(id: Long) = db.blockingQuery { ortRunRepository.get(id) }

    /**
     * Return the [ReporterJob] with the provided [id] or `null` if the job does not exist.
     */
    fun getReporterJob(id: Long) = db.blockingQuery { reporterJobRepository.get(id) }

    /**
     * Return the [ReporterJob] for the provided [ortRunId] or `null` if the job does not exist.
     */
    fun getReporterJobForOrtRun(ortRunId: Long) = db.blockingQuery { reporterJobRepository.getForOrtRun(ortRunId) }

    /**
     * Return the [ReporterRun] for the provided [ortRunId] or `null` if the run does not exist.
     */
    fun getReporterRunForOrtRun(ortRunId: Long) = db.blockingQuery {
        getReporterJobForOrtRun(ortRunId)?.let { reporterRunRepository.getByJobId(it.id) }
    }

    /**
     * Return the [NotifierJob] for the provided [id] or `null` if the run does not exist.
     */
    fun getNotifierJob(id: Long) = db.blockingQuery { notifierJobRepository.get(id) }

    /**
     * Return the resolved configuration for the provided [ortRun]. If no resolved configuration is stored, an empty
     * resolved configuration is returned.
     */
    fun getResolvedConfiguration(ortRun: OrtRun) = db.blockingQuery {
        resolvedConfigurationRepository.getForOrtRun(ortRun.id) ?: ResolvedConfiguration()
    }

    /**
     * Return the [ScannerJob] with the provided [id] or `null` if the job does not exist.
     */
    fun getScannerJob(id: Long) = db.blockingQuery { scannerJobRepository.get(id) }

    /**
     * Return the [ScannerJob] for the provided [ortRunId] or `null` if the job does not exist.
     */
    fun getScannerJobForOrtRun(ortRunId: Long) = db.blockingQuery { scannerJobRepository.getForOrtRun(ortRunId) }

    /**
     * Return the [ScannerRun] for the provided [ortRunId] or `null` if the run does not exist.
     */
    fun getScannerRunForOrtRun(ortRunId: Long) = db.blockingQuery {
        getScannerJobForOrtRun(ortRunId)?.let { scannerRunRepository.getByJobId(it.id) }
    }

    /**
     * Load the results of the previous worker steps and generate an [OrtResult] from them. In addition,
     * add a number of common labels that can be evaluated by different types of workers to obtain further information
     * about the run.
     *
     * [loadAdvisorRun], [loadScannerRun], and [loadEvaluatorRun] can be set to `false` to skip loading these parts of
     * the ORT result if they are not required. For example, if only vulnerability information is needed, loading the
     * scanner and evaluator runs can be skipped.
     *
     * If [failIfRepoInfoMissing] is *true*, throw an [IllegalArgumentException] if the repository information
     * is missing; otherwise, return an empty [Repository] object.
     */
    fun generateOrtResult(
        ortRun: OrtRun,
        loadAdvisorRun: Boolean = true,
        loadScannerRun: Boolean = true,
        loadEvaluatorRun: Boolean = true,
        failIfRepoInfoMissing: Boolean = true
    ): OrtResult {
        val repository = getOrtRepositoryInformation(ortRun, failIfMissing = failIfRepoInfoMissing)
        val resolvedConfiguration = getResolvedConfiguration(ortRun)
        val analyzerRun = getAnalyzerRunForOrtRun(ortRun.id)
        val advisorRun = if (loadAdvisorRun) getAdvisorRunForOrtRun(ortRun.id) else null
        val scannerRun = if (loadScannerRun) getScannerRunForOrtRun(ortRun.id) else null
        val evaluatorRun = if (loadEvaluatorRun) getEvaluatorRunForOrtRun(ortRun.id) else null

        val filteredOrtScanResults = filterScanResultsByVcsPath(scannerRun?.provenances, scannerRun?.scanResults)

        val provenanceResolutionResults = scannerRun?.provenances?.mapTo(mutableSetOf()) { it.mapToOrt() }.orEmpty()

        val provenancesWithoutVcsPath = provenanceResolutionResults
            .flatMapTo(mutableSetOf()) { it.getKnownProvenancesWithoutVcsPath().values }

        val vcsPathsForProvenances = getVcsPathsForProvenances(provenanceResolutionResults)

        val fileLists = getFileLists(fileListResolver, provenancesWithoutVcsPath)
            .mapNotNullTo(mutableSetOf()) { fileList ->
                vcsPathsForProvenances[fileList.provenance]?.let {
                    fileList.filterByVcsPaths(it)
                }
            }

        val baseResult = ortRun.mapToOrt(
            repository = repository,
            analyzerRun = analyzerRun?.mapToOrt(),
            advisorRun = advisorRun?.mapToOrt(),
            scannerRun = scannerRun?.mapToOrt()?.copy(
                scanResults = filteredOrtScanResults,
                files = fileLists
            ),
            evaluatorRun = evaluatorRun?.mapToOrt(),
            resolvedConfiguration = resolvedConfiguration.mapToOrt()
        )

        return baseResult.copy(
            // Add common labels for all types of workers
            labels = baseResult.labels + mapOf(
                RUN_ID_LABEL to ortRun.id.toString()
            )
        )
    }

    /**
     * Start the [AdvisorJob] with the provided [id] and return the updated job or `null` if the job does not exist.
     */
    fun startAdvisorJob(id: Long) = advisorJobRepository.tryStart(id, Clock.System.now())

    /**
     * Start the [AnalyzerJob] with the provided [id] and return the updated job or `null` if the job does not exist.
     */
    fun startAnalyzerJob(id: Long) = analyzerJobRepository.tryStart(id, Clock.System.now())

    /**
     * Start the [EvaluatorJob] with the provided [id] and return the updated job or `null` if the job does not exist.
     */
    fun startEvaluatorJob(id: Long) = evaluatorJobRepository.tryStart(id, Clock.System.now())

    /**
     * Start the [ReporterJob] with the provided [id] and return the updated job or `null` if the job does not exist.
     */
    fun startReporterJob(id: Long) = reporterJobRepository.tryStart(id, Clock.System.now())

    /**
     * Start the [NotifierJob] with the provided [id] and return the updated job or `null` if the job does not exist.
     */
    fun startNotifierJob(id: Long) = notifierJobRepository.tryStart(id, Clock.System.now())

    /**
     * Start the [ScannerJob] with the provided [id] and return the updated job or `null` if the job does not exist.
     */
    fun startScannerJob(id: Long) = scannerJobRepository.tryStart(id, Clock.System.now())

    /**
     * Store the provided [advisorRun].
     */
    fun storeAdvisorRun(advisorRun: AdvisorRun) {
        advisorRunRepository.create(
            advisorJobId = advisorRun.advisorJobId,
            startTime = advisorRun.startTime,
            endTime = advisorRun.endTime,
            environment = advisorRun.environment,
            config = advisorRun.config,
            results = advisorRun.results
        )
    }

    /**
     * Store the provided [analyzerRun].
     */
    fun storeAnalyzerRun(
        analyzerRun: AnalyzerRun,
        shortestDependencyPaths: Map<Identifier, List<ShortestDependencyPath>> = emptyMap()
    ) {
        analyzerRunRepository.create(
            analyzerJobId = analyzerRun.analyzerJobId,
            startTime = analyzerRun.startTime,
            endTime = analyzerRun.endTime,
            environment = analyzerRun.environment,
            config = analyzerRun.config,
            projects = analyzerRun.projects,
            packages = analyzerRun.packages,
            issues = analyzerRun.issues,
            dependencyGraphs = analyzerRun.dependencyGraphs,
            shortestDependencyPaths = shortestDependencyPaths
        )
    }

    /**
     * Store the provided [evaluatorRun].
     */
    fun storeEvaluatorRun(evaluatorRun: EvaluatorRun) {
        evaluatorRunRepository.create(
            evaluatorRun.evaluatorJobId,
            evaluatorRun.startTime,
            evaluatorRun.endTime,
            evaluatorRun.violations
        )
    }

    /**
     * Store the provided [reporterRun].
     */
    fun storeReporterRun(reporterRun: ReporterRun) {
        reporterRunRepository.create(
            reporterRun.reporterJobId,
            reporterRun.startTime,
            reporterRun.endTime,
            reporterRun.reports
        )
    }

    fun storeNotifierRun(notifierRun: NotifierRun) {
        notifierRunRepository.create(
            notifierRun.notifierJobId,
            notifierRun.startTime,
            notifierRun.endTime
        )
    }

    /**
     * Store the provided [repositoryInformation] associated with the [ortRunId].
     */
    fun storeRepositoryInformation(ortRunId: Long, repositoryInformation: Repository) {
        db.blockingQuery {
            val vcsInfoDao = VcsInfoDao.getOrPut(repositoryInformation.vcs.mapToModel())

            val processedVcsInfoDao = VcsInfoDao.getOrPut(repositoryInformation.vcsProcessed.mapToModel())

            val repositoryConfiguration = repositoryInformation.config.mapToModel(ortRunId)

            repositoryInformation.nestedRepositories.map { nestedRepository ->
                val nestedVcsInfoDao = VcsInfoDao.getOrPut(nestedRepository.value.mapToModel())

                NestedRepositoriesTable.insert {
                    it[this.ortRunId] = ortRunId
                    it[vcsId] = nestedVcsInfoDao.id
                    it[path] = nestedRepository.key
                }
            }

            repositoryConfigurationRepository.create(
                ortRunId = repositoryConfiguration.ortRunId,
                analyzerConfig = repositoryConfiguration.analyzerConfig,
                excludes = repositoryConfiguration.excludes,
                resolutions = repositoryConfiguration.resolutions,
                curations = repositoryConfiguration.curations,
                packageConfigurations = repositoryConfiguration.packageConfigurations,
                licenseChoices = repositoryConfiguration.licenseChoices,
                provenanceSnippetChoices = repositoryConfiguration.provenanceSnippetChoices
            )

            val ortRunDao = OrtRunDao[ortRunId]
            ortRunDao.vcsId = vcsInfoDao.id
            ortRunDao.vcsProcessedId = processedVcsInfoDao.id
        }
    }

    /**
     * Store the provided resolved [packageConfigurations] associated with the [ortRunId].
     */
    fun storeResolvedPackageConfigurations(ortRunId: Long, packageConfigurations: List<PackageConfiguration>) {
        db.blockingQuery {
            resolvedConfigurationRepository.addPackageConfigurations(
                ortRunId,
                packageConfigurations.map { it.mapToModel() }
            )
        }
    }

    /**
     * Store the provided resolved [packageCurations] associated with the [ortRunId].
     */
    fun storeResolvedPackageCurations(ortRunId: Long, packageCurations: List<ResolvedPackageCurations>) {
        db.blockingQuery {
            resolvedConfigurationRepository.addPackageCurations(ortRunId, packageCurations.map { it.mapToModel() })
        }
    }

    /**
     * Store the provided resolved [resolutions] associated with the [ortRunId].
     */
    fun storeResolvedResolutions(ortRunId: Long, resolutions: Resolutions) {
        db.blockingQuery {
            resolvedConfigurationRepository.addResolutions(ortRunId, resolutions.mapToModel())
        }
    }

    /**
     * Store the provided [issues] for the ORT run with the given [ortRunId]. This can be used for issues associated
     * with the run itself, not with any specific job.
     */
    fun storeIssues(ortRunId: Long, issues: List<Issue>) {
        db.blockingQuery {
            ortRunRepository.update(ortRunId, issues = issues.asPresent())
        }
    }

    fun updateRevision(ortRunId: Long, revision: String) {
        db.blockingQuery {
            ortRunRepository.update(ortRunId, revision = revision.asPresent())
        }
    }

    fun updateResolvedRevision(ortRunId: Long, resolvedRevision: String) {
        db.blockingQuery {
            ortRunRepository.update(ortRunId, resolvedRevision = resolvedRevision.asPresent())
        }
    }

    /**
     * Convert [ORT Server ScanResults][ScanResult] to [ORT ScanResults][OrtScanResult] and filter out any
     * results that are not within the VCS paths of the provenances.
     */
    internal fun filterScanResultsByVcsPath(
        provenances: Set<ProvenanceResolutionResult>?, scanResults: Set<ScanResult>?
    ): Set<OrtScanResult> {
        val ortProvenances = provenances?.map { it.mapToOrt() }?.toSet().orEmpty()
        val ortScanResults = scanResults?.map { it.mapToOrt() }.orEmpty()

        val vcsPathsForProvenances = getVcsPathsForProvenances(ortProvenances)

        return filterScanResultsByVcsPaths(ortScanResults, vcsPathsForProvenances)
    }
}

/**
 * Filter the [files][FileList.files] in this [FileList] to only contain files matching the provided [paths].
 */
private fun FileList.filterByVcsPaths(paths: Collection<String>): FileList {
    if (paths.any { it.isBlank() }) return this

    return copy(
        files = files.filterTo(mutableSetOf()) { file ->
            paths.any { file.path.startsWith("$it/") }
        }
    )
}

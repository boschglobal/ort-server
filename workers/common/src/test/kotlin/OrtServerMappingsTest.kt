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

package org.ossreviewtoolkit.server.workers.common

import io.kotest.core.spec.style.WordSpec
import io.kotest.inspectors.forAll
import io.kotest.matchers.shouldBe

import kotlinx.datetime.Instant

import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.server.model.AnalyzerJob
import org.ossreviewtoolkit.server.model.AnalyzerJobConfiguration
import org.ossreviewtoolkit.server.model.JobConfigurations
import org.ossreviewtoolkit.server.model.JobStatus
import org.ossreviewtoolkit.server.model.OrtRun
import org.ossreviewtoolkit.server.model.OrtRunStatus
import org.ossreviewtoolkit.server.model.PluginConfiguration
import org.ossreviewtoolkit.server.model.Repository
import org.ossreviewtoolkit.server.model.RepositoryType
import org.ossreviewtoolkit.server.model.resolvedconfiguration.PackageCurationProviderConfig
import org.ossreviewtoolkit.server.model.resolvedconfiguration.ResolvedConfiguration
import org.ossreviewtoolkit.server.model.resolvedconfiguration.ResolvedPackageCurations
import org.ossreviewtoolkit.server.model.runs.AnalyzerConfiguration
import org.ossreviewtoolkit.server.model.runs.AnalyzerRun
import org.ossreviewtoolkit.server.model.runs.DependencyGraph
import org.ossreviewtoolkit.server.model.runs.DependencyGraphNode
import org.ossreviewtoolkit.server.model.runs.DependencyGraphRoot
import org.ossreviewtoolkit.server.model.runs.Environment
import org.ossreviewtoolkit.server.model.runs.Identifier
import org.ossreviewtoolkit.server.model.runs.OrtIssue as OrtServerIssue
import org.ossreviewtoolkit.server.model.runs.Package
import org.ossreviewtoolkit.server.model.runs.PackageManagerConfiguration
import org.ossreviewtoolkit.server.model.runs.ProcessedDeclaredLicense
import org.ossreviewtoolkit.server.model.runs.Project
import org.ossreviewtoolkit.server.model.runs.RemoteArtifact
import org.ossreviewtoolkit.server.model.runs.VcsInfo
import org.ossreviewtoolkit.server.model.runs.advisor.AdvisorConfiguration
import org.ossreviewtoolkit.server.model.runs.advisor.AdvisorResult
import org.ossreviewtoolkit.server.model.runs.advisor.AdvisorRun
import org.ossreviewtoolkit.server.model.runs.advisor.Vulnerability
import org.ossreviewtoolkit.server.model.runs.advisor.VulnerabilityReference
import org.ossreviewtoolkit.server.model.runs.repository.Curations
import org.ossreviewtoolkit.server.model.runs.repository.Excludes
import org.ossreviewtoolkit.server.model.runs.repository.IssueResolution
import org.ossreviewtoolkit.server.model.runs.repository.LicenseChoices
import org.ossreviewtoolkit.server.model.runs.repository.LicenseFindingCuration
import org.ossreviewtoolkit.server.model.runs.repository.PackageConfiguration
import org.ossreviewtoolkit.server.model.runs.repository.PackageCuration
import org.ossreviewtoolkit.server.model.runs.repository.PackageCurationData
import org.ossreviewtoolkit.server.model.runs.repository.PackageLicenseChoice
import org.ossreviewtoolkit.server.model.runs.repository.PathExclude
import org.ossreviewtoolkit.server.model.runs.repository.RepositoryAnalyzerConfiguration
import org.ossreviewtoolkit.server.model.runs.repository.RepositoryConfiguration
import org.ossreviewtoolkit.server.model.runs.repository.Resolutions
import org.ossreviewtoolkit.server.model.runs.repository.RuleViolationResolution
import org.ossreviewtoolkit.server.model.runs.repository.ScopeExclude
import org.ossreviewtoolkit.server.model.runs.repository.SpdxLicenseChoice
import org.ossreviewtoolkit.server.model.runs.repository.VcsInfoCurationData
import org.ossreviewtoolkit.server.model.runs.repository.VulnerabilityResolution
import org.ossreviewtoolkit.server.model.runs.scanner.ArtifactProvenance
import org.ossreviewtoolkit.server.model.runs.scanner.FileArchiveConfiguration
import org.ossreviewtoolkit.server.model.runs.scanner.FileBasedStorageConfiguration
import org.ossreviewtoolkit.server.model.runs.scanner.FileStorageConfiguration
import org.ossreviewtoolkit.server.model.runs.scanner.LicenseFinding
import org.ossreviewtoolkit.server.model.runs.scanner.LocalFileStorageConfiguration
import org.ossreviewtoolkit.server.model.runs.scanner.ProvenanceResolutionResult
import org.ossreviewtoolkit.server.model.runs.scanner.ProvenanceStorageConfiguration
import org.ossreviewtoolkit.server.model.runs.scanner.ScanResult
import org.ossreviewtoolkit.server.model.runs.scanner.ScanSummary
import org.ossreviewtoolkit.server.model.runs.scanner.ScannerConfiguration
import org.ossreviewtoolkit.server.model.runs.scanner.ScannerDetail
import org.ossreviewtoolkit.server.model.runs.scanner.ScannerRun
import org.ossreviewtoolkit.server.model.runs.scanner.TextLocation

private const val TIME_STAMP_SECONDS = 1678119934L

class OrtServerMappingsTest : WordSpec({
    "OrtServerMappings" should {
        "map ORT server objects to ORT objects" {
            // Initialization of ORT server objects.
            val repository = Repository(
                id = 1L,
                organizationId = 1L,
                productId = 1L,
                type = RepositoryType.GIT,
                url = OrtTestData.projectProcessedRepositoryUrl
            )

            val environment = Environment(
                os = "Linux",
                ortVersion = "def456",
                javaVersion = "17",
                processors = 8,
                maxMemory = 12884901888L,
                variables = mapOf("JAVA_HOME" to "/opt/java/openjdk"),
                toolVersions = emptyMap()
            )

            val runIssue = OrtServerIssue(
                timestamp = Instant.parse("2023-08-02T07:59:38Z"),
                source = "test-tool",
                message = "Some problem with this run",
                severity = "WARN"
            )

            val ortRun = OrtRun(
                id = 1L,
                index = 1L,
                repositoryId = repository.id,
                revision = OrtTestData.projectRevision,
                createdAt = Instant.fromEpochSeconds(TIME_STAMP_SECONDS),
                finishedAt = null,
                jobConfigs = JobConfigurations(),
                resolvedJobConfigs = JobConfigurations(),
                status = OrtRunStatus.CREATED,
                mapOf("label key" to "label value"),
                null,
                null,
                emptyMap(),
                null,
                listOf(runIssue),
                "default",
                "c80ef3bcd2bec428da923a188dd0870b1153995c"
            )

            val analyzerJob = AnalyzerJob(
                id = 1L,
                ortRunId = ortRun.id,
                createdAt = Instant.fromEpochSeconds(TIME_STAMP_SECONDS),
                startedAt = Instant.fromEpochSeconds(TIME_STAMP_SECONDS),
                finishedAt = Instant.fromEpochSeconds(TIME_STAMP_SECONDS),
                configuration = AnalyzerJobConfiguration(),
                status = JobStatus.FINISHED
            )

            val analyzerConfiguration = AnalyzerConfiguration(
                allowDynamicVersions = false,
                enabledPackageManagers = listOf("Maven", "Gradle"),
                disabledPackageManagers = listOf("NPM"),
                packageManagers = mapOf(
                    "DotNet" to PackageManagerConfiguration(options = mapOf("directDependenciesOnly" to "true")),
                    "NPM" to PackageManagerConfiguration(options = mapOf("legacyPeerDeps" to "true")),
                    "NuGet" to PackageManagerConfiguration(options = mapOf("directDependenciesOnly" to "true"))
                )
            )

            val project = Project(
                identifier = Identifier(
                    type = "Maven",
                    namespace = "com.example",
                    name = "project",
                    version = "1.0"
                ),
                cpe = "cpe:example",
                definitionFilePath = "pom.xml",
                authors = setOf("Author One", "Author Two"),
                declaredLicenses = setOf("The MIT License", "Eclipse Public License 1.0"),
                vcs = VcsInfo(
                    type = RepositoryType.GIT,
                    url = OrtTestData.projectRepositoryUrl,
                    revision = "",
                    path = ""
                ),
                vcsProcessed = VcsInfo(
                    type = RepositoryType.GIT,
                    url = OrtTestData.projectProcessedRepositoryUrl,
                    revision = OrtTestData.projectRevision,
                    path = ""
                ),
                homepageUrl = "https://example.org/project",
                scopeNames = setOf("compile")
            )

            val pkgIdentifier = Identifier(
                type = "Maven",
                namespace = "com.example",
                name = "package",
                version = "1.0"
            )

            val pkg = Package(
                identifier = pkgIdentifier,
                purl = "Maven:com.example:package:1.0",
                cpe = "cpe:example",
                authors = setOf("Author One", "Author Two"),
                declaredLicenses = setOf(
                    "LicenseRef-declared",
                    "LicenseRef-toBeMapped1",
                    "LicenseRef-toBeMapped2",
                    "LicenseRef-unmapped1",
                    "LicenseRef-unmapped2"
                ),
                processedDeclaredLicense = ProcessedDeclaredLicense(
                    spdxExpression = "LicenseRef-declared OR LicenseRef-mapped1 OR LicenseRef-mapped2",
                    mappedLicenses = mapOf(
                        "LicenseRef-toBeMapped1" to "LicenseRef-mapped1",
                        "LicenseRef-toBeMapped2" to "LicenseRef-mapped2"
                    ),
                    unmappedLicenses = setOf("LicenseRef-unmapped1", "LicenseRef-unmapped2")
                ),
                description = "Example description",
                homepageUrl = "https://example.org/package",
                binaryArtifact = RemoteArtifact(
                    url = OrtTestData.pkgBinaryArtifactUrl,
                    hashValue = "123456",
                    hashAlgorithm = "SHA-1"
                ),
                sourceArtifact = RemoteArtifact(
                    url = OrtTestData.pkgSourceArtifactUrl,
                    hashValue = "654321",
                    hashAlgorithm = "SHA-1"
                ),
                vcs = VcsInfo(
                    type = RepositoryType.GIT,
                    url = OrtTestData.pkgRepositoryUrl,
                    revision = OrtTestData.pkgRevision,
                    path = ""
                ),
                vcsProcessed = VcsInfo(
                    type = RepositoryType.GIT,
                    url = OrtTestData.pkgProcessedRepositoryUrl,
                    revision = OrtTestData.pkgRevision,
                    path = ""
                ),
                isMetadataOnly = true,
                isModified = true
            )

            val issue = OrtServerIssue(
                timestamp = Instant.fromEpochSeconds(TIME_STAMP_SECONDS),
                source = "tool-x",
                message = "An issue occurred.",
                severity = "ERROR"
            )

            val dependencyGraph = DependencyGraph(
                packages = listOf(pkgIdentifier),
                nodes = listOf(DependencyGraphNode(0, 0, "DYNAMIC", emptyList())),
                edges = emptyList(),
                scopes = mapOf(
                    "com.example:project:1.0:compile" to listOf(DependencyGraphRoot(0, 0))
                )
            )

            val analyzerRun = AnalyzerRun(
                id = 1L,
                analyzerJobId = analyzerJob.id,
                startTime = Instant.fromEpochSeconds(TIME_STAMP_SECONDS),
                endTime = Instant.fromEpochSeconds(TIME_STAMP_SECONDS),
                environment = environment,
                config = analyzerConfiguration,
                projects = setOf(project),
                packages = setOf(pkg),
                issues = mapOf(pkgIdentifier to listOf(issue)),
                dependencyGraphs = mapOf("Maven" to dependencyGraph)
            )

            val advisorConfiguration = AdvisorConfiguration(
                config = mapOf(
                    "GitHubDefects" to PluginConfiguration(
                        options = mapOf(
                            "endpointUrl" to "https://github.com/defects",
                            "labelFilter" to "!any",
                            "maxNumberOfIssuesPerRepository" to "5",
                            "parallelRequests" to "2"
                        ),
                        secrets = mapOf("token" to "tokenValue")
                    ),
                    "NexusIQ" to PluginConfiguration(
                        options = mapOf(
                            "serverUrl" to "https://example.org/nexus",
                            "browseUrl" to "https://example.org/nexus/browse"
                        ),
                        secrets = mapOf(
                            "username" to "user",
                            "password" to "pass"
                        )
                    ),
                    "OSV" to PluginConfiguration(
                        options = mapOf("serverUrl" to "https://google.com/osv"),
                        secrets = emptyMap()
                    ),
                    "VulnerableCode" to PluginConfiguration(
                        options = mapOf("serverUrl" to "https://public.vulnerablecode.io"),
                        secrets = mapOf("apiKey" to "key")
                    )
                )
            )

            val vulnerability = Vulnerability(
                externalId = "CVE-2023-0001",
                summary = "Example summary.",
                description = "Example description.",
                references = listOf(
                    VulnerabilityReference(
                        url = "http://cve.example.org",
                        scoringSystem = "CVSS3",
                        severity = "5.5"
                    )
                )
            )

            val advisorResult = AdvisorResult(
                advisorName = "VulnerableCode",
                capabilities = listOf("VULNERABILITIES"),
                startTime = Instant.fromEpochSeconds(TIME_STAMP_SECONDS),
                endTime = Instant.fromEpochSeconds(TIME_STAMP_SECONDS),
                issues = listOf(issue),
                defects = emptyList(),
                vulnerabilities = listOf(vulnerability)

            )

            val advisorRun = AdvisorRun(
                id = 1L,
                advisorJobId = 1L,
                startTime = Instant.fromEpochSeconds(TIME_STAMP_SECONDS),
                endTime = Instant.fromEpochSeconds(TIME_STAMP_SECONDS),
                environment = environment,
                config = advisorConfiguration,
                advisorRecords = mapOf(pkgIdentifier to listOf(advisorResult))
            )

            val fileStorageConfiguration = FileStorageConfiguration(
                localFileStorage = LocalFileStorageConfiguration(
                    directory = "/path/to/storage",
                    compression = true
                )
            )

            val scannerConfiguration = ScannerConfiguration(
                skipConcluded = true,
                archive = FileArchiveConfiguration(
                    enabled = true,
                    fileStorage = fileStorageConfiguration
                ),
                createMissingArchives = true,
                detectedLicenseMappings = mapOf("license-1" to "spdx-license-1", "license-2" to "spdx-license-2"),
                config = mapOf(
                    "scanner-1" to PluginConfiguration(
                        options = mapOf("option-key-1" to "option-value-1"),
                        secrets = mapOf("secret-key-1" to "secret-value-1")
                    ),
                    "scanner-2" to PluginConfiguration(
                        options = mapOf("option-key-1" to "option-value-1", "option-key-2" to "option-value-2"),
                        secrets = mapOf("secret-key-1" to "secret-value-1", "secret-key-2" to "secret-value-2")
                    )
                ),
                storages = mapOf(
                    "local" to FileBasedStorageConfiguration(fileStorageConfiguration, "PROVENANCE_BASED")
                ),
                storageReaders = listOf("reader-1", "reader-2"),
                storageWriters = listOf("writer-1", "writer-2"),
                ignorePatterns = listOf("pattern-1", "pattern-2"),
                provenanceStorage = ProvenanceStorageConfiguration(
                    fileStorage = fileStorageConfiguration
                )
            )

            val packageCuration = PackageCuration(
                id = pkgIdentifier,
                data = PackageCurationData(
                    comment = "comment",
                    purl = "purl",
                    cpe = "cpe",
                    authors = setOf("author 1", "author 2"),
                    concludedLicense = "LicenseRef-concluded",
                    description = "description",
                    homepageUrl = "https://example.org/package-curated",
                    binaryArtifact = RemoteArtifact(
                        url = OrtTestData.pkgCuratedBinaryArtifactUrl,
                        hashValue = "0123456789abcdef0123456789abcdef01234567",
                        hashAlgorithm = "SHA-1"
                    ),
                    sourceArtifact = RemoteArtifact(
                        url = OrtTestData.pkgCuratedSourceArtifactUrl,
                        hashValue = "0123456789abcdef0123456789abcdef01234567",
                        hashAlgorithm = "SHA-1"
                    ),
                    vcs = VcsInfoCurationData(
                        type = RepositoryType.GIT,
                        url = OrtTestData.pkgCuratedRepositoryUrl,
                        revision = OrtTestData.pkgCuratedRevision,
                        path = OrtTestData.pkgCuratedPath
                    ),
                    isMetadataOnly = false,
                    isModified = false,
                    declaredLicenseMapping = mapOf(
                        "LicenseRef-toBeMapped1" to "LicenseRef-mapped1",
                        "LicenseRef-toBeMapped2" to "LicenseRef-mapped2"
                    )
                )
            )

            val artifactProvenance = ArtifactProvenance(packageCuration.data.sourceArtifact!!)

            val provenanceResolutionResult = ProvenanceResolutionResult(
                id = pkgIdentifier,
                packageProvenance = artifactProvenance
            )

            val scanResult = ScanResult(
                provenance = artifactProvenance,
                scanner = ScannerDetail(
                    name = "ScanCode",
                    version = "version",
                    configuration = "configuration"
                ),
                summary = ScanSummary(
                    startTime = Instant.fromEpochSeconds(TIME_STAMP_SECONDS),
                    endTime = Instant.fromEpochSeconds(TIME_STAMP_SECONDS),
                    licenseFindings = setOf(
                        LicenseFinding(
                            spdxLicense = "LicenseRef-detected-excluded",
                            location = TextLocation(path = "excluded/file", startLine = 1, endLine = 2)
                        ),
                        LicenseFinding(
                            spdxLicense = "LicenseRef-detected1",
                            location = TextLocation(path = "file1", startLine = 1, endLine = 2)
                        ),
                        LicenseFinding(
                            spdxLicense = "LicenseRef-detected2",
                            location = TextLocation(path = "file2", startLine = 1, endLine = 2)
                        ),
                        LicenseFinding(
                            spdxLicense = "LicenseRef-detected3",
                            location = TextLocation(path = "file3", startLine = 1, endLine = 2)
                        )
                    ),
                    copyrightFindings = emptySet(),
                    snippetFindings = emptySet(),
                    issues = listOf(issue)
                ),
                additionalData = mapOf("data-1" to "value-1")
            )

            val scannerRun = ScannerRun(
                id = 1L,
                scannerJobId = 1L,
                startTime = Instant.fromEpochSeconds(TIME_STAMP_SECONDS),
                endTime = Instant.fromEpochSeconds(TIME_STAMP_SECONDS),
                environment = environment,
                config = scannerConfiguration,
                provenances = setOf(provenanceResolutionResult),
                scanResults = setOf(scanResult)
            )

            val pathExclude = PathExclude(
                pattern = "excluded/**",
                reason = "EXAMPLE_OF",
                comment = "Test path exclude."
            )

            val licenseFindingCuration = LicenseFindingCuration(
                path = "file1",
                startLines = listOf(1),
                lineCount = 2,
                detectedLicense = "LicenseRef-detected1",
                concludedLicense = "LicenseRef-detected1-concluded",
                reason = "INCORRECT",
                comment = "Test license finding curation."
            )

            val spdxLicenseChoice = SpdxLicenseChoice(
                given = "LicenseRef-a OR LicenseRef-b",
                choice = "LicenseRef-b"
            )

            val repositoryConfig = RepositoryConfiguration(
                id = 1L,
                ortRunId = ortRun.id,
                analyzerConfig = RepositoryAnalyzerConfiguration(
                    allowDynamicVersions = true,
                    enabledPackageManagers = listOf("Gradle", "Maven"),
                    disabledPackageManagers = listOf("NPM"),
                    packageManagers = mapOf("Gradle" to PackageManagerConfiguration(listOf("Maven"))),
                    skipExcluded = true
                ),
                excludes = Excludes(
                    paths = listOf(pathExclude),
                    scopes = listOf(ScopeExclude("test", "TEST_DEPENDENCY_OF", "Test scope exclude."))
                ),
                resolutions = Resolutions(
                    issues = listOf(
                        IssueResolution(
                            message = issue.message,
                            reason = "SCANNER_ISSUE",
                            comment = "Test issue resolution."
                        )
                    ),
                    ruleViolations = listOf(
                        RuleViolationResolution(
                            message = ".*",
                            reason = "EXAMPLE_OF_EXCEPTION",
                            comment = "Test rule violation resolution."
                        )
                    ),
                    vulnerabilities = listOf(
                        VulnerabilityResolution(
                            externalId = vulnerability.externalId,
                            reason = "INEFFECTIVE_VULNERABILITY",
                            comment = "Test vulnerability resolution."
                        )
                    )
                ),
                curations = Curations(
                    packages = listOf(packageCuration),
                    licenseFindings = listOf(licenseFindingCuration)
                ),
                packageConfigurations = listOf(
                    PackageConfiguration(
                        id = pkgIdentifier,
                        sourceArtifactUrl = OrtTestData.pkgCuratedSourceArtifactUrl,
                        pathExcludes = listOf(pathExclude),
                        licenseFindingCurations = listOf(licenseFindingCuration)
                    )
                ),
                licenseChoices = LicenseChoices(
                    repositoryLicenseChoices = listOf(spdxLicenseChoice),
                    packageLicenseChoices = listOf(
                        PackageLicenseChoice(
                            identifier = pkgIdentifier,
                            licenseChoices = listOf(spdxLicenseChoice)
                        )
                    )
                )
            )

            val resolvedConfiguration = ResolvedConfiguration(
                packageConfigurations = listOf(
                    PackageConfiguration(
                        id = pkgIdentifier,
                        sourceArtifactUrl = OrtTestData.pkgCuratedSourceArtifactUrl,
                        pathExcludes = listOf(pathExclude),
                        licenseFindingCurations = listOf(licenseFindingCuration)
                    )
                ),
                packageCurations = listOf(
                    ResolvedPackageCurations(
                        provider = PackageCurationProviderConfig(name = "RepositoryConfiguration"),
                        curations = listOf(packageCuration)
                    )
                ),
                resolutions = Resolutions(
                    issues = listOf(
                        IssueResolution(
                            message = issue.message,
                            reason = "CANT_FIX_ISSUE",
                            comment = "Test issue resolution."
                        )
                    ),
                    ruleViolations = listOf(
                        RuleViolationResolution(
                            message = ".*",
                            reason = "CANT_FIX_EXCEPTION",
                            comment = "Test rule violation resolution."
                        )
                    ),
                    vulnerabilities = listOf(
                        VulnerabilityResolution(
                            externalId = vulnerability.externalId,
                            reason = "CANT_FIX_VULNERABILITY",
                            comment = "Test vulnerability resolution."
                        )
                    )
                )
            )

            val mappedOrtResult = ortRun.mapToOrt(
                repository = repository.mapToOrt(
                    revision = ortRun.revision,
                    repositoryConfig = repositoryConfig.mapToOrt()
                ),
                analyzerRun = analyzerRun.mapToOrt(),
                advisorRun = advisorRun.mapToOrt(),
                scannerRun = scannerRun.mapToOrt(),
                resolvedConfiguration = resolvedConfiguration.mapToOrt()
            )

            mappedOrtResult shouldBe OrtTestData.result
        }
    }

    "VcsType" should {
        "be mapped to a known RepositoryType" {
            val vcsTypes = listOf(VcsType.GIT, VcsType.GIT_REPO, VcsType.MERCURIAL, VcsType.SUBVERSION, VcsType.UNKNOWN)
            val repositoryTypes = listOf(
                RepositoryType.GIT,
                RepositoryType.GIT_REPO,
                RepositoryType.MERCURIAL,
                RepositoryType.SUBVERSION,
                RepositoryType.UNKNOWN
            )

            vcsTypes.zip(repositoryTypes).forAll { (vcsType, repositoryType) ->
                vcsType.mapToModel() shouldBe repositoryType
            }
        }

        "be mapped to a custom RepositoryType" {
            val name = "http"
            val vcsType = VcsType.forName(name)

            val repositoryType = vcsType.mapToModel()

            repositoryType.name shouldBe name
        }
    }
})

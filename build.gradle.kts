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

import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask

import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

val dockerBaseBuildArgs: String by project
val dockerBaseImageTag: String by project

plugins {
    alias(libs.plugins.dependencyAnalysis)
    alias(libs.plugins.gitSemver)
    alias(libs.plugins.jib) apply false
    alias(libs.plugins.versions)
}

semver {
    // Do not create an empty release commit when running the "releaseVersion" task.
    createReleaseCommit = false

    // Do not let untracked files bump the version or add a "-SNAPSHOT" suffix.
    noDirtyCheck = true
}

// Only override a default version (which usually is "unspecified"), but not a custom version.
if (version == Project.DEFAULT_VERSION) {
    version = semver.semVersion.takeIf { it.isPreRelease }
        // To get rid of a build part's "+" prefix because Docker tags do not support it, use only the original "build"
        // part as the "pre-release" part.
        ?.toString()?.replace("${semver.defaultPreRelease}+", "")
        // Fall back to a plain version without pre-release or build parts.
        ?: semver.version
}

logger.lifecycle("Building ORT Server version $version.")

tasks.named<DependencyUpdatesTask>("dependencyUpdates").configure {
    gradleReleaseChannel = "current"
    outputFormatter = "json"

    val nonFinalQualifiers = listOf(
        "alpha", "b", "beta", "cr", "dev", "ea", "eap", "m", "milestone", "pr", "preview", "rc", "\\d{14}"
    ).joinToString("|", "(", ")")

    val nonFinalQualifiersRegex = Regex(".*[.-]$nonFinalQualifiers[.\\d-+]*", RegexOption.IGNORE_CASE)

    rejectVersionIf {
        candidate.version.matches(nonFinalQualifiersRegex)
    }
}

// Gradle's "dependencies" task selector only executes on a single / the current project [1]. However, sometimes viewing
// all dependencies at once is beneficial, e.g. for debugging version conflict resolution.
// [1]: https://docs.gradle.org/current/userguide/viewing_debugging_dependencies.html#sec:listing_dependencies
tasks.register("allDependencies") {
    group = "Help"
    description = "Displays all dependencies declared in all projects."

    val dependenciesTasks = getTasksByName("dependencies", /* recursive = */ true).sorted()
    dependsOn(dependenciesTasks)

    // Ensure deterministic output by requiring to run tasks after each other in always the same order.
    dependenciesTasks.zipWithNext().forEach { (a, b) ->
        b.mustRunAfter(a)
    }
}

rootDir.walk().maxDepth(4).filter { it.isFile && it.extension == "Dockerfile" }.forEach { dockerfile ->
    val name = dockerfile.name.substringBeforeLast('.')

    val buildArgs = dockerBaseBuildArgs.takeUnless { it.isBlank() }
        ?.split(',')
        ?.flatMap { listOf("--build-arg", it.trim()) }
        .orEmpty()

    if (name == "UI") {
        tasks.register<Exec>("build${name}Image") {
            val context = dockerfile.parentFile.parent

            dependsOn(":core:generateOpenApiSpec")

            group = "Docker"
            description = "Builds the $name Docker image."

            inputs.file(dockerfile)
            inputs.dir(context)

            commandLine = listOf(
                "docker", "build",
                "-f", dockerfile.path,
                *buildArgs.toTypedArray(),
                "-t", "ort-server-${name.lowercase()}:$dockerBaseImageTag",
                "-q",
                context
            )
        }
    } else {
        tasks.register<Exec>("build${name}WorkerImage") {
            val context = dockerfile.parent

            group = "Docker"
            description = "Builds the $name worker Docker image."

            inputs.file(dockerfile)
            inputs.dir(context)

            commandLine = listOf(
                "docker", "build",
                "-f", dockerfile.path,
                *buildArgs.toTypedArray(),
                "-t", "ort-server-${name.lowercase()}-worker-base-image:$dockerBaseImageTag",
                "-q",
                context
            )
        }
    }
}

val buildAllWorkerImages by tasks.registering {
    group = "Docker"
    description = "Builds all worker Docker images."

    val workerImageTaskRegex = Regex("build[A-Z][a-z]+WorkerImage")
    val workerImageTasks = tasks.matching { it.name.matches(workerImageTaskRegex) }
    dependsOn(workerImageTasks)
}

tasks.register("buildAllImages") {
    group = "Docker"
    description = "Builds all Docker images for the backend and frontend."

    val jibDockerBuilds = getTasksByName("jibDockerBuild", /* recursive = */ true).onEach {
        it.mustRunAfter(buildAllWorkerImages)
    }

    val uiDockerBuild = getTasksByName("buildUIImage", /* recursive = */ false).single()

    dependsOn(buildAllWorkerImages, jibDockerBuilds, uiDockerBuild)
}

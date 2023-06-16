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

package org.ossreviewtoolkit.server.workers.common.env

import java.io.File
import java.net.URI

import org.ossreviewtoolkit.server.model.InfrastructureService
import org.ossreviewtoolkit.server.workers.common.context.WorkerContext

import org.slf4j.LoggerFactory

/**
 * A specialized generator class to generate the content of the .netrc file.
 *
 * This generator class produces an entry for the _.netrc_ file for each [InfrastructureService] it is provided. See
 * https://daniel.haxx.se/blog/2022/05/31/netrc-pains/ for a discussion of the format and its limitations. The file
 * written by this class is intended to be read by ORT's _NetRcAuthenticator_ class. As this class does not support
 * any advanced features (e.g. special characters in passwords, quoting, or escaping), those aspects are ignored here
 * as well.
 *
 * For the _.netrc_ file, only the host names of machines are relevant. In case there are multiple infrastructure
 * services defined with URLs pointing to the same host, the credentials of the first one are used; so the order in
 * which services are passed is relevant. (Typically, infrastructure services are defined in configuration files
 * located in the repositories to be analyzed. Hence, it is possible for users to change the order in which the
 * services are listed as necessary.)
 *
 * TODO: Define a common interface for all kinds of generators.
 */
class NetRcGenerator {
    companion object {
        /** The name of the file to be generated. */
        private const val TARGET_NAME = ".netrc"

        private val logger = LoggerFactory.getLogger(NetRcGenerator::class.java)

        /**
         * Obtain the host name of this [InfrastructureService] from its URL or *null* if the URL is not valid.
         */
        private fun InfrastructureService.host(): String? =
            runCatching {
                URI(url).host
            }.onFailure {
                logger.error("Could not extract host for service '{}'. Ignoring it.", this, it)
            }.getOrNull()
    }

    /**
     * Return a [File] where to store the data produced by this generator.
     */
    fun targetFile(): File = File(System.getProperty("user.home"), TARGET_NAME)

    /**
     * Generate the lines of the target file based on the given [context] and list of [infrastructureServices].
     */
    suspend fun generate(
        context: WorkerContext,
        infrastructureServices: Collection<InfrastructureService>
    ): Iterable<String> {
        val serviceHosts = infrastructureServices.map { it to it.host() }
            .filter { it.second != null }
            .toMap()
        val deDuplicatedServices = serviceHosts.keys.groupBy { serviceHosts.getValue(it) }
            .values
            .map { it.first() }

        val allSecrets = deDuplicatedServices.flatMap { listOf(it.usernameSecret, it.passwordSecret) }
        val secretValues = context.resolveSecrets(*allSecrets.toTypedArray())

        return deDuplicatedServices.map { service ->
            val host = URI(service.url).host
            val username = secretValues.getValue(service.usernameSecret)
            val password = secretValues.getValue(service.passwordSecret)

            "machine $host login $username password $password"
        }
    }
}
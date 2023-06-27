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

import org.ossreviewtoolkit.server.workers.common.env.definition.EnvironmentServiceDefinition

/**
 * A common interface for generators for package manager-specific configuration files.
 *
 * A generator can process [EnvironmentServiceDefinition]s of a specific type. Based on a collection of such
 * definitions, it can create a concrete configuration file.
 */
interface EnvironmentConfigGenerator<T : EnvironmentServiceDefinition> {
    /**
     * The class for the environment definitions supported by this generator. Based on this type, a filtering for the
     * relevant definitions can be done.
     */
    val environmentDefinitionType: Class<T>

    /**
     * Generate a configuration file for the given [definitions] with the help of the given [builder].
     */
    suspend fun generate(builder: ConfigFileBuilder, definitions: Collection<T>)

    /**
     * Generate a configuration file for the supported definition types in the provided [definitions] with the help of
     * the given [builder].
     */
    suspend fun generateApplicable(builder: ConfigFileBuilder, definitions: Collection<EnvironmentServiceDefinition>) {
        generate(builder, definitions.filterIsInstance(environmentDefinitionType))
    }
}
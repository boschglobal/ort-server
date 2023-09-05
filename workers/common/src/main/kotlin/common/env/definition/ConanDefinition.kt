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

package org.ossreviewtoolkit.server.workers.common.env.definition

import org.ossreviewtoolkit.server.model.InfrastructureService

/**
 * A specific [EnvironmentServiceDefinition] class for generating the Conan _remotes.json_ file with configuration
 * specific to package sources.
 *
 * See: https://docs.conan.io/2.0/reference/config_files/remotes.html
 */
class ConanDefinition(
    service: InfrastructureService,

    /**
     * Name of the remote. This name will be used in commands like `conan list`.
     */
    val name: String,

    /**
     * Indicates the URL to be used by Conan to search for the recipes/binaries.
     */
    val url: String,

    /**
     * Verify SSL certificate of the specified url.
     */
    val verifySsl: Boolean
) : EnvironmentServiceDefinition(service)
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

package org.ossreviewtoolkit.server.logaccess

import org.ossreviewtoolkit.server.config.ConfigManager

/**
 * A factory interface for creating [LogFileProvider] instances.
 */
interface LogFileProviderFactory {
    /**
     * The name of the [LogFileProvider] implementation. This is used to load a specific factory from the classpath.
     */
    val name: String

    /**
     * Create a new [LogFileProvider] instance that is fully initialized to download log files from the supported
     * external logging system. Obtain the required configuration information from the given [config].
     */
    fun createProvider(config: ConfigManager): LogFileProvider
}
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

package org.ossreviewtoolkit.server.config.secret.file

import com.typesafe.config.Config

import java.io.File

import org.ossreviewtoolkit.server.config.ConfigSecretProvider
import org.ossreviewtoolkit.server.config.ConfigSecretProviderFactory

/**
 * Factory implementation for [ConfigSecretFileProvider].
 */
class ConfigSecretFileProviderFactory : ConfigSecretProviderFactory {
    companion object {
        /** The name of this provider implementation. */
        const val NAME = "secret-file"

        /**
         * The name of the configuration property defining the list of secret files. The value must be a
         * comma-delimited list with the paths to the files containing the secret values.
         */
        private const val FILES_PROPERTY = "configSecretFileList"

        /** Regular expression to split the property with the list of files. */
        private val splitFilesRegex = Regex("""\s*,\s*""")
    }

    override val name: String
        get() = NAME

    override fun createProvider(config: Config): ConfigSecretProvider {
        val files = config.getString(FILES_PROPERTY).split(splitFilesRegex).map(::File)

        return ConfigSecretFileProvider(files)
    }
}
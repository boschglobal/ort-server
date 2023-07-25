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

package org.ossreviewtoolkit.server.config

import com.typesafe.config.Config

import java.io.InputStream
import java.util.ServiceLoader

import org.ossreviewtoolkit.server.utils.config.getStringOrNull

/**
 * A class wrapping different configuration service provider implementations simplifying the interaction with them.
 *
 * An instance of this class can be created via the [create] factory function. It loads the configured provider
 * implementations from the classpath and initializes them. It then provides a richer interface on top of these
 * provider interfaces. It also handles occurring exceptions and wraps them into [ConfigException] exceptions.
 */
class ConfigManager(
    /** The [Context] used by this instance. */
    val context: Context,

    /** The provider for configuration files. */
    private val configFileProvider: ConfigFileProvider
) {
    companion object {
        /**
         * The name of the section in the configuration that contains the settings evaluated by this class.
         */
        const val CONFIG_MANAGER_SECTION = "configManager"

        /**
         * The name of the configuration property that defines the name of the config file provider implementation.
         */
        const val FILE_PROVIDER_NAME_PROPERTY = "fileProvider"

        /** The service loader for file provider factories. */
        private val FILE_PROVIDER_LOADER = ServiceLoader.load(ConfigFileProviderFactory::class.java)

        /**
         * Return a new instance of [ConfigManager] that has been initialized with service provider implementations
         * defined by the given [config]. Configuration files are resolved from the given [context].
         * [Optionally][resolveContext] the context can be resolved first; all later invocations of the
         * [ConfigFileProvider] use the resolved context then.
         */
        fun create(config: Config, context: Context, resolveContext: Boolean = false): ConfigManager {
            if (!config.hasPath(CONFIG_MANAGER_SECTION)) {
                throw ConfigException("Missing '$CONFIG_MANAGER_SECTION' section.", null)
            }

            val managerConfig = config.getConfig(CONFIG_MANAGER_SECTION)

            val fileProviderName = managerConfig.getStringOrNull(FILE_PROVIDER_NAME_PROPERTY)
                ?: throw ConfigException("Missing '$FILE_PROVIDER_NAME_PROPERTY' property.", null)

            val fileProviderFactory = FILE_PROVIDER_LOADER.find { it.name == fileProviderName }
                ?: throw ConfigException(
                    "Could not find ConfigFileProvider with name '$fileProviderName' on classpath.",
                    null
                )
            val fileProvider = fileProviderFactory.createProvider(managerConfig)

            val resolvedContext = wrapExceptions {
                if (resolveContext) fileProvider.resolveContext(context) else context
            }

            return ConfigManager(resolvedContext, fileProvider)
        }
    }

    /**
     * Return an [InputStream] for reading the content of the configuration file at the given [path] in the current
     * [Context]. Throw a [ConfigException] if the underlying [ConfigFileProvider] throws an exception.
     */
    fun getFile(path: Path): InputStream = wrapExceptions { configFileProvider.getFile(context, path) }

    /**
     * Return the content of the configuration file under the given [path] in the current [Context] as a string.
     * Throw a [ConfigException] if the underlying [ConfigFileProvider] throws an exception.
     */
    fun getFileAsString(path: Path): String {
        val configStream = getFile(path)

        return wrapExceptions {
            configStream.use { stream ->
                String(stream.readAllBytes())
            }
        }
    }

    /**
     * Check whether a configuration file exists at the given [path] in the current [Context]. Throw a
     * [ConfigException] if the underlying [ConfigFileProvider] throws an exception.
     */
    fun containsFile(path: Path): Boolean = wrapExceptions { configFileProvider.contains(context, path) }

    /**
     * Return a [Set] with the [Path]s to configuration files that are located under the given [path]. The provided
     * [path] should point to a directory, so that it can contain files. Throw a [ConfigException] if the
     * underlying [ConfigFileProvider] throws an exception.
     */
    fun listFiles(path: Path): Set<Path> = wrapExceptions { configFileProvider.listFiles(context, path) }
}

/**
 * A specialized exception class for reporting errors related to the access of configuration data.
 */
class ConfigException(message: String, cause: Throwable?) : Exception(message, cause)

/**
 * Execute the given [block] and catch all exceptions it might throw. A caught exception is then wrapped in a
 * [ConfigException] which is rethrown.
 */
@Suppress("TooGenericExceptionCaught")
private fun <T> wrapExceptions(block: () -> T): T =
    try {
        block()
    } catch (e: Exception) {
        throw ConfigException("Exception from config provider", e)
    }
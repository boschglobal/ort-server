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

package org.ossreviewtoolkit.server.config.github

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.ok
import com.github.tomakehurst.wiremock.client.WireMock.okJson
import com.github.tomakehurst.wiremock.client.WireMock.status
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration

import com.typesafe.config.ConfigFactory

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.Spec
import io.kotest.core.spec.style.WordSpec
import io.kotest.core.test.TestCase
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

import io.ktor.http.HttpStatusCode

import io.mockk.every
import io.mockk.mockk

import java.util.Base64

import org.ossreviewtoolkit.server.config.ConfigException
import org.ossreviewtoolkit.server.config.ConfigSecretProvider
import org.ossreviewtoolkit.server.config.Context
import org.ossreviewtoolkit.server.config.Path
import org.ossreviewtoolkit.server.config.github.GitHubConfigFileProvider.Companion.GITHUB_API_URL
import org.ossreviewtoolkit.server.config.github.GitHubConfigFileProvider.Companion.JSON_CONTENT_TYPE_HEADER
import org.ossreviewtoolkit.server.config.github.GitHubConfigFileProvider.Companion.RAW_CONTENT_TYPE_HEADER
import org.ossreviewtoolkit.server.config.github.GitHubConfigFileProvider.Companion.REPOSITORY_NAME
import org.ossreviewtoolkit.server.config.github.GitHubConfigFileProvider.Companion.REPOSITORY_OWNER
import org.ossreviewtoolkit.server.config.github.GitHubConfigFileProvider.Companion.TOKEN
import org.ossreviewtoolkit.server.config.github.GitHubConfigFileProvider.Companion.USER_NAME

class GitHubConfigFileProviderTest : WordSpec() {
    override suspend fun beforeSpec(spec: Spec) {
        server.start()
    }

    override suspend fun afterSpec(spec: Spec) {
        server.stop()
    }

    override suspend fun beforeEach(testCase: TestCase) {
        server.resetAll()
    }

    init {
        "resolveContext" should {
            "resolve a context successfully" {
                server.stubExistingRevision()

                val provider = getProvider()

                val resolvedContext = provider.resolveContext(Context(REVISION))
                resolvedContext.name shouldBe "0a4721665650ba7143871b22ef878e5b81c8f8b5"
            }

            "throw exception if response doesn't contain SHA-1 commit ID" {
                server.stubMissingRevision()

                val provider = getProvider()

                val exception = shouldThrow<NoSuchFieldException> {
                    provider.resolveContext(Context(REVISION + 1))
                }

                exception.message shouldContain "SHA-1"
                exception.message shouldContain "commit"
                exception.message shouldContain "branch"
                exception.message shouldContain REVISION + 1
            }

            "throw exception if the branch does not exist" {
                server.stubRevisionNotFound()

                val provider = getProvider()

                val exception = shouldThrow<ConfigException> {
                    provider.resolveContext(Context(REVISION + NOT_FOUND))
                }

                exception.message shouldContain "branch"
                exception.message shouldContain REVISION + NOT_FOUND
                exception.message shouldContain "repository"
                exception.message shouldContain REPOSITORY
            }
        }

        "getFile" should {
            "successfully retrieve a file" {
                server.stubRawFile()

                val provider = getProvider()

                val fileContent = provider.getFile(Context(REVISION), Path(CONFIG_PATH))
                    .bufferedReader(Charsets.UTF_8)
                    .use { it.readText() }

                fileContent shouldBe CONTENT
            }

            "throw an exception if the response has a wrong content type" {
                server.stubUnexpectedJsonContentType()

                val provider = getProvider()

                val exception = shouldThrow<ConfigException> {
                    provider.getFile(Context(REVISION), Path(CONFIG_PATH + 1))
                }

                exception.message shouldContain "content type"
                exception.message shouldContain "application/json"
            }

            "throw an exception if the path refers a directory" {
                server.stubDirectory()

                val provider = getProvider()

                val exception = shouldThrow<ConfigException> {
                    provider.getFile(Context(REVISION), Path(DIRECTORY_PATH))
                }

                exception.message shouldContain DIRECTORY_PATH
                exception.message shouldContain "directory"
            }
        }

        "contains" should {
            "return `true` if the config file is present" {
                server.stubJsonFileContentType()

                val provider = getProvider()

                provider.contains(Context(REVISION + 1), Path(CONFIG_PATH)) shouldBe true
            }

            "return `false` if the path refers a directory" {
                server.stubDirectory()

                val provider = getProvider()

                provider.contains(Context(REVISION), Path(DIRECTORY_PATH)) shouldBe false
            }

            "return `false` if a `NotFound` response is received" {
                server.stubFileNotFound()

                val provider = getProvider()

                provider.contains(Context(REVISION), Path(CONFIG_PATH + NOT_FOUND)) shouldBe false
            }
        }

        "listFiles" should {
            "return a list of files inside a given directory" {
                server.stubDirectory()

                val expectedPaths = setOf(Path(CONFIG_PATH + "1"), Path(CONFIG_PATH + "2"))

                val provider = getProvider()

                val listFiles = provider.listFiles(Context(REVISION), Path(DIRECTORY_PATH))

                listFiles shouldContainExactlyInAnyOrder expectedPaths
            }

            "throw an exception if the path does not refer a directory" {
                server.stubJsonFileContentType()

                val provider = getProvider()

                val exception = shouldThrow<ConfigException> {
                    provider.listFiles(Context(REVISION + 1), Path(CONFIG_PATH))
                }

                exception.message shouldContain "`$CONFIG_PATH`"
                exception.message shouldContain "does not refer a directory."
            }
        }
    }
}

/** A mock for GitHub API to be used by tests. */
val server = WireMockServer(WireMockConfiguration.options().dynamicPort())

const val OWNER = "owner"
const val REPOSITORY = "repository"
const val REVISION = "configs-branch"
const val CONFIG_PATH = "/config/app.config"
const val DIRECTORY_PATH = "/config"
const val CONTENT = "repository: repo, username: user, password: pass"
const val NOT_FOUND = "NotFound"

/**
 * Returns a configured [GitHubConfigFileProvider] instance.
 */
private fun getProvider(): GitHubConfigFileProvider {
    val secretProvider = mockk<ConfigSecretProvider>()

    every { secretProvider.getSecret(USER_NAME) } returns "userName"
    every { secretProvider.getSecret(TOKEN) } returns "userToken"

    val config = ConfigFactory.parseMap(
        mapOf(
            GITHUB_API_URL to server.baseUrl(),
            REPOSITORY_OWNER to OWNER,
            REPOSITORY_NAME to REPOSITORY
        )
    )

    return GitHubConfigFileProvider(config, secretProvider)
}

/**
 * A stub for successfully resolving a revision.
 */
fun WireMockServer.stubExistingRevision() {
    stubFor(
        get(
            urlEqualTo("/repos/$OWNER/$REPOSITORY/branches/$REVISION")
        ).willReturn(
            okJson(
                "{\n" +
                        "    \"name\": \"app.config\",\n" +
                        "    \"path\": \"$CONFIG_PATH\",\n" +
                        "    \"sha\": \"0a4721665650ba7143871b22ef878e5b81c8f8b5\",\n" +
                        "    \"size\": 303\n" +
                        "}"
            )
        )
    )
}

/**
 * A stub with a missing SHA-1 commit ID.
 */
fun WireMockServer.stubMissingRevision() {
    stubFor(
        get(
            urlEqualTo("/repos/$OWNER/$REPOSITORY/branches/${REVISION + 1}")
        ).willReturn(
            okJson(
                "{\n" +
                        "    \"name\": \"app.config\",\n" +
                        "    \"path\": \"$CONFIG_PATH\",\n" +
                        "    \"size\": 303\n" +
                        "}"
            )
        )
    )
}

/**
 * A stub which returns a "Not Found" response for a revision.
 */
fun WireMockServer.stubRevisionNotFound() {
    stubFor(
        get(
            urlEqualTo("/repos/$OWNER/$REPOSITORY/branches/${REVISION + NOT_FOUND}")
        ).willReturn(
            status(
                HttpStatusCode.NotFound.value
            ).withBody(
                "{\n" +
                        "    \"message\": \"Branch not found\",\n" +
                        "    \"documentation_url\": \"https://docs.github.com/\"\n" +
                        "}"
            )
        )
    )
}

/**
 * A stub for successfully getting a raw file.
 */
fun WireMockServer.stubRawFile() {
    stubFor(
        get(
            urlPathEqualTo("/repos/$OWNER/$REPOSITORY/contents/$CONFIG_PATH")
        ).withQueryParam("ref", equalTo(REVISION))
            .withHeader("Accept", equalTo(RAW_CONTENT_TYPE_HEADER))
            .willReturn(
                ok(CONTENT).withHeader("Content-Type", RAW_CONTENT_TYPE_HEADER)
            )
    )
}

/**
 * A stub which returns a "Not Found" response for a file.
 */
fun WireMockServer.stubFileNotFound() {
    stubFor(
        get(
            urlPathEqualTo("/repos/$OWNER/$REPOSITORY/contents/${CONFIG_PATH + NOT_FOUND}")
        ).willReturn(
            status(
                HttpStatusCode.NotFound.value
            ).withBody(
                "{\n" +
                        "    \"message\": \"Not Found\",\n" +
                        "    \"documentation_url\": \"https://docs.github.com/\"\n" +
                        "}"
            )
        )
    )
}

/**
 * A stub returning a response with unexpected json content type.
 */
fun WireMockServer.stubUnexpectedJsonContentType() {
    stubFor(
        get(
            urlPathEqualTo("/repos/$OWNER/$REPOSITORY/contents/${CONFIG_PATH + 1}")
        ).withQueryParam("ref", equalTo(REVISION))
            .willReturn(
                aResponse().withBody(
                    "{\n" +
                            "    \"name\": \"app.config\",\n" +
                            "    \"path\": \"${CONFIG_PATH + 1}\",\n" +
                            "    \"sha\": \"0a4721665650ba7143871b22ef878e5b81c8f8b5\",\n" +
                            "    \"type\": \"file\",\n" +
                            "    \"size\": 303\n" +
                            "}"
                ).withHeader("Content-Type", JSON_CONTENT_TYPE_HEADER)
            )
    )
}

/**
 * A stub for successfully getting a file information in JSON format.
 */
fun WireMockServer.stubJsonFileContentType() {
    stubFor(
        get(
            urlPathEqualTo("/repos/$OWNER/$REPOSITORY/contents/$CONFIG_PATH")
        ).withQueryParam("ref", equalTo(REVISION + 1))
            .willReturn(
                okJson(
                    "{\n" +
                            "    \"name\": \"app.config\",\n" +
                            "    \"path\": \"$CONFIG_PATH\",\n" +
                            "    \"sha\": \"0a4721665650ba7143871b22ef878e5b81c8f8b5\",\n" +
                            "    \"size\": 303,\n" +
                            "    \"type\": \"file\",\n" +
                            "    \"content\": \"${String(Base64.getEncoder().encode(CONTENT.toByteArray()))}\"\n" +
                            "}"
                )
            )
    )
}

/**
 * A stub returning a response for a path referencing a directory with several objects.
 */
fun WireMockServer.stubDirectory() {
    stubFor(
        get(
            urlPathEqualTo("/repos/$OWNER/$REPOSITORY/contents/$DIRECTORY_PATH")
        ).withQueryParam("ref", equalTo(REVISION))
            .willReturn(
                okJson(
                    "[\n" +
                            "{\n" +
                            "    \"name\": \"app1.config\",\n" +
                            "    \"path\": \"${CONFIG_PATH + 1}\",\n" +
                            "    \"sha\": \"0a4721665650ba7143871b22ef878e5b81c8f8b6\",\n" +
                            "    \"type\": \"file\",\n" +
                            "    \"size\": 908\n" +
                            "}," +
                            "{\n" +
                            "    \"name\": \"app2.config\",\n" +
                            "    \"path\": \"${CONFIG_PATH + 2}\",\n" +
                            "    \"sha\": \"0a4721665650ba7143871b22ef878e5b81c8f8b7\",\n" +
                            "    \"type\": \"file\",\n" +
                            "    \"size\": 305\n" +
                            "}," +
                            "{\n" +
                            "    \"name\": \"other configs\",\n" +
                            "    \"path\": \"${DIRECTORY_PATH + 1}\",\n" +
                            "    \"sha\": \"0a4721665650ba7143871b22ef878e5b81c8f8b5\",\n" +
                            "    \"type\": \"dir\",\n" +
                            "    \"size\": 303\n" +
                            "}\n" +
                            "]"
                )
            )
    )
}
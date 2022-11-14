/*
 * Copyright (C) 2022 The ORT Project Authors (See <https://github.com/oss-review-toolkit/ort-server/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.server.core.api

import io.kotest.core.test.TestCase
import io.kotest.matchers.shouldBe

import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.patch
import io.ktor.client.request.setBody
import io.ktor.http.HttpStatusCode

import org.ossreviewtoolkit.server.api.v1.Repository
import org.ossreviewtoolkit.server.api.v1.RepositoryType as ApiRepositoryType
import org.ossreviewtoolkit.server.api.v1.UpdateRepository
import org.ossreviewtoolkit.server.api.v1.mapToApi
import org.ossreviewtoolkit.server.core.createJsonClient
import org.ossreviewtoolkit.server.core.testutils.basicTestAuth
import org.ossreviewtoolkit.server.core.testutils.noDbConfig
import org.ossreviewtoolkit.server.core.testutils.ortServerTestApplication
import org.ossreviewtoolkit.server.dao.connect
import org.ossreviewtoolkit.server.dao.migrate
import org.ossreviewtoolkit.server.dao.repositories.DaoOrganizationRepository
import org.ossreviewtoolkit.server.dao.repositories.DaoOrtRunRepository
import org.ossreviewtoolkit.server.dao.repositories.DaoProductRepository
import org.ossreviewtoolkit.server.dao.repositories.DaoRepositoryRepository
import org.ossreviewtoolkit.server.model.RepositoryType
import org.ossreviewtoolkit.server.model.repositories.OrganizationRepository
import org.ossreviewtoolkit.server.model.repositories.OrtRunRepository
import org.ossreviewtoolkit.server.model.repositories.ProductRepository
import org.ossreviewtoolkit.server.model.repositories.RepositoryRepository
import org.ossreviewtoolkit.server.model.util.OptionalValue
import org.ossreviewtoolkit.server.utils.test.DatabaseTest

class RepositoriesRouteIntegrationTest : DatabaseTest() {
    private lateinit var organizationRepository: OrganizationRepository
    private lateinit var ortRunRepository: OrtRunRepository
    private lateinit var productRepository: ProductRepository
    private lateinit var repositoryRepository: RepositoryRepository

    private var orgId = -1L
    private var productId = -1L

    override suspend fun beforeTest(testCase: TestCase) {
        dataSource.connect()
        dataSource.migrate()

        organizationRepository = DaoOrganizationRepository()
        ortRunRepository = DaoOrtRunRepository()
        productRepository = DaoProductRepository()
        repositoryRepository = DaoRepositoryRepository()

        orgId = organizationRepository.create(name = "name", description = "description").id
        productId = productRepository.create(name = "name", description = "description", organizationId = orgId).id
    }

    init {
        test("GET /repositories/{repositoryId} should return a single repository") {
            ortServerTestApplication(noDbConfig) {
                val client = createJsonClient()

                val type = RepositoryType.GIT
                val url = "https://example.com/repo.git"

                val createdRepository = repositoryRepository.create(type = type, url = url, productId = productId)

                val response = client.get("/api/v1/repositories/${createdRepository.id}") {
                    headers {
                        basicTestAuth()
                    }
                }

                with(response) {
                    status shouldBe HttpStatusCode.OK
                    body<Repository>() shouldBe Repository(createdRepository.id, type.mapToApi(), url)
                }
            }
        }

        test("PATCH /repositories/{repositoryId} should update a repository") {
            ortServerTestApplication(noDbConfig) {
                val client = createJsonClient()

                val createdRepository = repositoryRepository.create(
                    type = RepositoryType.GIT,
                    url = "https://example.com/repo.git",
                    productId = productId
                )

                val updateRepository = UpdateRepository(
                    OptionalValue.Present(ApiRepositoryType.SUBVERSION),
                    OptionalValue.Present("https://svn.example.com/repos/org/repo/trunk")
                )

                val response = client.patch("/api/v1/repositories/${createdRepository.id}") {
                    headers {
                        basicTestAuth()
                    }
                    setBody(updateRepository)
                }

                with(response) {
                    status shouldBe HttpStatusCode.OK
                    body<Repository>() shouldBe Repository(
                        createdRepository.id,
                        (updateRepository.type as OptionalValue.Present).value,
                        (updateRepository.url as OptionalValue.Present).value
                    )
                }
            }
        }

        test("DELETE /repositories/{repositoryId} should delete a repository") {
            ortServerTestApplication(noDbConfig) {
                val client = createJsonClient()

                val createdRepository = repositoryRepository.create(
                    type = RepositoryType.GIT,
                    url = "https://example.com/repo.git",
                    productId = productId
                )

                val response = client.delete("/api/v1/repositories/${createdRepository.id}") {
                    headers {
                        basicTestAuth()
                    }
                }

                response.status shouldBe HttpStatusCode.NoContent
                repositoryRepository.listForProduct(productId) shouldBe emptyList()
            }
        }
    }
}

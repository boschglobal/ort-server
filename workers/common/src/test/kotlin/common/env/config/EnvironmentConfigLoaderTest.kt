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

package org.ossreviewtoolkit.server.workers.common.common.env.config

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldNot
import io.kotest.matchers.string.shouldContain

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify

import org.ossreviewtoolkit.server.model.Hierarchy
import org.ossreviewtoolkit.server.model.InfrastructureService
import org.ossreviewtoolkit.server.model.Organization
import org.ossreviewtoolkit.server.model.Product
import org.ossreviewtoolkit.server.model.Repository
import org.ossreviewtoolkit.server.model.RepositoryType
import org.ossreviewtoolkit.server.model.Secret
import org.ossreviewtoolkit.server.model.repositories.InfrastructureServiceRepository
import org.ossreviewtoolkit.server.model.repositories.SecretRepository
import org.ossreviewtoolkit.server.workers.common.env.config.EnvironmentConfig
import org.ossreviewtoolkit.server.workers.common.env.config.EnvironmentConfigException
import org.ossreviewtoolkit.server.workers.common.env.config.EnvironmentConfigLoader
import org.ossreviewtoolkit.server.workers.common.env.config.EnvironmentDefinitionFactory
import org.ossreviewtoolkit.server.workers.common.env.definition.EnvironmentServiceDefinition
import org.ossreviewtoolkit.server.workers.common.env.definition.MavenDefinition

class EnvironmentConfigLoaderTest : StringSpec() {
    init {
        "An empty configuration is returned if there is no configuration file" {
            val tempDir = tempdir()
            val helper = TestHelper()

            val config = helper.loader().parse(tempDir, hierarchy)

            config.infrastructureServices should beEmpty()
        }

        "A configuration file can be read" {
            val helper = TestHelper()
            val userSecret = helper.createSecret("testUser", repository = repository)
            val pass1Secret = helper.createSecret("testPassword1", repository = repository)
            val pass2Secret = helper.createSecret("testPassword2", repository = repository)

            val expectedServices = listOf(
                createTestService(1, userSecret, pass1Secret),
                createTestService(2, userSecret, pass2Secret)
            )

            val config = loadConfig(".ort.env.simple.yml", helper)

            config.infrastructureServices shouldContainExactlyInAnyOrder expectedServices
        }

        "Secrets can be resolved from products and organizations" {
            val helper = TestHelper()
            val userSecret = helper.createSecret("testUser", repository = repository)
            val pass1Secret = helper.createSecret("testPassword1", product = product)
            val pass2Secret = helper.createSecret("testPassword2", organization = organization)

            val expectedServices = listOf(
                createTestService(1, userSecret, pass1Secret),
                createTestService(2, userSecret, pass2Secret)
            )

            val config = loadConfig(".ort.env.simple.yml", helper)

            config.infrastructureServices shouldContainExactlyInAnyOrder expectedServices
        }

        "Secrets are only queried if necessary" {
            val helper = TestHelper()
            helper.createSecret("testUser", repository = repository)
            helper.createSecret("testPassword1", repository = repository)
            helper.createSecret("testPassword2", repository = repository)

            loadConfig(".ort.env.simple.yml", helper)

            verify(exactly = 0) {
                helper.secretRepository.listForProduct(any(), any())
                helper.secretRepository.listForOrganization(any(), any())
            }
        }

        "Unresolved secrets cause an exception in strict mode" {
            val helper = TestHelper()
            helper.createSecret("testPassword1", organization = organization)

            val exception = shouldThrow<EnvironmentConfigException> {
                loadConfig(".ort.env.simple.yml", helper)
            }

            exception.message shouldContain "testUser"
            exception.message shouldContain "testPassword2"
        }

        "Services with unresolved secrets are ignored in non-strict mode" {
            val helper = TestHelper()
            val userSecret = helper.createSecret("testUser", repository = repository)
            val pass2Secret = helper.createSecret("testPassword2", repository = repository)

            val expectedServices = listOf(createTestService(2, userSecret, pass2Secret))

            val config = loadConfig(".ort.env.non-strict.yml", helper)

            config.infrastructureServices shouldContainExactlyInAnyOrder expectedServices
        }

        "Environment definitions are processed" {
            val helper = TestHelper()
            val userSecret = helper.createSecret("testUser", repository = repository)
            val pass1Secret = helper.createSecret("testPassword1", repository = repository)
            helper.createSecret("testPassword2", repository = repository)

            val service = createTestService(1, userSecret, pass1Secret)

            val config = loadConfig(".ort.env.definitions.yml", helper)

            config.shouldContainDefinition<MavenDefinition>(service) { it.id == "repo1" }
        }

        "Invalid definitions cause exceptions" {
            val helper = TestHelper()
            helper.createSecret("testUser", repository = repository)
            helper.createSecret("testPassword1", repository = repository)
            helper.createSecret("testPassword2", repository = repository)

            val exception = shouldThrow<EnvironmentConfigException> {
                loadConfig(".ort.env.definitions-errors.yml", helper)
            }

            exception.message shouldContain "'Non-existing service'"
            exception.message shouldContain "Missing service reference"
            exception.message shouldContain "Unsupported definition type 'unknown'"
            exception.message shouldContain "Missing required properties"
        }

        "Invalid definitions are ignored in non-strict mode" {
            val helper = TestHelper()
            val userSecret = helper.createSecret("testUser", repository = repository)
            val pass1Secret = helper.createSecret("testPassword1", repository = repository)
            helper.createSecret("testPassword2", repository = repository)

            val service = createTestService(1, userSecret, pass1Secret)

            val config = loadConfig(".ort.env.definitions-errors-non-strict.yml", helper)

            config.shouldContainDefinition<MavenDefinition>(service) { it.id == "repo1" }
        }

        "Services can be resolved in the hierarchy" {
            val helper = TestHelper()
            val userSecret = helper.createSecret("testUser", repository = repository)
            val passSecret = helper.createSecret("testPassword1", repository = repository)

            val prodService = createTestService(2, userSecret, passSecret)
            val orgService = createTestService(3, userSecret, passSecret)
            val shadowedOrgService = createTestService(2, userSecret, passSecret)
                .copy(url = "https://another-repo.example.org/test.git")
            helper.withProductService(prodService)
                .withOrganizationService(orgService)
                .withOrganizationService(shadowedOrgService)

            val config = loadConfig(".ort.env.definitions-hierarchy-services.yml", helper)

            config.shouldContainDefinition<MavenDefinition>(prodService) { it.id == "repo2" }
            config.shouldContainDefinition<MavenDefinition>(orgService) { it.id == "repo3" }
        }
    }

    /**
     * Read the test configuration with the given [name] from the resources using the given [helper].
     */
    private fun loadConfig(name: String, helper: TestHelper): EnvironmentConfig {
        val tempDir = tempdir()

        javaClass.getResourceAsStream("/$name")?.use { stream ->
            val target = tempDir.resolve(EnvironmentConfigLoader.CONFIG_FILE_PATH)
            target.outputStream().use { out ->
                stream.copyTo(out)
            }
        }

        return helper.loader().parse(tempDir, hierarchy)
    }
}

/**
 * A test helper class managing the dependencies required by the object under test.
 */
private class TestHelper(
    /** Mock for the repository for secrets. */
    val secretRepository: SecretRepository = mockk(),

    /** Mock for the repository for infrastructure services. */
    val serviceRepository: InfrastructureServiceRepository = mockk()
) {
    /** Stores the secrets referenced by tests. */
    private val secrets = mutableListOf<Secret>()

    /** Stores infrastructure services assigned to the current product. */
    private val productServices = mutableListOf<InfrastructureService>()

    /** Stores infrastructure services assigned to the current organization. */
    private val organizationServices = mutableListOf<InfrastructureService>()

    /**
     * Create a new [EnvironmentConfigLoader] instance with the dependencies managed by this object.
     */
    fun loader(): EnvironmentConfigLoader {
        initSecretRepository()
        initServiceRepository()

        return EnvironmentConfigLoader(secretRepository, serviceRepository, EnvironmentDefinitionFactory())
    }

    /**
     * Create a test secret with the given [name] and associate it with the given structures. The mock for the
     * [SecretRepository] is also prepared to return this secret when asked for the corresponding structure.
     */
    fun createSecret(
        name: String,
        repository: Repository? = null,
        product: Product? = null,
        organization: Organization? = null
    ): Secret =
        Secret(
            id = 0,
            path = name,
            name = name,
            description = null,
            organization = organization,
            product = product,
            repository = repository
        ).also { secrets.add(it) }

    /**
     * Add the given [service] to the list of product services. It will be returned by the mock service repository
     * when it is queried for product services.
     */
    fun withProductService(service: InfrastructureService): TestHelper {
        productServices += service
        return this
    }

    /**
     * Add the given [service] to the list of organization services. It will be returned by the mock service repository
     * when it is queried for organization services.
     */
    fun withOrganizationService(service: InfrastructureService): TestHelper {
        organizationServices += service
        return this
    }

    /**
     * Prepare the mock for the [SecretRepository] to answer queries based on the secrets that have been defined.
     */
    private fun initSecretRepository() {
        every {
            secretRepository.listForRepository(repository.id)
        } returns secrets.filter { it.repository != null }
        every {
            secretRepository.listForProduct(product.id)
        } returns secrets.filter { it.product != null }
        every {
            secretRepository.listForOrganization(organization.id)
        } returns secrets.filter { it.organization != null }
    }

    /**
     * Prepare the mock for the [InfrastructureServiceRepository] to answer queries for the product and organization
     * services based on the data that has been defined.
     */
    private fun initServiceRepository() {
        every { serviceRepository.listForProduct(hierarchy.product.id) } returns productServices
        every { serviceRepository.listForOrganization(hierarchy.organization.id) } returns organizationServices
    }
}

private val organization = Organization(20230621115936L, "Test organization")
private val product = Product(20230621120012L, organization.id, "Test product")
private val repository = Repository(
    20230621120048L,
    organization.id,
    product.id,
    RepositoryType.GIT,
    "https://repo.example.org/test.git"
)

/** A test [Hierarchy] used by the tests. */
private val hierarchy = Hierarchy(repository, product, organization)

/**
 * Create a test service based on the given [index] with the given [userSecret] and [passSecret].
 */
private fun createTestService(index: Int, userSecret: Secret, passSecret: Secret): InfrastructureService =
    InfrastructureService(
        name = "Test service$index",
        url = "https://repo.example.org/test/service$index",
        description = "Test service $index",
        usernameSecret = userSecret,
        passwordSecret = passSecret,
        organization = null,
        product = null
    )

/**
 * Check whether this [EnvironmentConfig] contains an environment definition of a specific type that references the
 * given [service] and passes the given [check].
 */
private inline fun <reified T : EnvironmentServiceDefinition> EnvironmentConfig.shouldContainDefinition(
    service: InfrastructureService,
    check: (T) -> Boolean
) {
    environmentDefinitions.find { definition ->
        definition is T && definition.service == service && check(definition)
    } shouldNot beNull()
}
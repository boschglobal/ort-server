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

package org.eclipse.apoapsis.ortserver.services

import org.eclipse.apoapsis.ortserver.dao.dbQuery
import org.eclipse.apoapsis.ortserver.dao.dbQueryCatching
import org.eclipse.apoapsis.ortserver.model.Organization
import org.eclipse.apoapsis.ortserver.model.repositories.OrganizationRepository
import org.eclipse.apoapsis.ortserver.model.repositories.ProductRepository
import org.eclipse.apoapsis.ortserver.model.util.ListQueryParameters
import org.eclipse.apoapsis.ortserver.model.util.OptionalValue

import org.jetbrains.exposed.sql.Database

import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger(OrganizationService::class.java)

/**
 * A service providing functions for working with [organizations][Organization].
 */
class OrganizationService(
    private val db: Database,
    private val organizationRepository: OrganizationRepository,
    private val productRepository: ProductRepository,
    private val authorizationService: AuthorizationService
) {
    /**
     * Create an organization.
     */
    suspend fun createOrganization(name: String, description: String?): Organization = db.dbQueryCatching {
        organizationRepository.create(name, description)
    }.onSuccess { organization ->
        runCatching {
            authorizationService.createOrganizationPermissions(organization.id)
            authorizationService.createOrganizationRoles(organization.id)
        }.onFailure {
            logger.error("Error while creating Keycloak roles for organization '${organization.id}'.", it)
        }
    }.getOrThrow()

    /**
     * Create a product inside an [organization][organizationId].
     */
    suspend fun createProduct(name: String, description: String?, organizationId: Long) = db.dbQueryCatching {
        productRepository.create(name, description, organizationId)
    }.onSuccess { product ->
        runCatching {
            authorizationService.createProductPermissions(product.id)
            authorizationService.createProductRoles(product.id)
        }.onFailure {
            logger.error("Error while creating Keycloak roles for product '${product.id}'.", it)
        }
    }.getOrThrow()

    /**
     * Delete an organization by [organizationId].
     */
    suspend fun deleteOrganization(organizationId: Long): Unit = db.dbQueryCatching {
        organizationRepository.delete(organizationId)
    }.onSuccess {
        runCatching {
            authorizationService.deleteOrganizationPermissions(organizationId)
            authorizationService.deleteOrganizationRoles(organizationId)
        }.onFailure {
            logger.error("Error while deleting Keycloak roles for organization '$organizationId'.", it)
        }
    }.getOrThrow()

    /**
     * Get an organization by [organizationId]. Returns null if the organization is not found.
     */
    suspend fun getOrganization(organizationId: Long): Organization? = db.dbQuery {
        organizationRepository.get(organizationId)
    }

    /**
     * List all organizations according to the given [parameters].
     */
    suspend fun listOrganizations(
        parameters: ListQueryParameters = ListQueryParameters.DEFAULT
    ): List<Organization> = db.dbQuery {
        organizationRepository.list(parameters)
    }

    /**
     * List all products for an [organization][organizationId].
     */
    suspend fun listProductsForOrganization(
        organizationId: Long,
        parameters: ListQueryParameters = ListQueryParameters.DEFAULT
    ) = db.dbQuery {
        productRepository.listForOrganization(organizationId, parameters)
    }

    /**
     * Update an organization by [organizationId] with the [present][OptionalValue.Present] values.
     */
    suspend fun updateOrganization(
        organizationId: Long,
        name: OptionalValue<String> = OptionalValue.Absent,
        description: OptionalValue<String?> = OptionalValue.Absent
    ): Organization = db.dbQuery {
        organizationRepository.update(organizationId, name, description)
    }
}

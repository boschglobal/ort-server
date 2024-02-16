/*
 * Copyright (C) 2023 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

package org.eclipse.apoapsis.ortserver.model.authorization

import org.eclipse.apoapsis.ortserver.model.Organization
import org.eclipse.apoapsis.ortserver.model.Product

/**
 * This enum contains the available permissions for [organizations][Organization]. These permissions are used by the API
 * to control access to the [Organization] endpoints.
 */
enum class OrganizationPermission {

    /** Permission to read the [Organization] details. */
    READ,

    /** Permission to write the [Organization] details. */
    WRITE,

    /** Permission to write the [Organization] secrets. */
    WRITE_SECRETS,

    /** Permission to read the list of [Product]s of the [Organization]. */
    READ_PRODUCTS,

    /** Permission to create a [Product] for the [Organization]. */
    CREATE_PRODUCT,

    /** Permission to delete the [Organization]. */
    DELETE;

    companion object {
        /**
         * Get all [role names][roleName] for the provided [organizationId].
         */
        fun getRolesForOrganization(organizationId: Long) =
            enumValues<OrganizationPermission>().map { it.roleName(organizationId) }

        /**
         * A unique prefix for the roles for the provided [organizationId].
         */
        fun rolePrefix(organizationId: Long) = "permission_organization_${organizationId}_"
    }

    /** A unique name for this permission to be used to represent the permission as a role in Keycloak. */
    fun roleName(organizationId: Long): String = "${rolePrefix(organizationId)}${name.lowercase()}"
}

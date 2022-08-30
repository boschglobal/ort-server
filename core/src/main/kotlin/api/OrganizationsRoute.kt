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

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.route

import org.ossreviewtoolkit.server.core.utils.requireParameter
import org.ossreviewtoolkit.server.dao.repositories.OrganizationsRepository
import org.ossreviewtoolkit.server.dao.repositories.ProductsRepository
import org.ossreviewtoolkit.server.shared.models.api.CreateOrganization
import org.ossreviewtoolkit.server.shared.models.api.CreateProduct
import org.ossreviewtoolkit.server.shared.models.api.UpdateOrganization

fun Route.organizations() = route("organizations") {
    get {
        val organizations = OrganizationsRepository.listOrganizations()

        call.respond(HttpStatusCode.OK, organizations.map { it.mapToApiModel() })
    }

    post {
        val createOrganization = call.receive<CreateOrganization>()

        val createdOrganization = OrganizationsRepository.createOrganization(createOrganization)

        call.respond(HttpStatusCode.Created, createdOrganization.mapToApiModel())
    }

    route("{organizationId}") {
        get {
            val id = call.requireParameter("organizationId").toLong()

            val organization = OrganizationsRepository.getOrganization(id)

            organization?.let { call.respond(HttpStatusCode.OK, it.mapToApiModel()) }
                ?: call.respond(HttpStatusCode.NotFound)
        }

        patch {
            val organizationId = call.requireParameter("organizationId").toLong()
            val org = call.receive<UpdateOrganization>()

            val updatedOrg = OrganizationsRepository.updateOrganization(organizationId, org)

            call.respond(HttpStatusCode.OK, updatedOrg.mapToApiModel())
        }

        delete {
            val id = call.requireParameter("organizationId").toLong()

            OrganizationsRepository.deleteOrganization(id)

            call.respond(HttpStatusCode.NoContent)
        }

        get("products") {
            val orgId = call.requireParameter("organizationId").toLong()

            call.respond(HttpStatusCode.OK, ProductsRepository.listProductsForOrg(orgId).map { it.mapToApiModel() })
        }

        post("products") {
            val createProduct = call.receive<CreateProduct>()
            val orgId = call.requireParameter("organizationId").toLong()

            val createdProduct = ProductsRepository.createProduct(orgId, createProduct)

            call.respond(HttpStatusCode.Created, createdProduct.mapToApiModel())
        }
    }
}

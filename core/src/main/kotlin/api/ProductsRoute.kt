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
import org.ossreviewtoolkit.server.dao.repositories.ProductsRepository
import org.ossreviewtoolkit.server.dao.repositories.RepositoriesRepository
import org.ossreviewtoolkit.server.shared.models.api.CreateRepository
import org.ossreviewtoolkit.server.shared.models.api.UpdateProduct

fun Route.products() = route("products/{productId}") {
    get {
        val id = call.requireParameter("productId").toLong()

        val product = ProductsRepository.getProduct(id)

        if (product != null) {
            call.respond(HttpStatusCode.OK, product.mapToApiModel())
        } else {
            call.respond(HttpStatusCode.NotFound)
        }
    }

    patch {
        val id = call.requireParameter("productId").toLong()
        val updateProduct = call.receive<UpdateProduct>()

        val updatedProduct = ProductsRepository.updateProduct(id, updateProduct)

        call.respond(HttpStatusCode.OK, updatedProduct.mapToApiModel())
    }

    delete {
        val id = call.requireParameter("productId").toLong()

        ProductsRepository.deleteProduct(id)

        call.respond(HttpStatusCode.NoContent)
    }

    route("repositories") {
        get {
            val id = call.requireParameter("productId").toLong()

            call.respond(
                HttpStatusCode.OK,
                RepositoriesRepository.listRepositoriesForProduct(id).map { it.mapToApiModel() }
            )
        }

        post {
            val id = call.requireParameter("productId").toLong()
            val createRepository = call.receive<CreateRepository>()

            call.respond(
                HttpStatusCode.Created,
                RepositoriesRepository.createRepository(id, createRepository).mapToApiModel()
            )
        }
    }
}

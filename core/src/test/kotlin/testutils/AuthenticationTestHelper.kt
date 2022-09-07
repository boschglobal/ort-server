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

package org.ossreviewtoolkit.server.core.testutils

import io.ktor.http.HeadersBuilder
import io.ktor.http.HttpHeaders
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.Principal
import io.ktor.server.auth.UserIdPrincipal
import io.ktor.server.auth.UserPasswordCredential
import io.ktor.server.auth.basic

import java.util.Base64

import org.ossreviewtoolkit.server.core.plugins.SecurityConfigurations

/** Credentials of a test user. */
const val TEST_USER = "user"
const val TEST_PASSWORD = "password"

/**
 * Configure alternative authentications for test execution. In the tests, token-based authentication does not work as
 * we have no valid tokens. Therefore, replace the standard configuration by a one using BasicAuth with test
 * credentials.
 */
fun Application.configureTestAuthentication() {
    install(Authentication) {
        basic(SecurityConfigurations.token) {
            validate { validateTestCredentials(it) }
        }
    }
}

/**
 * Validate whether the [credentials] passed to the server are the expected test credentials.
 */
fun validateTestCredentials(credentials: UserPasswordCredential): Principal? =
    credentials.takeIf { it.name == TEST_USER && it.password == TEST_PASSWORD }?.let { UserIdPrincipal(it.name) }

fun HeadersBuilder.basicTestAuth() {
    set(
        HttpHeaders.Authorization,
        "Basic ${Base64.getEncoder().encodeToString("$TEST_USER:$TEST_PASSWORD".toByteArray())}"
    )
}

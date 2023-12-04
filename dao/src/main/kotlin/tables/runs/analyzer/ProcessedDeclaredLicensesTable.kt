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

package org.ossreviewtoolkit.server.dao.tables.runs.analyzer

import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.LongEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.LongIdTable

import org.ossreviewtoolkit.server.model.runs.ProcessedDeclaredLicense

/**
 * A table to store the results of processing declared licenses.
 */
object ProcessedDeclaredLicensesTable : LongIdTable("processed_declared_licenses") {
    val packageId = reference("package_id", PackagesTable).nullable()
    val projectId = reference("project_id", ProjectsTable).nullable()

    val spdxExpression = text("spdx_expression")
}

class ProcessedDeclaredLicenseDao(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<ProcessedDeclaredLicenseDao>(ProcessedDeclaredLicensesTable)

    var pkg by PackageDao optionalReferencedOn ProcessedDeclaredLicensesTable.packageId
    var project by ProjectDao optionalReferencedOn ProcessedDeclaredLicensesTable.projectId

    var spdxExpression by ProcessedDeclaredLicensesTable.spdxExpression

    val mappedLicenses by MappedDeclaredLicenseDao via ProcessedDeclaredLicensesMappedDeclaredLicensesTable
    val unmappedLicenses by UnmappedDeclaredLicenseDao via ProcessedDeclaredLicensesUnmappedDeclaredLicensesTable

    fun mapToModel(): ProcessedDeclaredLicense = ProcessedDeclaredLicense(
        spdxExpression = spdxExpression,
        mappedLicenses = mappedLicenses.associate { it.declaredLicense to it.mappedLicense },
        unmappedLicenses = unmappedLicenses.mapTo(mutableSetOf()) { it.unmappedLicense }
    )
}

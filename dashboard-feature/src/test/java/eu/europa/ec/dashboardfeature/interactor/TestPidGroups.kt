/*
 * Copyright (c) 2026 European Commission
 *
 * Licensed under the EUPL, Version 1.2 or - as soon they will be approved by the European
 * Commission - subsequent versions of the EUPL (the "Licence"); You may not use this work
 * except in compliance with the Licence.
 *
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software distributed under
 * the Licence is distributed on an "AS IS" basis, WITHOUT WARRANTIES OR CONDITIONS OF
 * ANY KIND, either express or implied. See the Licence for the specific language
 * governing permissions and limitations under the Licence.
 */

package eu.europa.ec.dashboardfeature.interactor

import eu.europa.ec.corelogic.model.DocumentCategory
import eu.europa.ec.corelogic.model.DocumentIdentifier
import eu.europa.ec.dashboardfeature.ui.documents.detail.model.DocumentIssuanceStateUi
import eu.europa.ec.dashboardfeature.ui.documents.list.model.DocumentUi
import eu.europa.ec.eudi.sdjwt.vc.ClaimPathElement
import eu.europa.ec.eudi.wallet.document.format.DocumentClaim
import eu.europa.ec.eudi.wallet.document.format.MsoMdocClaim
import eu.europa.ec.eudi.wallet.document.format.SdJwtVcClaim
import eu.europa.ec.uilogic.component.ListItemDataUi
import eu.europa.ec.uilogic.component.ListItemMainContentDataUi
import eu.europa.ec.uilogic.component.ListItemSupportingContentDataUi
import junit.framework.TestCase.assertEquals
import org.junit.Assert.assertNotEquals
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertNull
import org.junit.Test
import java.time.LocalDate

// GRNET fork: a person's PIDs shown as one row in Documents.
class TestPidGroups {

    private fun row(id: String, groupKey: String?) = DocumentUi(
        documentIssuanceState = DocumentIssuanceStateUi.Issued,
        uiData = ListItemDataUi(
            itemId = id,
            mainContentData = ListItemMainContentDataUi.Text(text = id),
            overlineText = "GRNET Demo Issuer",
        ),
        documentIdentifier = DocumentIdentifier.SdJwtPid,
        documentCategory = DocumentCategory.Government,
        pidGroupKey = groupKey,
    )

    private fun group(rows: List<DocumentUi>) =
        rows.groupPids(title = { "Person Identification Data" }, supportingText = { "$it documents" })

    @Test
    fun `Given a person's three PIDs and a card, When grouped, Then the PIDs are one row where the first was`() {
        val rows = listOf(
            row("sdjwt", "issuer|doe|john|2000-01-01"),
            row("card", null),
            row("mdoc", "issuer|doe|john|2000-01-01"),
            row("mdoc-deferred", "issuer|doe|john|2000-01-01"),
        )

        val grouped = group(rows)

        assertEquals(2, grouped.size)
        val pids = grouped[0]
        assertEquals(listOf("sdjwt", "mdoc", "mdoc-deferred"), pids.groupMemberIds)
        assertEquals(
            "Person Identification Data",
            (pids.uiData.mainContentData as ListItemMainContentDataUi.Text).text,
        )
        assertEquals(
            "3 documents",
            (pids.uiData.supportingContentData as ListItemSupportingContentDataUi.Text).text,
        )
        assertEquals("GRNET Demo Issuer", pids.uiData.overlineText)
        assertEquals("card", grouped[1].uiData.itemId)
    }

    @Test
    fun `Given two persons' PIDs, When grouped, Then each person has a row of their own`() {
        val grouped = group(
            listOf(
                row("john-sdjwt", "issuer|doe|john|2000-01-01"),
                row("jane-sdjwt", "issuer|doe|jane|1999-05-05"),
                row("john-mdoc", "issuer|doe|john|2000-01-01"),
                row("jane-mdoc", "issuer|doe|jane|1999-05-05"),
            )
        )

        assertEquals(
            listOf(listOf("john-sdjwt", "john-mdoc"), listOf("jane-sdjwt", "jane-mdoc")),
            grouped.map { it.groupMemberIds },
        )
    }

    @Test
    fun `Given a single PID, or none, When grouped, Then the list is unchanged`() {
        val single = listOf(row("sdjwt", "issuer|doe|john|2000-01-01"), row("card", null))
        assertEquals(single, group(single))
        assertEquals(emptyList<DocumentUi>(), group(emptyList()))
    }

    private fun key(vararg claims: DocumentClaim, issuer: String = "GRNET Demo Issuer") =
        pidGroupKey(claims = claims.toList(), issuer = issuer)

    private fun sdJwt(name: String, value: Any) = SdJwtVcClaim(
        pathElement = ClaimPathElement.Claim(name),
        value = value,
        rawValue = value.toString(),
        issuerMetadata = null,
        selectivelyDisclosable = true,
        children = emptyList(),
    )

    private fun mdoc(name: String, value: Any) = MsoMdocClaim(
        nameSpace = "eu.europa.ec.eudi.pid.1",
        dataElementName = name,
        value = value,
        rawValue = byteArrayOf(),
        issuerMetadata = null,
    )

    @Test
    fun `Given the SD-JWT VC and the mdoc PID of one person, Then their keys are the same`() {
        val sdJwtKey = key(
            sdJwt("family_name", "Doe"), sdJwt("given_name", "John"), sdJwt("birthdate", "2000-01-01"),
        )
        val mdocKey = key(
            mdoc("family_name", "Doe"), mdoc("given_name", "John"),
            mdoc("birth_date", LocalDate.of(2000, 1, 1)),
        )

        assertNotNull(sdJwtKey)
        assertEquals(sdJwtKey, mdocKey)
    }

    @Test
    fun `Given PIDs of another person or another issuer, Then their keys differ`() {
        val john = arrayOf(sdJwt("family_name", "Doe"), sdJwt("given_name", "John"))
        val jane = arrayOf(sdJwt("family_name", "Doe"), sdJwt("given_name", "Jane"))

        assertNotEquals(key(*john), key(*jane))
        assertNotEquals(key(*john), key(*john, issuer = "Another Issuer"))
    }

    @Test
    fun `Given a PID that discloses no name or birth date, Then it has no key`() {
        assertNull(key(sdJwt("nationalities", "GR")))
    }
}

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

import eu.europa.ec.corelogic.model.DocumentIdentifier
import eu.europa.ec.dashboardfeature.ui.documents.detail.model.DocumentIssuanceStateUi
import eu.europa.ec.dashboardfeature.ui.documents.list.model.DocumentUi
import eu.europa.ec.eudi.wallet.document.IssuedDocument
import eu.europa.ec.eudi.wallet.document.format.DocumentClaim
import eu.europa.ec.eudi.wallet.document.format.MsoMdocClaim
import eu.europa.ec.eudi.wallet.document.format.SdJwtVcClaim
import eu.europa.ec.uilogic.component.AppIcons
import eu.europa.ec.uilogic.component.ListItemDataUi
import eu.europa.ec.uilogic.component.ListItemMainContentDataUi
import eu.europa.ec.uilogic.component.ListItemSupportingContentDataUi
import eu.europa.ec.uilogic.component.ListItemTrailingContentDataUi

// GRNET fork: a person's PIDs, one per format the issuer provides ("PID Combined" issues them all),
// are shown in Documents as one row, which opens a list of them. They are told apart by the
// issuer and the person they identify, not by when they were issued: a deferred PID arrives
// later, and one format may be issued again on its own.

/** The start of the item id of a group's row; the rest is its members' ids. */
internal const val PID_GROUP_ITEM_ID_PREFIX = "pid_group:"

/** Whether [identifier] is a PID's, by the same rule as "PID Combined" in Add document. */
internal fun DocumentIdentifier.isPid(): Boolean =
    this == DocumentIdentifier.SdJwtPid || this == DocumentIdentifier.MdocPid

/**
 * The key of [this] PID's group: [issuer] and the person, by family name, given names and birth
 * date, whichever the PID discloses; `null` when it discloses none of them.
 */
internal fun IssuedDocument.pidGroupKey(issuer: String): String? =
    pidGroupKey(claims = data.claims, issuer = issuer)

/** See [IssuedDocument.pidGroupKey], from a PID's [claims]. */
internal fun pidGroupKey(claims: List<DocumentClaim>, issuer: String): String? {
    fun valueOf(vararg names: String): String? = claims.firstNotNullOfOrNull { claim ->
        val name = when (claim) {
            is SdJwtVcClaim -> claim.claimName
            is MsoMdocClaim -> claim.dataElementName
            else -> null
        }
        claim.value?.takeIf { name in names }?.toString()?.trim()?.lowercase()
    }

    val familyName = valueOf("family_name")
    val givenName = valueOf("given_name")
    // An mdoc's full-date and an SD-JWT's string compare as yyyy-mm-dd.
    val birthDate = valueOf("birthdate", "birth_date")
        ?.let { Regex("""\d{4}-\d{2}-\d{2}""").find(it)?.value ?: it }

    if (familyName == null && givenName == null && birthDate == null) return null
    return listOf(issuer.trim().lowercase(), familyName, givenName, birthDate).joinToString("|")
}

/**
 * [this] list with the PIDs of each group of two or more ([DocumentUi.pidGroupKey]) replaced by
 * one row, where the first of them was, titled [title], with [supportingText] for the number of
 * documents in it. [title] is read only when there is a group.
 */
internal fun List<DocumentUi>.groupPids(
    title: () -> String,
    supportingText: (count: Int) -> String,
): List<DocumentUi> {
    val groups = filter { it.pidGroupKey != null }
        .groupBy { it.pidGroupKey }
        .filterValues { it.size >= 2 }
    if (groups.isEmpty()) return this

    val shown = mutableSetOf<String>()
    return flatMap { document ->
        val key = document.pidGroupKey
        val members = groups[key]
        when {
            key == null || members == null -> listOf(document)
            !shown.add(key) -> emptyList()
            else -> listOf(members.toGroupRow(title(), supportingText(members.size)))
        }
    }
}

private fun List<DocumentUi>.toGroupRow(title: String, supportingText: String): DocumentUi {
    val first = first()
    return DocumentUi(
        documentIssuanceState = DocumentIssuanceStateUi.Issued,
        uiData = ListItemDataUi(
            itemId = PID_GROUP_ITEM_ID_PREFIX + joinToString(",") { it.uiData.itemId },
            mainContentData = ListItemMainContentDataUi.Text(text = title),
            overlineText = first.uiData.overlineText,
            supportingContentData = ListItemSupportingContentDataUi.Text(text = supportingText),
            leadingContentData = first.uiData.leadingContentData,
            trailingContentData = ListItemTrailingContentDataUi.Icon(
                iconData = AppIcons.KeyboardArrowRight
            ),
        ),
        documentIdentifier = first.documentIdentifier,
        documentCategory = first.documentCategory,
        groupMemberIds = map { it.uiData.itemId },
    )
}

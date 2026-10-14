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

package eu.europa.ec.commonfeature.extension

import eu.europa.ec.corelogic.model.DocumentIdentifier
import eu.europa.ec.corelogic.model.toDocumentIdentifier
import eu.europa.ec.eudi.wallet.document.Document
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.resourceslogic.provider.ResourceProvider

/**
 * GRNET fork: [this] document's name where it is requested or shared: a PID as "Person
 * Identification Data", whatever its format and whatever its issuer calls it ("PID (SD-JWT VC)",
 * "PID (MSO Mdoc)"), as Android's credential selector and the Documents list name it. Any other
 * document by its own name.
 */
fun Document.nameWhenShared(resourceProvider: ResourceProvider): String =
    when (toDocumentIdentifier()) {
        DocumentIdentifier.SdJwtPid, DocumentIdentifier.MdocPid ->
            resourceProvider.getString(R.string.documents_screen_pid_group_title)

        else -> name
    }

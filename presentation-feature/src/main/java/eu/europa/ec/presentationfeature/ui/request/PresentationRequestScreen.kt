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

package eu.europa.ec.presentationfeature.ui.request

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import eu.europa.ec.commonfeature.ui.request.Event
import eu.europa.ec.commonfeature.ui.request.RequestScreen
import eu.europa.ec.uilogic.component.content.LocalShownInSheet
import eu.europa.ec.uilogic.navigation.helper.IntentAction

@Composable
fun PresentationRequestScreen(
    intentAction: IntentAction?,
    navController: NavController,
    viewModel: PresentationRequestViewModel
) {
    RequestScreen(
        intentAction = intentAction,
        navController = navController,
        viewModel = viewModel
    )

    // GRNET fork: in a sheet, a payment that the device's own prompt approves (the card's key
    // requires the user) goes on to it at once, once the request is checked: the prompt shows the
    // payment, over this screen, so the user need not tap Pay first. Once only: cancelled, the
    // prompt leaves this screen with its buttons, to pay or cancel. Not over a warning about the
    // requester, which the user must read and accept first.
    if (LocalShownInSheet.current) {
        val state by viewModel.viewState.collectAsStateWithLifecycle()
        var started by rememberSaveable { mutableStateOf(false) }
        val ready = !state.isLoading
                && state.error == null
                && !state.isBottomSheetOpen
                && state.registrationWarning == null
                && state.allowShare
                && state.keysRequireUserAuthentication
                && state.requestDataUi.selectedCombination?.transactionData?.payment != null
        LaunchedEffect(ready) {
            if (ready && !started) {
                started = true
                viewModel.setEvent(Event.StickyButtonPressed)
            }
        }
    }
}

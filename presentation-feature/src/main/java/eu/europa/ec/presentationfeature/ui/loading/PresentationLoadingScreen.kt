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

package eu.europa.ec.presentationfeature.ui.loading

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import eu.europa.ec.commonfeature.config.RequestUriConfig
import eu.europa.ec.commonfeature.ui.loading.LoadingScreen
import eu.europa.ec.commonfeature.ui.request.RequestSummary
import eu.europa.ec.presentationfeature.ui.request.PresentationRequestViewModel
import eu.europa.ec.uilogic.component.content.LocalShownInSheet
import eu.europa.ec.uilogic.navigation.PresentationScreens
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun PresentationLoadingScreen(
    navController: NavController,
    viewModel: PresentationLoadingViewModel
) {
    // GRNET fork: in a sheet, a payment stays on screen while the device's prompt approves it,
    // over the sheet: the request as its screen showed it, without its buttons.
    val requestEntry = remember(navController) {
        runCatching {
            navController.getBackStackEntry(PresentationScreens.PresentationRequest.screenRoute)
        }.getOrNull()
    }
    val payment = if (LocalShownInSheet.current && requestEntry != null) {
        val requestViewModel: PresentationRequestViewModel = koinViewModel(
            viewModelStoreOwner = requestEntry,
            parameters = {
                parametersOf(
                    requestEntry.arguments?.getString(RequestUriConfig.serializedKeyName).orEmpty()
                )
            }
        )
        val requestState by requestViewModel.viewState.collectAsStateWithLifecycle()
        requestState.takeIf {
            it.requestDataUi.selectedCombination?.transactionData?.payment != null
        }
    } else {
        null
    }

    LoadingScreen(
        navController = navController,
        viewModel = viewModel,
        content = payment?.let { requestState ->
            { paddingValues -> RequestSummary(state = requestState, paddingValues = paddingValues) }
        },
    )
}

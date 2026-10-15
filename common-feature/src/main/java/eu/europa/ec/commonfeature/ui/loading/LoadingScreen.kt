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

package eu.europa.ec.commonfeature.ui.loading

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.uilogic.component.content.ContentHeader
import eu.europa.ec.uilogic.component.content.ContentHeaderConfig
import eu.europa.ec.uilogic.component.content.ContentScreen
import eu.europa.ec.uilogic.component.content.LocalShownInSheet
import eu.europa.ec.uilogic.component.content.ScreenNavigateAction
import eu.europa.ec.uilogic.component.preview.PreviewTheme
import eu.europa.ec.uilogic.component.preview.ThemeModePreviews
import eu.europa.ec.uilogic.component.utils.OncePerViewModelEffect
import eu.europa.ec.uilogic.component.wrap.ButtonConfig
import eu.europa.ec.uilogic.component.wrap.ButtonType
import eu.europa.ec.uilogic.component.wrap.StickyBottomConfig
import eu.europa.ec.uilogic.component.wrap.StickyBottomType
import eu.europa.ec.uilogic.component.wrap.WrapStickyBottomContent
import eu.europa.ec.uilogic.extension.finish
import eu.europa.ec.uilogic.extension.openUrl
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach

@Composable
fun LoadingScreen(
    navController: NavController,
    viewModel: LoadingViewModel
) {
    val state: State by viewModel.viewState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val rejection = state.rejection
    val isRejected = rejection != null
    // GRNET fork: in a sheet, the close button is there from the start and does what it does on
    // the screen before: it cancels the request and closes the sheet. Not once the user has
    // authenticated, while the response is on its way.
    val inSheet = LocalShownInSheet.current

    val onNavigationRequested: (Effect.Navigation) -> Unit = { navigationEffect ->
        when (navigationEffect) {
            is Effect.Navigation.SwitchScreen -> {
                navController.navigate(navigationEffect.screenRoute) {
                    popUpTo(viewModel.getCallerScreen().screenRoute) {
                        inclusive = true
                    }
                }
            }

            is Effect.Navigation.PopBackStackUpTo -> {
                navController.popBackStack(
                    route = navigationEffect.screenRoute,
                    inclusive = navigationEffect.inclusive
                )
            }

            is Effect.Navigation.CloseRejection -> {
                navController.popBackStack(
                    route = navigationEffect.initiatorRoute,
                    inclusive = false,
                )
                navigationEffect.redirectUri?.let { redirectUri ->
                    context.openUrl(uri = redirectUri)
                }
            }
        }
    }

    // GRNET fork: a navigation the view model keeps until it is done, done once the screen is
    // shown, the first time or again after the activity's recreation.
    val pendingNavigation = state.pendingNavigation
    LaunchedEffect(pendingNavigation) {
        if (pendingNavigation != null) {
            viewModel.setEvent(Event.NavigationHandled)
            onNavigationRequested(pendingNavigation)
        }
    }

    ContentScreen(
        isLoading = state.error != null && !isRejected,
        navigatableAction = if (isRejected) {
            ScreenNavigateAction.NONE
        } else if (inSheet) {
            if (state.isAuthenticated) ScreenNavigateAction.NONE else ScreenNavigateAction.CANCELABLE
        } else if (state.isCancellable) {
            ScreenNavigateAction.CANCELABLE
        } else {
            ScreenNavigateAction.NONE
        },
        onBack = if (isRejected) {
            { viewModel.setEvent(Event.CloseRejection) }
        } else if (inSheet) {
            if (state.isAuthenticated) null else { { context.finish() } }
        } else if (state.isCancellable) {
            { viewModel.setEvent(Event.GoBack) }
        } else {
            null
        },
        contentErrorConfig = state.error,
        // GRNET fork: in a sheet, the close button's room even without it, as the screen before
        // has it, so that nothing moves when it goes.
        keepToolBar = inSheet,
        stickyBottom = if (rejection != null) {
            { paddingValues ->
                RejectionAction(
                    paddingValues = paddingValues,
                    enabled = !rejection.isClosing,
                    onClose = { viewModel.setEvent(Event.CloseRejection) },
                )
            }
        } else null,
    ) { paddingValues ->
        Content(
            state = state,
            effectFlow = viewModel.effect,
            onNavigationRequested = onNavigationRequested,
            paddingValues = paddingValues
        )
    }

    OncePerViewModelEffect(viewModel) {
        viewModel.setEvent(Event.Initialize)
        viewModel.setEvent(Event.DoWork(context))
    }
}

@Composable
private fun Content(
    state: State,
    effectFlow: Flow<Effect>,
    onNavigationRequested: (Effect.Navigation) -> Unit,
    paddingValues: PaddingValues
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Top
    ) {

        ContentHeader(
            modifier = Modifier.fillMaxWidth(),
            config = state.headerConfig,
        )
    }

    LaunchedEffect(Unit) {
        effectFlow.onEach { effect ->
            when (effect) {
                is Effect.Navigation -> onNavigationRequested(effect)
            }
        }.collect()
    }
}

@Composable
private fun RejectionAction(
    paddingValues: PaddingValues,
    enabled: Boolean,
    onClose: () -> Unit,
) {
    WrapStickyBottomContent(
        modifier = Modifier
            .fillMaxWidth()
            .padding(paddingValues),
        stickyBottomConfig = StickyBottomConfig(
            type = StickyBottomType.OneButton(
                config = ButtonConfig(
                    type = ButtonType.PRIMARY,
                    enabled = enabled,
                    onClick = onClose,
                ),
            ),
            showDivider = false,
        ),
    ) {
        Text(text = stringResource(R.string.generic_close))
    }
}

@ThemeModePreviews
@Composable
private fun RejectionPreview() {
    PreviewTheme {
        ContentScreen(
            navigatableAction = ScreenNavigateAction.NONE,
            onBack = {},
            stickyBottom = { paddingValues ->
                RejectionAction(
                    paddingValues = paddingValues,
                    enabled = true,
                    onClose = {},
                )
            },
        ) { paddingValues ->
            ContentHeader(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState()),
                config = ContentHeaderConfig(
                    description = null,
                    mainText = stringResource(R.string.loading_rejection_description),
                ),
            )
        }
    }
}
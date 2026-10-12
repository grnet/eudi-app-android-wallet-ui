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

package eu.europa.ec.startupfeature.ui.splash

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import eu.europa.ec.uilogic.component.AppIcons
import eu.europa.ec.uilogic.component.content.ContentErrorConfig
import eu.europa.ec.uilogic.component.content.ContentScreen
import eu.europa.ec.uilogic.component.utils.OncePerViewModelEffect
import eu.europa.ec.uilogic.component.wrap.WrapImage
import eu.europa.ec.uilogic.extension.finish
import eu.europa.ec.uilogic.extension.takePendingIntentAction
import eu.europa.ec.uilogic.navigation.ModuleRoute
import eu.europa.ec.uilogic.navigation.StartupScreens
import eu.europa.ec.uilogic.navigation.helper.IntentType
import eu.europa.ec.uilogic.navigation.helper.handleIntentAction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach

@Composable
fun SplashScreen(
    navController: NavController,
    viewModel: SplashViewModel
) {
    val state: State by viewModel.viewState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Content(
        state = state,
        effectFlow = viewModel.effect,
        onEventSend = viewModel::setEvent,
        onNavigationRequested = { navigationEffect ->
            when (navigationEffect) {
                is Effect.Navigation.Finish -> context.finish()
                is Effect.Navigation.SwitchModule -> {
                    navController.navigate(navigationEffect.moduleRoute.route) {
                        popUpTo(ModuleRoute.StartupModule.route) { inclusive = true }
                    }
                }

                is Effect.Navigation.SwitchScreen -> {
                    // GRNET fork: a pending request from a browser opens straight away, with the
                    // splash left below it, where the request screen takes its intent from.
                    val dcApiRequest = navigationEffect.dcApiRequestArguments
                        ?.let { arguments ->
                            context.takePendingIntentAction(IntentType.DC_API)
                                ?.let { action -> action to arguments }
                        }
                    if (dcApiRequest != null) {
                        handleIntentAction(
                            navController = navController,
                            action = dcApiRequest.first,
                            arguments = dcApiRequest.second,
                        )
                    } else {
                        navController.navigate(navigationEffect.route) {
                            popUpTo(StartupScreens.Splash.screenRoute) { inclusive = true }
                        }
                    }
                }
            }
        }
    )

    OncePerViewModelEffect(viewModel) {
        viewModel.setEvent(Event.Initialize)
    }
}

@Composable
private fun Content(
    state: State,
    effectFlow: Flow<Effect>,
    onEventSend: (Event) -> Unit,
    onNavigationRequested: (navigationEffect: Effect.Navigation) -> Unit
) {
    val visibilityState = remember {
        MutableTransitionState(false).apply {
            targetState = true
        }
    }

    if (state.error != null) {
        ContentScreen(
            contentErrorConfig = ContentErrorConfig(
                errorSubTitle = state.error,
                onCancel = { onEventSend(Event.Cancel) },
                onRetry = { onEventSend(Event.Retry) },
            )
        ) { }
    } else {
        Scaffold { paddingValues ->
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center
            ) {
                AnimatedVisibility(
                    visibleState = visibilityState,
                    enter = fadeIn(animationSpec = tween(state.logoAnimationDuration)),
                    exit = fadeOut(animationSpec = tween(state.logoAnimationDuration)),
                ) {
                    // GRNET fork: the gov.gr beta logo beneath the EUDI mark, so the
                    // build is told apart from the reference app from its first screen.
                    // The mark is cut to its artwork, which fills the middle 100dp of its
                    // 160dp canvas, so that the gap between the two is the one set here;
                    // gov.gr is as wide as the mark's artwork, so "BETA" can be read.
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        WrapImage(
                            iconData = AppIcons.LogoIcon,
                            modifier = Modifier.size(
                                width = SPLASH_MARK_SIZE.dp,
                                height = SPLASH_MARK_ARTWORK_HEIGHT.dp,
                            ),
                            contentScale = ContentScale.Crop,
                        )
                        WrapImage(
                            iconData = AppIcons.GovGrBeta,
                            modifier = Modifier
                                .padding(top = SPLASH_GOVGR_TOP_PADDING.dp)
                                .width(SPLASH_GOVGR_WIDTH.dp)
                                .aspectRatio(GOVGR_ASPECT_RATIO),
                        )
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        effectFlow.onEach { effect ->
            when (effect) {
                is Effect.Navigation -> onNavigationRequested(effect)
            }
        }.collect()
    }
}

/**
 * GRNET fork: the EUDI mark on the splash screen, its 160dp canvas cut to the 100dp band its
 * artwork fills (the artwork spans 30dp to 129dp of the canvas's height).
 */
private const val SPLASH_MARK_SIZE = 160
private const val SPLASH_MARK_ARTWORK_HEIGHT = 100

/** GRNET fork: the gov.gr beta logo on the splash screen: its width, and its space below the mark. */
private const val SPLASH_GOVGR_WIDTH = 144
private const val SPLASH_GOVGR_TOP_PADDING = 20

/** The gov.gr beta logo's width to height, from its drawable's viewport (502.02 by 150.61). */
private const val GOVGR_ASPECT_RATIO = 502.02f / 150.61f

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

package eu.europa.ec.assemblylogic.ui

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import eu.europa.ec.commonfeature.config.PresentationMode
import eu.europa.ec.commonfeature.config.RequestUriConfig
import eu.europa.ec.commonfeature.router.featureCommonGraph
import eu.europa.ec.presentationfeature.router.presentationGraph
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.resourceslogic.theme.ThemeManager
import eu.europa.ec.uilogic.component.content.LocalShownInSheet
import eu.europa.ec.uilogic.container.EudiComponentActivity
import eu.europa.ec.uilogic.navigation.PresentationScreens
import eu.europa.ec.uilogic.navigation.helper.IntentAction
import eu.europa.ec.uilogic.navigation.helper.generateComposableArguments
import eu.europa.ec.uilogic.navigation.helper.handleIntentAction
import eu.europa.ec.uilogic.navigation.helper.hasIntentAction
import eu.europa.ec.uilogic.serializer.UiSerializer
import org.koin.android.ext.android.inject

/**
 * GRNET fork: a request through the Digital Credentials API, shown as a sheet over the page
 * that made it instead of opening the whole wallet. The sheet runs the same screens as the
 * wallet does (the request, the authentication, the result), which return the response to the
 * caller and finish this activity, so the browser and the page stay where they were.
 *
 * As when the wallet opens on such a request, there is no unlock first: the requested values
 * stay hidden until the user authenticates to approve.
 */
class DcApiActivity : EudiComponentActivity() {

    private val uiSerializer: UiSerializer by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val intentAction = hasIntentAction(intent)
        if (intentAction == null) {
            finish()
            return
        }
        enableEdgeToEdge()
        setContent {
            ThemeManager.instance.Theme {
                CompositionLocalProvider(LocalShownInSheet provides true) {
                    RequestSheet(
                        intentAction = intentAction,
                        arguments = requestArguments(),
                        onDismiss = ::finish,
                    )
                }
            }
        }
    }

    /** The request screen's arguments, as the splash screen builds them for such a request. */
    private fun requestArguments(): String = generateComposableArguments(
        mapOf(
            RequestUriConfig.serializedKeyName to uiSerializer.toBase64(
                RequestUriConfig(
                    PresentationMode.DcApi(
                        initiatorRoute = PresentationScreens.PresentationRequest.screenRoute,
                        userAuthenticated = false,
                    )
                ),
                RequestUriConfig.Parser
            )
        )
    )
}

private const val START_ROUTE = "DC_API_SHEET_START"

// Of the screen's height; the rest shows the page behind.
private const val SHEET_HEIGHT_FRACTION = 0.7f

/**
 * The sheet: the screens in a panel along the bottom, over the caller's page. A tap on the page
 * cancels the request while it is being shown, as Cancel does; once the user has approved it,
 * only the screens' own buttons close the sheet, so that the response is not lost.
 */
@Composable
private fun RequestSheet(
    intentAction: IntentAction,
    arguments: String,
    onDismiss: () -> Unit,
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val showingRequest =
        backStackEntry?.destination?.route == PresentationScreens.PresentationRequest.screenRoute
    val visible = remember { MutableTransitionState(false).apply { targetState = true } }

    Box(modifier = Modifier.fillMaxSize()) {
        // No scrim of our own: the page is already dimmed. Android's credential selector, which
        // started this activity, leaves its dim under this window, which is see-through, and a
        // scrim here dimmed the page a second time. What is left of it is where a tap cancels.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    enabled = showingRequest,
                    onClickLabel = stringResource(R.string.request_cancel_button_text),
                    onClick = onDismiss,
                )
        )

        AnimatedVisibility(
            visibleState = visible,
            enter = slideInVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(SHEET_HEIGHT_FRACTION)
                    // The sheet starts below the status bar; its screens must not pad for it.
                    .consumeWindowInsets(WindowInsets.statusBars),
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                color = MaterialTheme.colorScheme.surface,
            ) {
                NavHost(
                    navController = navController,
                    startDestination = START_ROUTE,
                ) {
                    // Hands the request to its screen as the splash screen does, through this
                    // entry's saved state.
                    composable(START_ROUTE) {
                        LaunchedEffect(Unit) {
                            handleIntentAction(navController, intentAction, arguments)
                        }
                    }
                    presentationGraph(navController)
                    featureCommonGraph(navController)
                }
            }
        }
    }
}

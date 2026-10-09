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

package eu.europa.ec.startupfeature.interactor

import eu.europa.ec.businesslogic.config.ConfigLogic
import eu.europa.ec.businesslogic.controller.storage.PrefKeys
import eu.europa.ec.commonfeature.config.BiometricMode
import eu.europa.ec.commonfeature.config.BiometricUiConfig
import eu.europa.ec.commonfeature.config.IssuanceFlowType
import eu.europa.ec.commonfeature.config.IssuanceUiConfig
import eu.europa.ec.commonfeature.config.OnBackNavigationConfig
import eu.europa.ec.commonfeature.config.PresentationMode
import eu.europa.ec.commonfeature.config.RequestUriConfig
import eu.europa.ec.commonfeature.config.TrustMarkMode
import eu.europa.ec.commonfeature.config.TrustMarkUiConfig
import eu.europa.ec.commonfeature.interactor.QuickPinInteractor
import eu.europa.ec.commonfeature.model.PinFlow
import eu.europa.ec.corelogic.controller.WalletCoreDocumentsController
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import eu.europa.ec.uilogic.config.ConfigNavigation
import eu.europa.ec.uilogic.config.NavigationType
import eu.europa.ec.uilogic.navigation.CommonScreens
import eu.europa.ec.uilogic.navigation.DashboardScreens
import eu.europa.ec.uilogic.navigation.IssuanceScreens
import eu.europa.ec.uilogic.navigation.PresentationScreens
import eu.europa.ec.uilogic.navigation.helper.generateComposableArguments
import eu.europa.ec.uilogic.navigation.helper.generateComposableNavigationLink
import eu.europa.ec.uilogic.serializer.UiSerializer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed interface SplashRoutePartialState {
    /**
     * @property dcApiRequestArguments GRNET fork: the arguments of the request screen, to open a
     * pending request from a browser (DC API) right after the splash, without unlocking the
     * wallet first, or `null` if the wallet is not set up for that.
     */
    data class Success(
        val route: String,
        val dcApiRequestArguments: String? = null,
    ) : SplashRoutePartialState
    data class Failure(val error: String) : SplashRoutePartialState
}

interface SplashInteractor {
    suspend fun getAfterSplashRoute(): SplashRoutePartialState
}

class SplashInteractorImpl(
    private val quickPinInteractor: QuickPinInteractor,
    private val uiSerializer: UiSerializer,
    private val resourceProvider: ResourceProvider,
    private val walletCoreDocumentsController: WalletCoreDocumentsController,
    private val configLogic: ConfigLogic,
    private val prefKeys: PrefKeys,
) : SplashInteractor {

    private val hasDocuments: Boolean
        get() = walletCoreDocumentsController.getAllDocuments().isNotEmpty()

    private val shouldActivateWithPid: Boolean
        get() = configLogic.forcePidActivation && !hasDocuments

    override suspend fun getAfterSplashRoute(): SplashRoutePartialState =
        withContext(Dispatchers.IO) {
            runCatching {
                val continuationRoute = if (quickPinInteractor.hasPin()) {
                    getBiometricsConfig()
                } else {
                    getQuickPinConfig()
                }
                val route = if (prefKeys.getTrustMarkIntroductionCompleted()) {
                    continuationRoute
                } else {
                    val config = uiSerializer.toBase64(
                        model = TrustMarkUiConfig(
                            mode = TrustMarkMode.Welcome(continuationRoute = continuationRoute)
                        ),
                        parser = TrustMarkUiConfig.Parser,
                    )
                    if (config.isNullOrBlank()) {
                        return@runCatching SplashRoutePartialState.Failure(
                            error = resourceProvider.genericErrorMessage()
                        )
                    }
                    generateComposableNavigationLink(
                        screen = CommonScreens.TrustMark,
                        arguments = generateComposableArguments(
                            mapOf(TrustMarkUiConfig.serializedKeyName to config)
                        ),
                    )
                }
                // GRNET fork: a request from a browser goes straight to the request screen, and
                // the user authenticates once, to approve it, not first to unlock the wallet
                // too (UAegean PA2 requirements §3.2.1: no authentication merely to open the
                // confirmation screen). Only for a wallet that is set up, with documents.
                val dcApiRequestArguments = if (
                    quickPinInteractor.hasPin()
                    && prefKeys.getTrustMarkIntroductionCompleted()
                    && hasDocuments
                ) {
                    getDcApiRequestArguments()
                } else {
                    null
                }
                SplashRoutePartialState.Success(
                    route = route,
                    dcApiRequestArguments = dcApiRequestArguments,
                )
            }.getOrElse {
                SplashRoutePartialState.Failure(error = resourceProvider.genericErrorMessage())
            }
        }

    private fun getDcApiRequestArguments(): String {
        return generateComposableArguments(
            mapOf(
                RequestUriConfig.serializedKeyName to uiSerializer.toBase64(
                    RequestUriConfig(
                        PresentationMode.DcApi(
                            // The request finishes the activity whichever way it ends, and
                            // never returns to a screen behind the unlock.
                            initiatorRoute = PresentationScreens.PresentationRequest.screenRoute,
                            userAuthenticated = false,
                        )
                    ),
                    RequestUriConfig.Parser
                )
            )
        )
    }

    private fun getQuickPinConfig(): String {
        return generateComposableNavigationLink(
            screen = CommonScreens.QuickPin,
            arguments = generateComposableArguments(
                mapOf(
                    "pinFlow" to if (shouldActivateWithPid) {
                        PinFlow.CREATE_WITH_ACTIVATION
                    } else {
                        PinFlow.CREATE_WITHOUT_ACTIVATION
                    }
                )
            )
        )
    }

    private fun getBiometricsConfig(): String {

        val shouldActivateWithPid = configLogic.forcePidActivation && !hasDocuments

        return generateComposableNavigationLink(
            screen = CommonScreens.Biometric,
            arguments = generateComposableArguments(
                mapOf(
                    BiometricUiConfig.serializedKeyName to uiSerializer.toBase64(
                        BiometricUiConfig(
                            mode = BiometricMode.Login(
                                title = resourceProvider.getString(R.string.biometric_login_title),
                                subTitleWhenBiometricsEnabled = resourceProvider.getString(R.string.biometric_login_biometrics_enabled_subtitle),
                                subTitleWhenBiometricsNotEnabled = resourceProvider.getString(R.string.biometric_login_biometrics_not_enabled_subtitle),
                            ),
                            isPreAuthorization = true,
                            shouldInitializeBiometricAuthOnCreate = true,
                            onSuccessNavigation = ConfigNavigation(
                                navigationType = NavigationType.PushScreen(
                                    screen = if (!shouldActivateWithPid) {
                                        DashboardScreens.Dashboard
                                    } else {
                                        IssuanceScreens.AddDocument
                                    },
                                    arguments = if (shouldActivateWithPid) {
                                        mapOf(
                                            IssuanceUiConfig.serializedKeyName to uiSerializer.toBase64(
                                                model = IssuanceUiConfig(
                                                    flowType = IssuanceFlowType.NoDocument
                                                ),
                                                parser = IssuanceUiConfig.Parser
                                            )
                                        )
                                    } else {
                                        emptyMap()
                                    }
                                )
                            ),
                            onBackNavigationConfig = OnBackNavigationConfig(
                                onBackNavigation = ConfigNavigation(
                                    navigationType = NavigationType.Finish
                                ),
                                hasToolbarBackIcon = false
                            )
                        ),
                        BiometricUiConfig.Parser
                    ).orEmpty()
                )
            )
        )
    }
}
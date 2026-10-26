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

import com.android.build.api.dsl.LibraryExtension
import com.android.build.api.variant.LibraryAndroidComponentsExtension
import com.google.android.libraries.mapsplatform.secrets_gradle_plugin.SecretsPluginExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.dependencies
import project.convention.logic.addConfigField
import project.convention.logic.config.LibraryModule
import project.convention.logic.config.LibraryPluginConfig
import project.convention.logic.configureFlavors
import project.convention.logic.configureGradleManagedDevices
import project.convention.logic.configureKotlinAndroid
import project.convention.logic.configurePrintApksTask
import project.convention.logic.disableUnnecessaryAndroidTests
import project.convention.logic.libs

class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {

        with(target) {

            val config =
                extensions.create<LibraryPluginConfig>("moduleConfig", LibraryModule.Unspecified)

            val walletScheme = "eudi-wallet"
            val walletHost = "*"

            val eudiOpenId4VpScheme = "eudi-openid4vp"
            val eudiOpenid4VpHost = "*"

            val mdocOpenId4VpScheme = "mdoc-openid4vp"
            val mdocOpenid4VpHost = "*"

            val openId4VpScheme = "openid4vp"
            val openid4VpHost = "*"

            val haipOpenId4VpScheme = "haip-vp"
            val haipOpenid4VpHost = "*"

            val credentialOfferScheme = "openid-credential-offer"
            val credentialOfferHost = "*"

            val credentialOfferHaipScheme = "haip-vci"
            val credentialOfferHaipHost = "*"

            // GRNET fork: the schemes the Launchpad testbed and its participants use,
            // for presentation requests and credential offers.
            val eaapOpenId4VpScheme = "eu-eaap"
            val eaapOpenid4VpHost = "*"

            val credentialOfferEaaScheme = "eu-eaa-offer"
            val credentialOfferEaaHost = "*"

            val openId4VciAuthorizationScheme = "eu.europa.ec.euidi"
            val openId4VciAuthorizationHost = "authorization"

            val rqesScheme = "rqes"
            val rqesHost = "oauth"
            val rqesPath = "/callback"

            // TODO This is temporary until proper value
            val rqesDocRetrievalScheme = "eudi-rqes"
            val rqesDocRetrievalHost = "*"

            with(pluginManager) {
                apply("com.android.library")
                apply("project.android.library.kover")
                apply("project.android.lint")
                apply("project.android.koin")
                apply("kotlinx-serialization")
                apply("com.google.android.libraries.mapsplatform.secrets-gradle-plugin")
                apply("kotlin-parcelize")
            }

            extensions.configure<LibraryExtension>("android") {
                configureKotlinAndroid(this)
                with(defaultConfig) {

                    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

                    addConfigField("DEEPLINK", "$walletScheme://")
                    addConfigField("EUDI_OPENID4VP_SCHEME", eudiOpenId4VpScheme)
                    addConfigField("MDOC_OPENID4VP_SCHEME", mdocOpenId4VpScheme)
                    addConfigField("OPENID4VP_SCHEME", openId4VpScheme)
                    addConfigField("HAIP_OPENID4VP_SCHEME", haipOpenId4VpScheme)
                    addConfigField("CREDENTIAL_OFFER_SCHEME", credentialOfferScheme)
                    addConfigField("CREDENTIAL_OFFER_HAIP_SCHEME", credentialOfferHaipScheme)
                    addConfigField("EAAP_OPENID4VP_SCHEME", eaapOpenId4VpScheme)
                    addConfigField("CREDENTIAL_OFFER_EAA_SCHEME", credentialOfferEaaScheme)
                    addConfigField("ISSUE_AUTHORIZATION_SCHEME", openId4VciAuthorizationScheme)
                    addConfigField("ISSUE_AUTHORIZATION_HOST", openId4VciAuthorizationHost)
                    addConfigField(
                        "ISSUE_AUTHORIZATION_DEEPLINK",
                        "$openId4VciAuthorizationScheme://$openId4VciAuthorizationHost"
                    )
                    addConfigField("RQES_SCHEME", rqesScheme)
                    addConfigField("RQES_HOST", rqesHost)
                    addConfigField("RQES_DEEPLINK", "$rqesScheme://$rqesHost$rqesPath")
                    addConfigField("RQES_DOC_RETRIEVAL_SCHEME", rqesDocRetrievalScheme)

                    // GRNET fork: the dev flavour's backend, as build arguments.
                    //   ./gradlew assembleDevRelease \
                    //       -PissuerUrls=https://a.example/issuer,https://b.example \
                    //       -PwalletProviderUrl=https://c.example/wallet-provider
                    // issuerUrls is comma-separated, one VciConfig per entry. The
                    // defaults are upstream's dev flavour values, so a build without
                    // the properties behaves exactly as upstream's.
                    addConfigField(
                        "ISSUER_URLS",
                        providers.gradleProperty("issuerUrls").getOrElse(
                            "https://ec.dev.issuer.eudiw.dev,https://dev.issuer-backend.eudiw.dev"
                        )
                    )
                    // The trusted lists: "ri", upstream's (the default), or "ec", the
                    // Commission's for the Launchpad testbed. emptyTrustLists, with "ec"
                    // only: any of pid,wrpac,wrprc, comma-separated, read from the list's
                    // empty twin instead, for the testbed's negative cases.
                    addConfigField(
                        "TRUST_LISTS",
                        providers.gradleProperty("trustLists").getOrElse("ri")
                    )
                    addConfigField(
                        "EMPTY_TRUST_LISTS",
                        providers.gradleProperty("emptyTrustLists").getOrElse("")
                    )
                    // Whether GRNET's own CAs back up the lists (GrnetTrust). On by
                    // default, for the payment demo; off for the testbed's negative cases.
                    addConfigField(
                        "GRNET_TRUST_ANCHORS",
                        providers.gradleProperty("grnetTrustAnchors")
                            .map { it.toBoolean() }
                            .getOrElse(true)
                    )
                    // Whether "Check Registration Certificates" starts on, until the user
                    // changes it in Settings. Off by default, as upstream, for the payment
                    // demo; on for the Launchpad testbed, whose PRE-09 to PRE-14 expect
                    // the wallet to warn about registration certificates.
                    addConfigField(
                        "REGISTRATION_CHECK_DEFAULT",
                        providers.gradleProperty("registrationCheckDefault")
                            .map { it.toBoolean() }
                            .getOrElse(false)
                    )
                    addConfigField(
                        "WALLET_PROVIDER_URL",
                        providers.gradleProperty("walletProviderUrl").getOrElse(
                            "https://dev.wallet-provider.eudiw.dev"
                        )
                    )

                    // Manifest placeholders for Wallet deepLink
                    manifestPlaceholders["deepLinkScheme"] = walletScheme
                    manifestPlaceholders["deepLinkHost"] = walletHost

                    // Manifest placeholders used for OpenId4VP
                    manifestPlaceholders["eudiOpenid4vpScheme"] = eudiOpenId4VpScheme
                    manifestPlaceholders["eudiOpenid4vpHost"] = eudiOpenid4VpHost
                    manifestPlaceholders["mdocOpenid4vpScheme"] = mdocOpenId4VpScheme
                    manifestPlaceholders["mdocOpenid4vpHost"] = mdocOpenid4VpHost
                    manifestPlaceholders["openid4vpScheme"] = openId4VpScheme
                    manifestPlaceholders["openid4vpHost"] = openid4VpHost
                    manifestPlaceholders["haipOpenid4vpScheme"] = haipOpenId4VpScheme
                    manifestPlaceholders["haipOpenid4vpHost"] = haipOpenid4VpHost

                    // Manifest placeholders used for OpenId4VCI
                    manifestPlaceholders["credentialOfferHost"] = credentialOfferHost
                    manifestPlaceholders["credentialOfferScheme"] = credentialOfferScheme
                    manifestPlaceholders["credentialOfferHaipHost"] = credentialOfferHaipHost
                    manifestPlaceholders["credentialOfferHaipScheme"] = credentialOfferHaipScheme

                    manifestPlaceholders["eaapOpenid4vpScheme"] = eaapOpenId4VpScheme
                    manifestPlaceholders["eaapOpenid4vpHost"] = eaapOpenid4VpHost
                    manifestPlaceholders["credentialOfferEaaScheme"] = credentialOfferEaaScheme
                    manifestPlaceholders["credentialOfferEaaHost"] = credentialOfferEaaHost

                    // Manifest placeholders used for OpenId4VCI Authorization
                    manifestPlaceholders["openId4VciAuthorizationScheme"] =
                        openId4VciAuthorizationScheme
                    manifestPlaceholders["openId4VciAuthorizationHost"] =
                        openId4VciAuthorizationHost

                    // Manifest placeholders used for RQES
                    manifestPlaceholders["rqesHost"] = rqesHost
                    manifestPlaceholders["rqesScheme"] = rqesScheme
                    manifestPlaceholders["rqesPath"] = rqesPath

                    // Manifest placeholders used for RQES Document Retrieval
                    manifestPlaceholders["rqesDocRetrievalScheme"] = rqesDocRetrievalScheme
                    manifestPlaceholders["rqesDocRetrievalHost"] = rqesDocRetrievalHost
                }
                configureFlavors(this)
                configureGradleManagedDevices(this)
            }
            extensions.configure<LibraryAndroidComponentsExtension> {
                configurePrintApksTask(this)
                disableUnnecessaryAndroidTests(target)
            }
            extensions.configure<SecretsPluginExtension> {
                defaultPropertiesFileName = "secrets.defaults.properties"
                ignoreList.add("sdk.*")
            }
            dependencies {
                add("implementation", libs.findLibrary("kotlinx-coroutines-android").get())
                add("implementation", libs.findLibrary("kotlinx-coroutines-guava").get())
                add("implementation", libs.findLibrary("kotlinx.serialization.json").get())
                add("implementation", libs.findLibrary("androidx-work-ktx").get())
            }
            afterEvaluate {
                if (!config.module.isLogicModule && !config.module.isFeatureCommon) {
                    dependencies {
                        add("implementation", project(LibraryModule.CommonFeature.path))
                    }
                }
            }
        }
    }
}

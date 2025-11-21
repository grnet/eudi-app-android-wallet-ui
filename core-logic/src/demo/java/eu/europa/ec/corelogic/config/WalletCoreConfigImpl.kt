/*
 * Copyright (c) 2023 European Commission
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

package eu.europa.ec.corelogic.config

import android.content.Context
import eu.europa.ec.corelogic.BuildConfig
import eu.europa.ec.eudi.wallet.EudiWalletConfig
import eu.europa.ec.eudi.wallet.issue.openid4vci.OpenId4VciManager
import eu.europa.ec.eudi.wallet.transfer.openId4vp.ClientIdScheme
import eu.europa.ec.eudi.wallet.transfer.openId4vp.Format
import eu.europa.ec.resourceslogic.R
import kotlin.time.Duration.Companion.seconds

internal class WalletCoreConfigImpl(
    private val context: Context
) : WalletCoreConfig {

    private var _config: EudiWalletConfig? = null

    override val config: EudiWalletConfig
        get() {
            if (_config == null) {
                _config = EudiWalletConfig {
                    configureDocumentKeyCreation(
                        userAuthenticationRequired = false,
                        userAuthenticationTimeout = 30.seconds,
                        useStrongBoxForKeys = true
                    )
                    configureOpenId4Vp {
                        withClientIdSchemes(
                            listOf(
                                ClientIdScheme.X509SanDns,
                                ClientIdScheme.X509Hash
                            )
                        )
                        withSchemes(
                            listOf(
                                BuildConfig.OPENID4VP_SCHEME,
                                BuildConfig.EUDI_OPENID4VP_SCHEME,
                                BuildConfig.MDOC_OPENID4VP_SCHEME
                            )
                        )
                        withFormats(
                            Format.MsoMdoc.ES256, Format.SdJwtVc.ES256
                        )
                    }

                    configureReaderTrustStore(
                        context,
                        R.raw.pidissuerca02_cz,
                        R.raw.pidissuerca02_ee,
                        R.raw.pidissuerca02_eu,
                        R.raw.pidissuerca02_lu,
                        R.raw.pidissuerca02_nl,
                        R.raw.pidissuerca02_pt,
                        R.raw.pidissuerca02_ut,
                        R.raw.dc4eu,

                        // POTENTIAL playground
                        R.raw.intermediaire, R.raw.issuer, R.raw.root,
                        // Czech root (Warsaw)
                        R.raw.czech_root,
                        // GRNET IACA
                        // R.raw.root_ca_grnet,
                        R.raw.iaca,
//                        // https://github.com/eu-digital-identity-wallet/eudi-srv-web-verifier-endpoint-23220-4-kt/issues/256
//                        // Certificate generated from: https://registry.serviceproviders.eudiw.dev/
//                        R.raw.grnet
                        // Utrecht reader certificates
                        R.raw.animo_reader_ca,
                        R.raw.bundesdruckerei_reader_ca,
                        R.raw.clr_labs_reader_ca,
                        R.raw.credence_id_reader_ca,
                        R.raw.fast_enterprises_reader_ca,
                        R.raw.fime_reader_ca_1,
                        R.raw.fime_reader_ca_2,
                        R.raw.google_reader_ca,
                        R.raw.idakto_reader_ca,
                        R.raw.idemia_reader_ca,
                        R.raw.in_groupe_reader_ca,
                        R.raw.lapid_reader_ca,
                        R.raw.lt_reader_ca,
                        R.raw.mattr_reader_ca,
                        R.raw.nearform_reader_ca,
                        R.raw.ogcio_reader_ca,
                        R.raw.panasonic_reader_ca,
                        R.raw.rdw_test_reader_ca,
                        R.raw.scytales_reader_ca,
                        R.raw.spruceid_reader_ca,
                        R.raw.thales_reader_ca_1,
                        R.raw.thales_reader_ca_2,
                        R.raw.thales_root_ca,
                        R.raw.toppan_reader_ca,
                        R.raw.veridos_reader_ca,
                        R.raw.zetes_reader_ca,
                        // Online interop event reader certificates (April '25)
                        R.raw.a_sit_verifier,
                        R.raw.a_sit_verifier_ca,
                        R.raw.amadeus_travelready,
                        R.raw.animo_root,
                        R.raw.iaca_ants_fr_idakto_4,
                        R.raw.ants_fr_idakto_reader_trusted_certificates,
                        R.raw.idakto_vp,
                        R.raw.citybee_iaca_lt_potential,
                        R.raw.verifier_citybee_ds_0001_lt_dev,
                        R.raw.lapid_ca,
                        R.raw.mattr_dc_api_verifier,
                        R.raw.mattr_main_verifier,
                        R.raw.nf_verifier,
                        R.raw.panasonic_connect_iaca_cacert,
                        R.raw.panasonic_connect_reader,
                        R.raw.toppan_goid_mdl_reader_ca_cer,
                        R.raw.toppan_hid_global_certificate,
                        R.raw.toppan_mdl_iaca,
                        R.raw.zetes_ca,
                        R.raw.funke_animo_id,
                        R.raw.lapid_reader,
                        // POTENTIAL tests
                        R.raw.lissi1,
                        R.raw.lissi2,
                        R.raw.lissi3,
                    )
                }
            }
            return _config!!
        }

    override val vciConfig: List<OpenId4VciManager.Config>
        get() = listOf(
            OpenId4VciManager.Config.Builder()
                .withIssuerUrl(issuerUrl = "https://issuer.eudiw.dev")
                .withClientId(clientId = "wallet-dev")
                .withAuthFlowRedirectionURI(BuildConfig.ISSUE_AUTHORIZATION_DEEPLINK)
                .withParUsage(OpenId4VciManager.Config.ParUsage.IF_SUPPORTED)
                //.withDPoPUsage(OpenId4VciManager.Config.DPoPUsage.IfSupported())
                .withDPoPUsage(OpenId4VciManager.Config.DPoPUsage.Disabled)
                .build(),
            OpenId4VciManager.Config.Builder()
                .withIssuerUrl(issuerUrl = "https://issuer-backend.eudiw.dev")
                .withClientId(clientId = "wallet-dev")
                .withAuthFlowRedirectionURI(BuildConfig.ISSUE_AUTHORIZATION_DEEPLINK)
                .withParUsage(OpenId4VciManager.Config.ParUsage.IF_SUPPORTED)
                //.withDPoPUsage(OpenId4VciManager.Config.DPoPUsage.IfSupported())
                .withDPoPUsage(OpenId4VciManager.Config.DPoPUsage.Disabled)
                .build()
        )
}

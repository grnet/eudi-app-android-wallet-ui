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
 * the Licence is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF
 * ANY KIND, either express or implied. See the Licence for the specific language
 * governing permissions and limitations under the Licence.
 */

package eu.europa.ec.corelogic.config

import android.content.Context
import com.nimbusds.jose.JWSVerifier
import com.nimbusds.jose.crypto.ECDSAVerifier
import com.nimbusds.jose.crypto.RSASSAVerifier
import com.nimbusds.jwt.SignedJWT
import eu.europa.ec.eudi.etsi119602.consultation.DownloadSingleLoTE
import eu.europa.ec.eudi.etsi119602.consultation.LoadLoTEAndPointers
import eu.europa.ec.eudi.etsi119602.consultation.LoadSingleLoTEWithFileCache
import eu.europa.ec.eudi.etsi119602.consultation.LotEMeta
import eu.europa.ec.eudi.etsi119602.consultation.ProvisionTrustAnchorsFromLoTEs
import eu.europa.ec.eudi.etsi119602.consultation.VerifyJwtSignature
import eu.europa.ec.eudi.etsi119602.consultation.eu
import eu.europa.ec.eudi.etsi119602.consultation.eudiwJvm
import eu.europa.ec.eudi.etsi119602.datamodel.Uri
import eu.europa.ec.eudi.etsi1196x2.consultation.CertificationChainValidation
import eu.europa.ec.eudi.etsi1196x2.consultation.DisposableContainer
import eu.europa.ec.eudi.etsi1196x2.consultation.GetTrustAnchors
import eu.europa.ec.eudi.etsi1196x2.consultation.IsChainTrustedForContext
import eu.europa.ec.eudi.etsi1196x2.consultation.IsChainTrustedForEUDIW
import eu.europa.ec.eudi.etsi1196x2.consultation.NonEmptyList
import eu.europa.ec.eudi.etsi1196x2.consultation.SensitiveApi
import eu.europa.ec.eudi.etsi1196x2.consultation.SupportedLists
import eu.europa.ec.eudi.etsi1196x2.consultation.ValidateCertificateChain
import eu.europa.ec.eudi.etsi1196x2.consultation.ValidateCertificateChainUsingDirectTrustJvm
import eu.europa.ec.eudi.etsi1196x2.consultation.ValidateCertificateChainUsingPKIXJvm
import eu.europa.ec.eudi.etsi1196x2.consultation.VerificationContext
import eu.europa.ec.eudi.etsi1196x2.consultation.or
import eu.europa.ec.resourceslogic.R
import io.ktor.client.HttpClient
import kotlinx.coroutines.CancellationException
import kotlinx.io.files.Path
import java.security.cert.CertificateFactory
import java.security.cert.TrustAnchor
import java.security.cert.X509Certificate
import java.security.interfaces.ECPublicKey
import java.security.interfaces.RSAPublicKey
import kotlin.time.Duration.Companion.minutes

/**
 * GRNET fork: issuer and relying-party trust for the dev flavour, the EU trusted lists first and
 * GRNET's own CAs when those do not trust a chain.
 *
 * wallet-core builds its trusted-list source internally from `configureEtsiTrust` and does not
 * expose it, so a source that adds anchors to it has to be built beside it. [euLists] rebuilds
 * the same pipeline from the same public ETSI library, the way wallet-core 0.30.2's
 * `EtsiTrustProvider` does, and [grnet] is tried only when it answers NotTrusted.
 *
 * The GRNET anchors, in the dev flavour's `res/raw`:
 *
 * - `grnet_iaca`, the IACA in WEBUILD/pki, for PIDs and for access certificates. The issuer's
 *   document signer is a leaf under it and signs the mdoc PIDs and the issuer's metadata.
 * - `webuild_trust_registry`, the WE BUILD Trust Registry root, for PIDs and for access
 *   certificates. GRNET's WE BUILD-onboarded certificate is a leaf under it, the one on the
 *   Commission's PID providers list, and signs the SD-JWT VC PIDs.
 * - `grnet_pid_provider`, GRNET's PID provider certificate in the ETSI TS 119 412-6 profile
 *   (WEBUILD/pki/etsi-profile), for PIDs. Self-signed, so its own anchor; the same key as the
 *   WE BUILD-onboarded one, and the certificate the issuer signs SD-JWT VC PIDs with once the
 *   Commission's PID providers list carries it.
 *
 * Revocation is not checked for either, as the dev flavour already does for the EU lists
 * (`relaxPkixRevocation`): Android's PKIX does not fetch a CRL from a certificate's
 * distribution point by itself.
 *
 * [SensitiveApi]: the library's name for handing a validator anchors directly rather than
 * reading them from a trusted list, which is exactly what this class is for.
 */
@OptIn(SensitiveApi::class)
internal class GrnetTrust(
    context: Context,
    httpClient: HttpClient,
    loteLocations: SupportedLists<Uri>,
    loteSignerAnchors: List<X509Certificate>,
    private val withGrnetAnchors: Boolean = true,
) {

    private val disposables = DisposableContainer()

    private val pkix = ValidateCertificateChainUsingPKIXJvm(
        customization = { isRevocationEnabled = false }
    )

    private val euLists = ProvisionTrustAnchorsFromLoTEs.eudiwJvm(
        loadLoTEAndPointers = LoadLoTEAndPointers(
            constraints = LoadLoTEAndPointers.Constraints.DoNotLoadOtherPointers,
            // The lists' signers checked against the same anchors as wallet-core's, the same way.
            verifyJwtSignature = LoteSignatureVerifier(
                signerAnchors = NonEmptyList(loteSignerAnchors.map { TrustAnchor(it, null) }),
                validateSigner = ValidateCertificateChainUsingDirectTrustJvm or pkix,
            ),
            // Its own directory: wallet-core's pipeline caches the same lists in lote-cache.
            loadLoTE = LoadSingleLoTEWithFileCache(
                cacheDirectory = Path(context.cacheDir.path, "lote-cache-grnet"),
                downloadSingleLoTE = DownloadSingleLoTE(httpClient),
            ),
        ),
        // As relaxCertificateProfiles() does for the dev flavour's EU lists.
        svcTypePerCtx = SupportedLists.eu().withoutEndEntityProfiles(),
        pkix = pkix,
    ).cached(disposables, loteLocations, ttl = 20.minutes) // wallet-core's default cacheTtl

    private val grnet: IsChainTrustedForContext<List<X509Certificate>, VerificationContext, TrustAnchor> =
        run {
            val iaca = context.trustAnchor(R.raw.grnet_iaca)
            val webuild = context.trustAnchor(R.raw.webuild_trust_registry)
            val pidProvider = context.trustAnchor(R.raw.grnet_pid_provider)
            val anchors: Map<VerificationContext, List<TrustAnchor>> = mapOf(
                VerificationContext.PID to listOf(iaca, webuild, pidProvider),
                VerificationContext.WalletRelyingPartyAccessCertificate to listOf(iaca, webuild),
            )
            IsChainTrustedForContext(
                anchors.keys,
                object : GetTrustAnchors<VerificationContext, TrustAnchor> {
                    override suspend fun invoke(query: VerificationContext): NonEmptyList<TrustAnchor>? =
                        NonEmptyList.nelOrNull(anchors[query].orEmpty())
                },
                pkix,
            )
        }

    /**
     * The EU lists, then GRNET's anchors for the contexts it has any for; the lists alone
     * without [withGrnetAnchors].
     */
    val source: IsChainTrustedForEUDIW<List<X509Certificate>, TrustAnchor> =
        if (withGrnetAnchors) {
            euLists.recoverWith { context, _ -> grnet.takeIf { it.contains(context) } }
        } else {
            euLists
        }
}

private fun Context.trustAnchor(rawRes: Int): TrustAnchor =
    resources.openRawResource(rawRes).use {
        TrustAnchor(
            CertificateFactory.getInstance("X.509").generateCertificate(it) as X509Certificate,
            null
        )
    }

/** wallet-core's `relaxProfiles`, which it keeps private. */
private fun SupportedLists<LotEMeta<VerificationContext>>.withoutEndEntityProfiles():
        SupportedLists<LotEMeta<VerificationContext>> {
    fun LotEMeta<VerificationContext>.relax() = copy(
        svcTypePerCtx = svcTypePerCtx.mapValues { (_, v) -> v.copy(endEntityProfile = null) }
    )
    return copy(
        pidProviders = pidProviders?.relax(),
        walletProviders = walletProviders?.relax(),
        wrpacProviders = wrpacProviders?.relax(),
        wrprcProviders = wrprcProviders?.relax(),
        pubEaaProviders = pubEaaProviders?.relax(),
        qeaProviders = qeaProviders?.relax(),
        eaaProviders = eaaProviders.mapValues { (_, v) -> v.relax() },
    )
}

/**
 * wallet-core's `LoteJwtVerifier` (0.32), which it keeps internal, written the same way so that
 * this copy can be dropped once wallet-core exposes it: the `x5c` leaf is valid today and chains
 * to one of [signerAnchors], and the list's JWS signature verifies against the leaf's public key,
 * EC (upstream's lists, ES256) or RSA (the Commission's, RS512).
 */
internal class LoteSignatureVerifier(
    private val signerAnchors: NonEmptyList<TrustAnchor>,
    private val validateSigner: ValidateCertificateChain<List<X509Certificate>, TrustAnchor>,
) : VerifyJwtSignature {
    override suspend fun invoke(jwt: String): VerifyJwtSignature.Outcome = try {
        val signedJwt = SignedJWT.parse(jwt)
        val x5c = signedJwt.header.x509CertChain
        if (x5c.isNullOrEmpty()) {
            VerifyJwtSignature.Outcome.NotVerified(
                IllegalArgumentException("JWT header does not contain x5c certificate chain")
            )
        } else {
            val certificateFactory = CertificateFactory.getInstance("X.509")
            val chain = x5c.map {
                certificateFactory.generateCertificate(it.decode().inputStream()) as X509Certificate
            }
            val signingCert = chain.first()
            signingCert.checkValidity()
            when (val validation = validateSigner(chain, signerAnchors)) {
                is CertificationChainValidation.NotTrusted ->
                    VerifyJwtSignature.Outcome.NotVerified(validation.cause)

                is CertificationChainValidation.Trusted ->
                    verifySignature(signedJwt, signingCert, jwt)
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
        VerifyJwtSignature.Outcome.NotVerified(e)
    }

    private fun verifySignature(
        signedJwt: SignedJWT,
        signingCert: X509Certificate,
        jwt: String,
    ): VerifyJwtSignature.Outcome {
        val verifier: JWSVerifier = when (val publicKey = signingCert.publicKey) {
            is ECPublicKey -> ECDSAVerifier(publicKey)
            is RSAPublicKey -> RSASSAVerifier(publicKey)
            else -> return VerifyJwtSignature.Outcome.NotVerified(
                UnsupportedOperationException("Unsupported key type: ${publicKey.algorithm}")
            )
        }
        return if (signedJwt.verify(verifier)) {
            VerifyJwtSignature.Outcome.Verified(jwt)
        } else {
            VerifyJwtSignature.Outcome.NotVerified(
                IllegalStateException("LoTE JWT signature verification failed")
            )
        }
    }
}

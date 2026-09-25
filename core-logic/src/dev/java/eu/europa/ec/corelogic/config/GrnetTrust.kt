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
import eu.europa.ec.eudi.etsi119602.consultation.DownloadSingleLoTE
import eu.europa.ec.eudi.etsi119602.consultation.LoadLoTEAndPointers
import eu.europa.ec.eudi.etsi119602.consultation.LoadSingleLoTEWithFileCache
import eu.europa.ec.eudi.etsi119602.consultation.LotEMeta
import eu.europa.ec.eudi.etsi119602.consultation.ProvisionTrustAnchorsFromLoTEs
import eu.europa.ec.eudi.etsi119602.consultation.VerifyJwtSignature
import eu.europa.ec.eudi.etsi119602.consultation.eu
import eu.europa.ec.eudi.etsi119602.consultation.eudiwJvm
import eu.europa.ec.eudi.etsi119602.datamodel.Uri
import eu.europa.ec.eudi.etsi1196x2.consultation.DisposableContainer
import eu.europa.ec.eudi.etsi1196x2.consultation.GetTrustAnchors
import eu.europa.ec.eudi.etsi1196x2.consultation.IsChainTrustedForContext
import eu.europa.ec.eudi.etsi1196x2.consultation.IsChainTrustedForEUDIW
import eu.europa.ec.eudi.etsi1196x2.consultation.NonEmptyList
import eu.europa.ec.eudi.etsi1196x2.consultation.SensitiveApi
import eu.europa.ec.eudi.etsi1196x2.consultation.SupportedLists
import eu.europa.ec.eudi.etsi1196x2.consultation.ValidateCertificateChainUsingPKIXJvm
import eu.europa.ec.eudi.etsi1196x2.consultation.VerificationContext
import eu.europa.ec.resourceslogic.R
import io.ktor.client.HttpClient
import kotlinx.io.files.Path
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.math.BigInteger
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.cert.TrustAnchor
import java.security.cert.X509Certificate
import java.security.interfaces.ECPublicKey
import java.util.Base64
import kotlin.time.Duration.Companion.minutes

/**
 * GRNET fork: issuer trust for the dev flavour, the EU trusted lists first and GRNET's own
 * CAs when those do not trust a chain.
 *
 * wallet-core builds its trusted-list source internally from `configureEtsiTrust` and does not
 * expose it, so a source that adds anchors to it has to be built beside it. [euLists] rebuilds
 * the same pipeline from the same public ETSI library, the way wallet-core 0.30.2's
 * `EtsiTrustProvider` does, and [grnet] is tried only when it answers NotTrusted.
 *
 * The GRNET anchor is `grnet_iaca` in the dev flavour's `res/raw`, the IACA in WEBUILD/pki,
 * trusted for PIDs and for access certificates. The issuer's document signer is a leaf under it
 * and signs both the PIDs and the issuer's metadata.
 *
 * Revocation is not checked, as the dev flavour already does for the EU lists
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
) {

    private val disposables = DisposableContainer()

    private val pkix = ValidateCertificateChainUsingPKIXJvm(
        customization = { isRevocationEnabled = false }
    )

    private val euLists = ProvisionTrustAnchorsFromLoTEs.eudiwJvm(
        loadLoTEAndPointers = LoadLoTEAndPointers(
            constraints = LoadLoTEAndPointers.Constraints.DoNotLoadOtherPointers,
            verifyJwtSignature = LoteSignatureVerifier,
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
            // WE BUILD Trust Registry root: pending confirmation of its fingerprint, see .github/README.md
            // val webuild = context.trustAnchor(R.raw.webuild_trust_registry)
            val anchors: Map<VerificationContext, List<TrustAnchor>> = mapOf(
                VerificationContext.PID to listOf(iaca),
                VerificationContext.WalletRelyingPartyAccessCertificate to listOf(iaca /*, webuild */),
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

    /** The EU lists, then GRNET's anchors for the contexts it has any for. */
    val source: IsChainTrustedForEUDIW<List<X509Certificate>, TrustAnchor> =
        euLists.recoverWith { context, _ -> grnet.takeIf { it.contains(context) } }
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
 * What wallet-core's internal `LoteJwtVerifier` checks, and no more: the list's JWS signature
 * against the public key of the `x5c` leaf. Not whether that leaf is trusted. The lists are
 * all ES256; an EC signature arrives as r||s and Java wants it DER-encoded.
 */
internal object LoteSignatureVerifier : VerifyJwtSignature {
    override suspend fun invoke(jwt: String): VerifyJwtSignature.Outcome = runCatching {
        val (header, payload, signature) = jwt.split('.').also { require(it.size == 3) }
        val headerJson = Json.parseToJsonElement(String(base64Url(header))).jsonObject
        val digest = when (val alg = headerJson.getValue("alg").jsonPrimitive.content) {
            "ES256" -> "SHA256withECDSA"
            "ES384" -> "SHA384withECDSA"
            "ES512" -> "SHA512withECDSA"
            else -> error("unsupported alg $alg")
        }
        // x5c is plain base64, unlike the rest of a JWS (RFC 7515 §4.1.6).
        val leaf = CertificateFactory.getInstance("X.509").generateCertificate(
            Base64.getDecoder()
                .decode(headerJson.getValue("x5c").jsonArray[0].jsonPrimitive.content)
                .inputStream()
        )
        require(leaf.publicKey is ECPublicKey) { "x5c leaf key is not EC" }
        val verified = Signature.getInstance(digest).run {
            initVerify(leaf.publicKey)
            update("$header.$payload".toByteArray(Charsets.US_ASCII))
            verify(rawToDer(base64Url(signature)))
        }
        require(verified) { "LoTE JWT signature verification failed" }
        VerifyJwtSignature.Outcome.Verified(jwt)
    }.getOrElse { VerifyJwtSignature.Outcome.NotVerified(it) }

    private fun base64Url(s: String): ByteArray = Base64.getUrlDecoder().decode(s)

    private fun rawToDer(raw: ByteArray): ByteArray {
        val half = raw.size / 2
        fun int(bytes: ByteArray): ByteArray {
            val value = BigInteger(1, bytes).toByteArray()
            return byteArrayOf(0x02, value.size.toByte()) + value
        }
        val body = int(raw.copyOfRange(0, half)) + int(raw.copyOfRange(half, raw.size))
        // Long-form length for P-521, whose SEQUENCE body can pass 127 bytes.
        val length = if (body.size < 128) byteArrayOf(body.size.toByte())
        else byteArrayOf(0x81.toByte(), body.size.toByte())
        return byteArrayOf(0x30) + length + body
    }
}

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

import eu.europa.ec.eudi.etsi119602.consultation.VerifyJwtSignature
import eu.europa.ec.eudi.etsi1196x2.consultation.NonEmptyList
import eu.europa.ec.eudi.etsi1196x2.consultation.SensitiveApi
import eu.europa.ec.eudi.etsi1196x2.consultation.ValidateCertificateChainUsingDirectTrustJvm
import eu.europa.ec.eudi.etsi1196x2.consultation.ValidateCertificateChainUsingPKIXJvm
import eu.europa.ec.eudi.etsi1196x2.consultation.or
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigInteger
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.cert.TrustAnchor
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64

/**
 * GRNET fork: [LoteSignatureVerifier] trusts a list only when its signer chains to one of the
 * configured anchors and its signature verifies, as wallet-core's own LoTE verifier does since
 * 0.32.
 *
 * The fixtures in `resources/lote` are a test CA, a leaf it issued with the leaf's key, and an
 * unrelated CA, all valid until 2126.
 */
@OptIn(SensitiveApi::class)
class TestLoteSignatureVerifier {

    private val ca = certificate("lote/ca.pem")
    private val otherCa = certificate("lote/other_ca.pem")
    private val leaf = certificate("lote/leaf.pem")
    private val leafKey = privateKey("lote/leaf.key")

    private val validateSigner = ValidateCertificateChainUsingDirectTrustJvm or
            ValidateCertificateChainUsingPKIXJvm(customization = { isRevocationEnabled = false })

    @Test
    fun `Given a list signed by a leaf of the anchor CA, When verified, Then it is Verified`() =
        runTest {
            val outcome = verifier(anchor = ca)(signedList())

            assertTrue(outcome.toString(), outcome is VerifyJwtSignature.Outcome.Verified)
        }

    @Test
    fun `Given a list signed by a leaf of another CA, When verified, Then it is NotVerified`() =
        runTest {
            val outcome = verifier(anchor = otherCa)(signedList())

            assertTrue(outcome.toString(), outcome is VerifyJwtSignature.Outcome.NotVerified)
        }

    @Test
    fun `Given a list whose payload was changed after signing, When verified, Then it is NotVerified`() =
        runTest {
            val (header, _, signature) = signedList().split('.')
            val tampered = "$header.${base64Url("""{"LoTE":"changed"}""".toByteArray())}.$signature"

            val outcome = verifier(anchor = ca)(tampered)

            assertTrue(outcome.toString(), outcome is VerifyJwtSignature.Outcome.NotVerified)
        }

    @Test
    fun `Given a list without an x5c header, When verified, Then it is NotVerified`() =
        runTest {
            val outcome = verifier(anchor = ca)(signedList(header = """{"alg":"ES256"}"""))

            assertTrue(outcome.toString(), outcome is VerifyJwtSignature.Outcome.NotVerified)
        }

    private fun verifier(anchor: X509Certificate) = LoteSignatureVerifier(
        signerAnchors = NonEmptyList(listOf(TrustAnchor(anchor, null))),
        validateSigner = validateSigner,
    )

    private fun signedList(
        header: String = """{"alg":"ES256","x5c":["${Base64.getEncoder().encodeToString(leaf.encoded)}"]}""",
    ): String {
        val signingInput = "${base64Url(header.toByteArray())}.${base64Url("""{"LoTE":{}}""".toByteArray())}"
        val der = Signature.getInstance("SHA256withECDSA").run {
            initSign(leafKey)
            update(signingInput.toByteArray(Charsets.US_ASCII))
            sign()
        }
        return "$signingInput.${base64Url(derToRaw(der))}"
    }

    private fun certificate(resource: String): X509Certificate =
        stream(resource).use {
            CertificateFactory.getInstance("X.509").generateCertificate(it) as X509Certificate
        }

    private fun privateKey(resource: String): PrivateKey {
        val pem = stream(resource).use { String(it.readBytes()) }
        val der = Base64.getMimeDecoder().decode(
            pem.substringAfter("-----BEGIN PRIVATE KEY-----").substringBefore("-----END PRIVATE KEY-----")
        )
        return KeyFactory.getInstance("EC").generatePrivate(PKCS8EncodedKeySpec(der))
    }

    private fun stream(resource: String) =
        requireNotNull(javaClass.classLoader?.getResourceAsStream(resource)) { "no $resource" }

    private fun base64Url(bytes: ByteArray): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    /** A DER ECDSA signature as the r||s a JWS carries, 32 bytes each for P-256. */
    private fun derToRaw(der: ByteArray): ByteArray {
        var offset = if (der[1].toInt() and 0x80 != 0) 3 else 2
        fun next(): ByteArray {
            check(der[offset].toInt() == 0x02) { "not an INTEGER" }
            val length = der[offset + 1].toInt()
            val value = der.copyOfRange(offset + 2, offset + 2 + length)
            offset += 2 + length
            return BigInteger(1, value).toByteArray().takeLast(32).toByteArray()
                .let { ByteArray(32 - it.size) + it }
        }
        return next() + next()
    }
}

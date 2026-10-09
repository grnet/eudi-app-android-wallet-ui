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

package eu.europa.ec.corelogic.transactiondata

import eu.europa.ec.authenticationlogic.model.DeviceAuthenticationMethod
import java.util.Base64
import java.util.UUID
import kotlinx.io.bytestring.ByteString
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test
import org.multipaz.crypto.Algorithm
import org.multipaz.presentment.TransactionProtocol

// GRNET fork: TS12 card payments, as the WE BUILD PA2 relying party sends them.
class TestScaPaymentTransactionType {

    // The transaction_data entry of UAegean's Fast Ferries checkout, captured 2026-10-06.
    private val mockedRequest = """
        {"type":"urn:eudi:sca:payment:1","credential_ids":["sca_card_dpc"],
         "transaction_data_hashes_alg":["sha-256"],
         "payload":{"transaction_id":"ff-1791254240824-xqei0x","date_time":"2026-10-06T02:37:21.010Z",
                    "payee":{"name":"Fast Ferries","id":"fast-ferries-demo"},"currency":"EUR","amount":38}}
    """.trimIndent()

    @Test
    fun `Given the relying party's payment, When parsed, Then every field is read`() {
        // When
        val payment = ScaPaymentTransactionType.parseOpenId4VpRequest(mockedRequest)

        // Then
        assertEquals(ScaPayment.TYPE, payment.type)
        assertEquals(listOf("sca_card_dpc"), payment.credentialIds)
        assertEquals(listOf("sha-256"), payment.hashAlgorithms)
        assertEquals("ff-1791254240824-xqei0x", payment.payload.transactionId)
        assertEquals("2026-10-06T02:37:21.010Z", payment.payload.dateTime)
        assertEquals("Fast Ferries", payment.payload.payee.name)
        assertEquals("fast-ferries-demo", payment.payload.payee.id)
        assertEquals("EUR", payment.payload.currency)
        assertEquals("38", payment.payload.amount.content)
    }

    @Test
    fun `Given the payment as transmitted, When parseJson is called, Then its hash algorithm and bytes are kept`() {
        // Given
        val transmitted = ByteString(
            Base64.getUrlEncoder().withoutPadding()
                .encodeToString(mockedRequest.toByteArray())
                .toByteArray()
        )

        // When
        val transactionData = ScaPaymentTransactionType.parseJson(transmitted)

        // Then
        assertSame(ScaPaymentTransactionType, transactionData.type)
        assertEquals(TransactionProtocol.OPENID4VP, transactionData.protocol)
        assertEquals(transmitted, transactionData.rawBytes)
        assertEquals(listOf(Algorithm.SHA256), transactionData.hashAlgorithms)
        assertEquals("Fast Ferries", transactionData.payload.payload.payee.name)
    }

    @Test
    fun `Given TS12 members this wallet does not show, When parsed, Then they are ignored`() {
        // Given
        val request = mockedRequest.replace(
            "\"amount\":38}",
            "\"amount\":38,\"amount_estimated\":false,\"pisp\":{\"legal_name\":\"PISP\"}}",
        )

        // When
        val payment = ScaPaymentTransactionType.parseOpenId4VpRequest(request)

        // Then
        assertEquals("38", payment.payload.amount.content)
    }

    @Test
    fun `Given a minimal payment, When parsed, Then optional fields are absent`() {
        // When
        val payment = ScaPaymentTransactionType.parseOpenId4VpRequest(
            """{"type":"urn:eudi:sca:payment:1","credential_ids":["q"],
               "payload":{"payee":{"name":"Payee"},"currency":"EUR","amount":12.345}}"""
        )

        // Then
        assertNull(payment.hashAlgorithms)
        assertNull(payment.payload.transactionId)
        assertNull(payment.payload.dateTime)
        assertNull(payment.payload.payee.id)
        assertEquals(JsonPrimitive(12.345).content, payment.payload.amount.content)
    }

    @Test
    fun `Given a payment the user could not be shown properly, When parsed, Then it is rejected`() {
        val invalid = listOf(
            // another type
            mockedRequest.replace("urn:eudi:sca:payment:1", "urn:eudi:sca:login:1"),
            // bound to no credential
            mockedRequest.replace("[\"sca_card_dpc\"]", "[]"),
            // no amount
            mockedRequest.replace(",\"amount\":38", ""),
            // an amount that is not a number
            mockedRequest.replace("\"amount\":38", "\"amount\":\"38\""),
            // no currency
            mockedRequest.replace("\"currency\":\"EUR\",", ""),
            // a payee without a name
            mockedRequest.replace("\"name\":\"Fast Ferries\"", "\"name\":\" \""),
        )

        invalid.forEach { request ->
            assertThrows(request, IllegalArgumentException::class.java) {
                ScaPaymentTransactionType.parseOpenId4VpRequest(request)
            }
        }
    }

    // GRNET fork: the Key Binding JWT claims of CS-12 §7.3 item 8.

    @Test
    fun `Given the user unlocked the card's key with a biometric, When the claims are made, Then a jti and the amr of possession and inherence are added`() {
        // Given
        ScaKeyAuthentication.record(DeviceAuthenticationMethod.BIOMETRIC)

        // When
        val claims = ScaPaymentTransactionType.keyBindingClaims(transactionData = emptyList())

        // Then
        val jti = (claims.getValue("jti") as JsonPrimitive).content
        assertEquals(jti, UUID.fromString(jti).toString())
        assertEquals(
            JsonArray(
                listOf(
                    JsonObject(mapOf("possession" to JsonPrimitive("device_bound_key"))),
                    JsonObject(mapOf("inherence" to JsonPrimitive("biometric_device"))),
                )
            ),
            claims["amr"],
        )
    }

    @Test
    fun `Given the user unlocked the card's key with the screen lock, When the claims are made, Then the amr has possession and knowledge`() {
        // Given
        ScaKeyAuthentication.record(DeviceAuthenticationMethod.DEVICE_CREDENTIAL)

        // When
        val claims = ScaPaymentTransactionType.keyBindingClaims(transactionData = emptyList())

        // Then
        assertEquals(
            JsonObject(mapOf("knowledge" to JsonPrimitive("screen_lock_device"))),
            (claims["amr"] as JsonArray)[1],
        )
    }

    @Test
    fun `Given no key was unlocked by the user, or the system did not say how, When the claims are made, Then no amr is claimed`() {
        listOf(null, DeviceAuthenticationMethod.UNKNOWN).forEach { method ->
            // Given
            ScaKeyAuthentication.clear()
            method?.let { ScaKeyAuthentication.record(it) }

            // When
            val claims = ScaPaymentTransactionType.keyBindingClaims(transactionData = emptyList())

            // Then
            assertNull(claims["amr"])
            assertNotNull(claims["jti"])
        }
    }

    @Test
    fun `Given an authentication was recorded, When two presentations are signed, Then only the first claims it and each has its own jti`() {
        // Given
        ScaKeyAuthentication.record(DeviceAuthenticationMethod.BIOMETRIC)

        // When
        val first = ScaPaymentTransactionType.keyBindingClaims(transactionData = emptyList())
        val second = ScaPaymentTransactionType.keyBindingClaims(transactionData = emptyList())

        // Then
        assertNotNull(first["amr"])
        assertNull(second["amr"])
        assertNotEquals(first["jti"], second["jti"])
    }
}

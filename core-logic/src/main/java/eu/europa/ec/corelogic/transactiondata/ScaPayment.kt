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

import kotlinx.io.bytestring.ByteString
import kotlinx.io.bytestring.decodeToString
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import org.multipaz.documenttype.TransactionType
import org.multipaz.presentment.TransactionData
import org.multipaz.presentment.TransactionProtocol
import org.multipaz.util.fromBase64Url

// GRNET fork: TS12 card payment transaction data, as the WE BUILD PA2 relying party sends it.
// wallet-core declares only the QES types, so the app declares this one beside them.
//
// TS12's payment object has optional members this wallet does not show (the PISP, execution
// date, recurrence and so on), so unknown members are ignored rather than rejected, unlike the
// QES types. What the user is asked to approve is required: payee, amount and currency.
private val json: Json = Json { ignoreUnknownKeys = true }

/**
 * A payment the relying party asks the user to approve, of type [TYPE]. Its hash is bound into the
 * presentation of the payment credential that [credentialIds] names.
 */
@Serializable
data class ScaPayment(
    @SerialName("type")
    val type: String,
    @SerialName("credential_ids")
    val credentialIds: List<String>,
    @SerialName("transaction_data_hashes_alg")
    val hashAlgorithms: List<String>? = null,
    @SerialName("payload")
    val payload: ScaPaymentPayload,
) {
    init {
        require(type == TYPE) { "ScaPayment: 'type' must be '$TYPE', was '$type'" }
        require(credentialIds.isNotEmpty()) { "ScaPayment: 'credential_ids' must not be empty" }
        require(hashAlgorithms == null || hashAlgorithms.isNotEmpty()) {
            "ScaPayment: 'transaction_data_hashes_alg' must not be empty"
        }
    }

    companion object {
        const val TYPE = "urn:eudi:sca:payment:1"
    }
}

@Serializable
data class ScaPaymentPayload(
    @SerialName("transaction_id")
    val transactionId: String? = null,
    @SerialName("date_time")
    val dateTime: String? = null,
    @SerialName("payee")
    val payee: ScaPaymentPayee,
    @SerialName("currency")
    val currency: String,
    /** A JSON number, kept as received so that no digit is lost or rounded. */
    @SerialName("amount")
    val amount: JsonPrimitive,
) {
    init {
        require(currency.isNotBlank()) { "ScaPayment: 'currency' must not be blank" }
        require(!amount.isString && amount.content.toBigDecimalOrNull() != null) {
            "ScaPayment: 'amount' must be a number, was '$amount'"
        }
    }
}

@Serializable
data class ScaPaymentPayee(
    @SerialName("name")
    val name: String,
    @SerialName("id")
    val id: String? = null,
) {
    init {
        require(name.isNotBlank()) { "ScaPayment: the payee's 'name' must not be blank" }
    }
}

/**
 * The transaction data type of [ScaPayment]. A payload it cannot read is reported to the relying
 * party as `invalid_transaction_data`.
 */
object ScaPaymentTransactionType : TransactionType<ScaPayment>(
    displayName = "Payment",
    identifier = ScaPayment.TYPE,
) {
    override fun parseOpenId4VpRequest(jsonString: String): ScaPayment =
        json.decodeFromString(ScaPayment.serializer(), jsonString)

    override fun parseJson(serialized: ByteString): TransactionData<ScaPayment> {
        val payment = parseOpenId4VpRequest(
            serialized.decodeToString().fromBase64Url().decodeToString()
        )
        return TransactionData(
            type = this,
            payload = payment,
            protocol = TransactionProtocol.OPENID4VP,
            rawBytes = serialized,
            hashAlgorithms = parseJoseHashAlgorithms(payment.hashAlgorithms),
        )
    }
}

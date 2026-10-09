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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * GRNET fork: how the user authenticated to unlock a key that requires it, such as a payment
 * card's, for the presentation being signed. Recorded when the system's prompt for that key
 * succeeds; taken, once, by [ScaPaymentTransactionType] for the Key Binding JWT's `amr`.
 */
object ScaKeyAuthentication {

    @Volatile
    private var method: DeviceAuthenticationMethod? = null

    fun record(method: DeviceAuthenticationMethod) {
        this.method = method
    }

    fun clear() {
        method = null
    }

    /**
     * The `amr` of CS-12 §7.3 item 8 for the authentication recorded, if any, and clears it: the
     * key's possession, and the factor the user unlocked it with; at least two categories. `null`
     * when no key was unlocked by the user for this presentation, or the system did not say how,
     * so that no factor is claimed that was not used.
     */
    fun takeAmr(): JsonArray? {
        val factor = when (method.also { method = null }) {
            DeviceAuthenticationMethod.BIOMETRIC -> INHERENCE to BIOMETRIC_DEVICE
            DeviceAuthenticationMethod.DEVICE_CREDENTIAL -> KNOWLEDGE to SCREEN_LOCK_DEVICE
            DeviceAuthenticationMethod.UNKNOWN, null -> return null
        }
        return JsonArray(
            listOf(
                buildJsonObject { put(POSSESSION, JsonPrimitive(DEVICE_BOUND_KEY)) },
                buildJsonObject { put(factor.first, JsonPrimitive(factor.second)) },
            )
        )
    }

    // The categories of CS-12 §7.3 item 8. It names the methods only by example
    // ("pin_6_or_more_digits", "face_device"); these follow that pattern.
    private const val POSSESSION = "possession"
    private const val INHERENCE = "inherence"
    private const val KNOWLEDGE = "knowledge"

    /** The key in the device's keystore (StrongBox where available) that the card is bound to. */
    private const val DEVICE_BOUND_KEY = "device_bound_key"

    /** A biometric of the device: a fingerprint or the face; the system does not say which. */
    private const val BIOMETRIC_DEVICE = "biometric_device"

    /** The device's screen lock: its PIN, pattern or password; the system does not say which. */
    private const val SCREEN_LOCK_DEVICE = "screen_lock_device"
}

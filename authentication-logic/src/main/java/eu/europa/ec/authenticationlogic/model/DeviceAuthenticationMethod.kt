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

package eu.europa.ec.authenticationlogic.model

import androidx.biometric.BiometricPrompt

/**
 * GRNET fork: how the user authenticated at the system's prompt.
 */
enum class DeviceAuthenticationMethod {
    /** A biometric, such as a fingerprint or the face. */
    BIOMETRIC,

    /** The device's screen lock: its PIN, pattern or password. */
    DEVICE_CREDENTIAL,

    /** Not reported by the system. */
    UNKNOWN;

    companion object {
        fun of(result: BiometricPrompt.AuthenticationResult?): DeviceAuthenticationMethod =
            when (result?.authenticationType) {
                BiometricPrompt.AUTHENTICATION_RESULT_TYPE_BIOMETRIC -> BIOMETRIC
                BiometricPrompt.AUTHENTICATION_RESULT_TYPE_DEVICE_CREDENTIAL -> DEVICE_CREDENTIAL
                else -> UNKNOWN
            }
    }
}

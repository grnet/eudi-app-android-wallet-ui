/**
 * Adapter for self-signed certificates, added according to instructions in how_to_build.md.
 */

import android.annotation.SuppressLint
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.logging.Logging
import java.security.SecureRandom
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import javax.security.cert.CertificateException

object ProvideKtorHttpClient {

    @SuppressLint("TrustAllX509TrustManager", "CustomX509TrustManager")
    fun client(): HttpClient {
        val trustAllCerts = arrayOf<TrustManager>(
            object : X509TrustManager {
                @Throws(CertificateException::class)
                override fun checkClientTrusted(
                    chain: Array<java.security.cert.X509Certificate>,
                    authType: String
                ) {
                }

                @Throws(CertificateException::class)
                override fun checkServerTrusted(
                    chain: Array<java.security.cert.X509Certificate>,
                    authType: String
                ) {
                }

                override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> {
                    return arrayOf()
                }
            }
        )

        return HttpClient(Android) {
            install(Logging)
            engine {
                requestConfig
                sslManager = { httpsURLConnection ->
                    httpsURLConnection.sslSocketFactory = SSLContext.getInstance("TLS").apply {
                        init(null, trustAllCerts, SecureRandom())
                    }.socketFactory
                    httpsURLConnection.hostnameVerifier = HostnameVerifier { _, _ -> true }
                }
            }
        }
    }

}

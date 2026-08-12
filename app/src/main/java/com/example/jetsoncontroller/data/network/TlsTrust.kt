package com.example.jetsoncontroller.data.network

import android.annotation.SuppressLint
import java.security.MessageDigest
import java.security.cert.Certificate
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.X509TrustManager

internal fun certificateSha256(certificate: Certificate): String =
    MessageDigest.getInstance("SHA-256")
        .digest(certificate.encoded)
        .joinToString("") { "%02x".format(it) }

@SuppressLint("CustomX509TrustManager")
internal class HelloBootstrapTrustManager : X509TrustManager {
    var lastServerCertificateSha256: String? = null
        private set

    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        throw CertificateException("Client certificates are not supported")
    }

    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        val leaf = chain?.firstOrNull()
            ?: throw CertificateException("The Jetson did not present a TLS certificate")
        leaf.checkValidity()
        lastServerCertificateSha256 = certificateSha256(leaf)
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
}

internal class PinnedCertificateTrustManager(
    private val expectedFingerprint: String
) : X509TrustManager {
    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        throw CertificateException("Client certificates are not supported")
    }

    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        val leaf = chain?.firstOrNull()
            ?: throw CertificateException("The Jetson did not present a TLS certificate")
        leaf.checkValidity()
        if (!certificateSha256(leaf).equals(expectedFingerprint, ignoreCase = true)) {
            throw CertificateException("The Jetson TLS certificate changed")
        }
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
}

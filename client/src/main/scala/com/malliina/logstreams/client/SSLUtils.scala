package com.malliina.logstreams.client

import java.security.cert.X509Certificate
import javax.net.ssl.{SSLContext, TrustManager, X509TrustManager}

object SSLUtils {
  /**
    * @return an SSL context that trusts all certificates
    */
  def trustAllSslContext() = {
    val sslContext = SSLContext.getInstance("TLS")
    sslContext.init(null, Array[TrustManager](trustAllTrustManager()), null)
    sslContext
  }

  def trustAllTrustManager() = new X509TrustManager() {
    override def checkClientTrusted(chain: Array[X509Certificate], authType: String) {
    }

    override def checkServerTrusted(chain: Array[X509Certificate], authType: String) {
    }

    override def getAcceptedIssuers: Array[X509Certificate] = null
  }
}

package com.malliina.logstreams.html

import com.malliina.logstreams.HashedAssets
import org.http4s.Uri
import com.malliina.http.FullUrl

trait AssetsSource:
  def at(file: String): Uri

object AssetsSource:
  def apply(isProd: Boolean): AssetsSource =
    if isProd then CDNAssets(FullUrl.https("logs-cdn.malliina.com", ""))
    else HashedAssetsSource

object HashedAssetsSource extends AssetsSource:
  override def at(file: String): Uri =
    val optimal = HashedAssets.assets.getOrElse(file, file)
    Uri.unsafeFromString(s"/assets/$optimal")

class CDNAssets(cdnBaseUrl: FullUrl) extends AssetsSource:
  override def at(file: String): Uri =
    val optimal = HashedAssets.assets.getOrElse(file, file)
    val url = cdnBaseUrl / "assets" / optimal
    Uri.unsafeFromString(url.url)

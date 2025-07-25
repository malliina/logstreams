package com.malliina.logstreams.html

import com.malliina.logstreams.HashedAssets
import org.http4s.Uri
import com.malliina.http.FullUrl
import org.http4s.implicits.uri

trait AssetsSource:
  def at(file: String): Uri

object AssetsSource:
  def apply(isProd: Boolean): AssetsSource =
    if isProd then HashedAssetsSource
    else DirectAssets

object DirectAssets extends AssetsSource:
  override def at(file: String): Uri = Uri.unsafeFromString(s"/assets/$file")

object HashedAssetsSource extends AssetsSource:
  override def at(file: String): Uri =
    val optimal = HashedAssets.assets.getOrElse(file, file)
    Uri.unsafeFromString(s"/assets/$optimal")

class CDNAssets(cdnBaseUrl: FullUrl) extends AssetsSource:
  override def at(file: String): Uri =
    val optimal = HashedAssets.assets.getOrElse(file, file)
    val url = cdnBaseUrl / "assets" / optimal
    Uri.unsafeFromString(url.url)

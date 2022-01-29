package com.malliina.logstreams.html

import com.malliina.logstreams.HashedAssets
import org.http4s.Uri

trait AssetsSource:
  def at(file: String): Uri

object HashedAssetsSource extends AssetsSource:
  override def at(file: String): Uri =
    val optimal = HashedAssets.assets.getOrElse(file, file)
    Uri.unsafeFromString(s"/assets/$optimal")

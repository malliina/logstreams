package com.malliina.logstreams.html

import com.malliina.logstreams.HashedAssets
import org.http4s.Uri
import org.http4s.implicits.uri

trait AssetsSource:
  def at(file: String): Uri

object AssetsSource:
  def apply(isProd: Boolean): AssetsSource =
    if isProd then HashedAssetsSource
    else DirectAssets

object DirectAssets extends AssetsSource:
  override def at(file: String): Uri =
    uri"/assets".addPath(file)

object HashedAssetsSource extends AssetsSource:
  override def at(file: String): Uri =
    val optimal = HashedAssets.assets.getOrElse(file, file)
    uri"/assets".addPath(optimal)

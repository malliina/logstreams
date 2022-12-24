package com.malliina.app

import java.nio.file.Paths

import com.malliina.logstreams.db.Conf
import com.typesafe.config.ConfigFactory

trait AppConf:
  def database: Conf
  def close(): Unit

object AppConf:
  def apply(conf: Conf) = new AppConf:
    override def database: Conf = conf
    override def close(): Unit = ()

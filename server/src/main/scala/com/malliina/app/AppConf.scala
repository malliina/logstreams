package com.malliina.app

import com.malliina.database.Conf

trait AppConf:
  def database: Conf
  def close(): Unit

object AppConf:
  def apply(conf: Conf) = new AppConf:
    override def database: Conf = conf
    override def close(): Unit = ()

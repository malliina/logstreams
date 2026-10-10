package com.malliina.logstreams.db

import com.malliina.logstreams.db.DoobieMappings.given
import com.malliina.logstreams.models.Language
import com.malliina.values.Email
import doobie.Read

import java.time.Instant

case class Admin(email: Email, language: Language, createdAt: Instant) derives Read

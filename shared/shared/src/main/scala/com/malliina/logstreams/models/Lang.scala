package com.malliina.logstreams.models

import com.malliina.values.StringEnumCompanion

case class ProfileLang(language: String, english: String)

case class CalendarLang(from: String, to: String)

case class ResultsLang(
  app: String,
  time: String,
  message: String,
  logger: String,
  thread: String,
  client: String,
  userAgent: String,
  level: String,
  noResults: String
)

case class LogsLang(
  title: String,
  verbose: String,
  compact: String,
  apps: String,
  calendar: CalendarLang,
  search: String,
  searchPlaceholder: String,
  results: ResultsLang
)

case class ServersLang(
  title: String,
  app: String,
  address: String,
  userAgent: String,
  id: String,
  joined: String,
  unknown: String
)

case class UsersLang(
  title: String,
  username: String,
  password: String,
  action: String,
  addUser: String,
  deleteUser: String
)

case class NavLang(appName: String, logs: String, sources: String, users: String)

case class Lang(
  nav: NavLang,
  logs: LogsLang,
  servers: ServersLang,
  users: UsersLang,
  profile: ProfileLang
)

object Lang:
  val cookieName = "lang"

  val en = Lang(
    NavLang("logstreams", "Logs", "Sources", "Users"),
    LogsLang(
      "Logs",
      "Verbose",
      "Compact",
      "Apps",
      CalendarLang("From", "To"),
      "Search",
      "Message, stacktrace, thread, ...",
      ResultsLang(
        "App",
        "Time",
        "Message",
        "Logger",
        "Thread",
        "Client",
        "User Agent",
        "Level",
        "No results"
      )
    ),
    ServersLang("Servers", "App", "Address", "User-Agent", "ID", "Joined", "Unknown"),
    UsersLang("Users", "Username", "Password", "Aciton", "Add User", "Delete"),
    ProfileLang("Language", "English")
  )
  val default = en

enum Language(val code: String):
  case English extends Language("en-US")
  case Finnish extends Language("fi-FI")
  case Swedish extends Language("sv-SE")

object Language extends StringEnumCompanion[Language]:
  override def all: Seq[Language] = Seq(English)
  override def write(t: Language): String = t.code
  val default: Language = English

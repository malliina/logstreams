package com.malliina.logstreams.models

import com.malliina.values.StringEnumCompanion

enum Language(val code: String):
  case English extends Language("en-US")
  case Finnish extends Language("fi-FI")
  case Swedish extends Language("sv-SE")

object Language extends StringEnumCompanion[Language]:
  override def all: Seq[Language] = Seq(English, Swedish, Finnish)
  override def write(t: Language): String = t.code
  val default: Language = English

case class ProfileLang(
  title: String,
  language: String,
  english: String,
  swedish: String,
  success: String,
  failure: String
)

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
  noResults: String,
  query: String,
  apps: String,
  queryLevel: String,
  limit: String,
  offset: String,
  queryTime: String,
  starting: String,
  until: String,
  between: String
)

case class PagingLang(
  navigation: String,
  next: String,
  previous: String
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
  deleteUser: String,
  noUsers: String
)

case class NavLang(
  appName: String,
  logs: String,
  sources: String,
  users: String,
  paging: PagingLang
)

case class Lang(
  language: Language,
  nav: NavLang,
  logs: LogsLang,
  servers: ServersLang,
  users: UsersLang,
  profile: ProfileLang
)

object Lang:
  val cookieName = "logstreams-lang"

  val en: Lang = Lang(
    Language.English,
    NavLang("logstreams", "Logs", "Sources", "Users", PagingLang("Navigation", "Next", "Previous")),
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
        "No results for",
        "query",
        "apps",
        "level",
        "limit",
        "offset",
        "time",
        "starting",
        "until",
        "between"
      )
    ),
    ServersLang("Servers", "App", "Address", "User-Agent", "ID", "Joined", "Unknown"),
    UsersLang("Users", "Username", "Password", "Action", "Add User", "Delete", "No users."),
    ProfileLang(
      "Profile",
      "Language",
      "English",
      "Swedish",
      "Completed successfully.",
      "An error occurred."
    )
  )
  val se: Lang = Lang(
    Language.Swedish,
    NavLang(
      "logstreams",
      "Loggar",
      "Källor",
      "Användare",
      PagingLang("Navigering", "Följande", "Föregående")
    ),
    LogsLang(
      "Loggar",
      "Mera",
      "Kompakt",
      "Appar",
      CalendarLang("Från", "Till"),
      "Sök",
      "Meddelande, tråd, ...",
      ResultsLang(
        "App",
        "Tid",
        "Meddelande",
        "Loggare",
        "Tråd",
        "Klient",
        "Agent",
        "Nivå",
        "Inga resultat för",
        "sökord",
        "appar",
        "nivå",
        "högst",
        "hoppa över",
        "tid",
        "från",
        "tills",
        "mellan"
      )
    ),
    ServersLang("Servrar", "App", "Adress", "User-Agent", "ID", "Kopplade", "Okänd"),
    UsersLang(
      "Användare",
      "Användarnamn",
      "Lösenord",
      "Gärning",
      "Lägg till",
      "Radera",
      "Inga användare."
    ),
    ProfileLang("Profil", "Språk", "Engelska", "Svenska", "Okej.", "Det misslyckades.")
  )
  val default = en

  def apply(language: Language): Lang = language match
    case Language.English => en
    case Language.Finnish => en
    case Language.Swedish => se

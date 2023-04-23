package com.malliina.logstreams.models

object FrontStrings extends FrontStrings

trait FrontStrings:
  val AppsDropdown = "apps-dropdown"
  val AppsFiltered = "apps-filtered"
  val AppsDropdownMenuId = "apps-dropdown-menu"
  val DropdownItem = "dropdown-item"

  val LogLevelDropdown = "level-dropdown"
  val LogLevelDropdownButton = "level-dropdown-button"
  val LogLevelDropdownMenuId = "level-dropdown-menu"

  val LabelCompact = "label-compact"
  val LabelVerbose = "label-verbose"
  val OptionCompact = "option-compact"
  val OptionVerbose = "option-verbose"

  val LogTableId = "log-table"

  val MobileContentId = "mobile-content"

  val SourceTableId = "source-table"
  val TableBodyId = "table-body"
  val TableHeadId = "table-head"

  val FromTimePickerId = "from-time-picker"
  val ToTimePickerId = "to-time-picker"

  val VerboseKey = "verbose"

  val SearchInput = "search-input"
  val SearchButton = "search-button"

  object classes:
    val MobileList = "mobile-list d-md-none"
    val Socket = "sockets"
    val Sources = "sources"

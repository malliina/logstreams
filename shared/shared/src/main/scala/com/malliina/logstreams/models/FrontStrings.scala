package com.malliina.logstreams.models

import com.malliina.html.{Bootstrap, Tags}

object FrontStrings extends FrontStrings

trait FrontStrings:
  private val bs = Bootstrap(Tags(scalatags.Text))
  val AppsDropdown = "apps-dropdown"
  val AppsFiltered = "apps-filtered"
  val AppsDropdownMenuId = "apps-dropdown-menu"
  val DisplayNone = "d-none"
  val DropdownItem = "dropdown-item"
  val SearchFeedbackId = "search-feedback"
  val SearchFeedbackRowId = "search-feedback-row"

  val LoadingSpinner = "loading-spinner"
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
  val TableClasses = s"${bs.tables.defaultClass} $DisplayNone d-md-block"
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

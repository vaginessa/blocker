package com.merxury.blocker.ui.home.advsearch.local

import com.merxury.blocker.data.app.InstalledApp

sealed class LocalSearchState {
    object NotStarted : LocalSearchState()
    data class Loading(val app: InstalledApp) : LocalSearchState()
    data class Error(val exception: Throwable) : LocalSearchState()
    object Finished : LocalSearchState()
    object Searching : LocalSearchState()
}

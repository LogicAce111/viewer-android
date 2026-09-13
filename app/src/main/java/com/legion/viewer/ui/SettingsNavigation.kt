package com.legion.viewer.ui

/** Settings has one explicit caller, independent of playback and reader navigation. */
internal class SettingsNavigation {
    private var source: ViewerScreen = ViewerScreen.Home

    fun navigating(from: ViewerScreen, to: ViewerScreen) {
        if (to == ViewerScreen.Settings) {
            if (from != ViewerScreen.Settings) {
                source = when (from) {
                    ViewerScreen.Home, is ViewerScreen.Category -> from
                    else -> ViewerScreen.Home
                }
            }
        } else {
            clear()
        }
    }

    fun takeReturnTarget(): ViewerScreen = source.also { clear() }

    fun clear() {
        source = ViewerScreen.Home
    }
}

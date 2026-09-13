package com.legion.viewer.ui

import com.legion.viewer.model.MediaCategory
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsNavigationTest {
    @Test fun allFiveEntryPointsReturnToTheirOwnPage() {
        val navigation = SettingsNavigation()
        val sources = listOf(ViewerScreen.Home) + MediaCategory.entries.map { ViewerScreen.Category(it) }
        sources.forEach { source ->
            navigation.navigating(source, ViewerScreen.Settings)
            assertEquals(source, navigation.takeReturnTarget())
        }
    }

    @Test fun repeatedSettingsEntryDoesNotOverwriteTheCaller() {
        val navigation = SettingsNavigation()
        val comics = ViewerScreen.Category(MediaCategory.Comics)
        navigation.navigating(comics, ViewerScreen.Settings)
        navigation.navigating(ViewerScreen.Settings, ViewerScreen.Settings)
        assertEquals(comics, navigation.takeReturnTarget())
    }

    @Test fun returnConsumesSourceAndMissingSourceDefaultsToHome() {
        val navigation = SettingsNavigation()
        assertEquals(ViewerScreen.Home, navigation.takeReturnTarget())
        navigation.navigating(ViewerScreen.Category(MediaCategory.Text), ViewerScreen.Settings)
        assertEquals(ViewerScreen.Category(MediaCategory.Text), navigation.takeReturnTarget())
        assertEquals(ViewerScreen.Home, navigation.takeReturnTarget())
    }

    @Test fun topLevelNavigationDiscardsOldCaller() {
        val navigation = SettingsNavigation()
        navigation.navigating(ViewerScreen.Category(MediaCategory.Video), ViewerScreen.Settings)
        navigation.clear()
        assertEquals(ViewerScreen.Home, navigation.takeReturnTarget())
        navigation.navigating(ViewerScreen.Category(MediaCategory.Music), ViewerScreen.Settings)
        assertEquals(ViewerScreen.Category(MediaCategory.Music), navigation.takeReturnTarget())
    }

    @Test fun leavingThroughMiniPlayerDoesNotLeaveStaleSettingsCaller() {
        val navigation = SettingsNavigation()
        navigation.navigating(ViewerScreen.Category(MediaCategory.Comics), ViewerScreen.Settings)
        navigation.navigating(ViewerScreen.Settings, ViewerScreen.Player)
        assertEquals(ViewerScreen.Home, navigation.takeReturnTarget())
    }

    @Test fun unexpectedCallerFallsBackToHome() {
        val navigation = SettingsNavigation()
        navigation.navigating(ViewerScreen.Player, ViewerScreen.Settings)
        assertEquals(ViewerScreen.Home, navigation.takeReturnTarget())
    }
}

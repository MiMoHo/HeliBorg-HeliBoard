// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings

import androidx.test.core.app.ApplicationProvider
import helium314.keyboard.latin.App
import helium314.keyboard.latin.utils.JniUtils
import helium314.keyboard.settings.screens.createAboutSettings
import helium314.keyboard.settings.screens.createAdvancedSettings
import helium314.keyboard.settings.screens.createAppearanceSettings
import helium314.keyboard.settings.screens.createCorrectionSettings
import helium314.keyboard.settings.screens.createGestureTypingSettings
import helium314.keyboard.settings.screens.createLayoutSettings
import helium314.keyboard.settings.screens.createPreferencesSettings
import helium314.keyboard.settings.screens.createToolbarSettings
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class SettingsContainerTest {
    private val context = ApplicationProvider.getApplicationContext<App>()

    @AfterTest fun reset() {
        JniUtils.sHaveGestureLib = false
    }

    // search results are opened in the screen that lists them, so every searchable setting needs the correct screen
    @Test fun everySettingIsMappedToTheScreenListingIt() {
        JniUtils.sHaveGestureLib = true // include gesture typing settings
        val container = SettingsContainer(context)
        // each screen lists the settings created in the same file, see SettingsNavHost for the screen of each destination
        val settingsOfScreen = mapOf(
            SettingsDestination.About to createAboutSettings(context),
            SettingsDestination.Appearance to createAppearanceSettings(context),
            SettingsDestination.TextCorrection to createCorrectionSettings(context),
            SettingsDestination.Preferences to createPreferencesSettings(context),
            SettingsDestination.Toolbar to createToolbarSettings(context),
            SettingsDestination.Layouts to createLayoutSettings(context),
            SettingsDestination.Advanced to createAdvancedSettings(context),
            SettingsDestination.GestureTyping to createGestureTypingSettings(context),
        )
        val expected = settingsOfScreen.flatMap { (screen, settings) -> settings.map { it.key to screen } }.toMap()

        val actual = container.filter("").associate { it.key to container.screenOf(it.key) }
        assertEquals(expected, actual)
    }
}

package io.github.hiramrg.hermes.ui

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.hiramrg.hermes.platform.BottomMode
import io.github.hiramrg.hermes.platform.Cartridge
import io.github.hiramrg.hermes.platform.Hermes
import io.github.hiramrg.hermes.platform.Screens

private sealed class Screen {
    data object Setup : Screen()
    data object Home : Screen()
    data object Settings : Screen()
    data class Backups(val cart: Cartridge) : Screen()
}

/** Top screen: the walkthrough, the cartridge shelf and settings. */
class MainActivity : ComponentActivity() {

    private var screen by mutableStateOf<Screen>(Screen.Home)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Hermes.init(this)
        screen = if (Hermes.prefs.setupDone && Hermes.setupComplete) Screen.Home else Screen.Setup
        val dual = Screens.roles(this, Hermes.prefs.swapScreens).bottom != null
        if (savedInstanceState == null) Hermes.checkForUpdate(silent = true)

        setContent {
            HermesTheme {
                when (val s = screen) {
                    Screen.Setup -> {
                        BackHandler(enabled = Hermes.prefs.setupDone) { screen = Screen.Home }
                        SetupScreen(dual) {
                            Hermes.finishSetup()
                            screen = Screen.Home
                        }
                    }
                    Screen.Home -> HomeScreen(
                        onSettings = { screen = Screen.Settings },
                        onBackups = { screen = Screen.Backups(it) },
                    )
                    Screen.Settings -> {
                        BackHandler { screen = Screen.Home }
                        SettingsScreen(onBack = { screen = Screen.Home }, onSetup = { screen = Screen.Setup })
                    }
                    is Screen.Backups -> {
                        BackHandler { screen = Screen.Home }
                        BackupsScreen(s.cart) { screen = Screen.Home }
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        Hermes.mainVisible = true
    }

    /** Minimized or closed: the bottom screen closes too, unless it's guiding setup or a game. */
    override fun onStop() {
        super.onStop()
        Hermes.mainVisible = false
    }

    override fun onResume() {
        super.onResume()
        BottomScreen.show(this)
        // Coming back from Stadium: rescan, and bring Stadium's changes home.
        val returning = Hermes.prefs.insertion?.launched == true
        if (Hermes.bottomMode == BottomMode.SETUP_GUIDE && Hermes.prefs.linkVerified) Hermes.bottomMode = BottomMode.HOME
        Hermes.refresh()
        if (returning) Hermes.sync(waitForStadium = true)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    /** The Thor's A and B buttons act like select and back in Hermes' menus. */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val mapped = when (event.keyCode) {
            KeyEvent.KEYCODE_BUTTON_A -> KeyEvent.KEYCODE_DPAD_CENTER
            KeyEvent.KEYCODE_BUTTON_B -> KeyEvent.KEYCODE_BACK
            else -> null
        }
        if (mapped != null) {
            return super.dispatchKeyEvent(KeyEvent(event.downTime, event.eventTime, event.action, mapped, event.repeatCount, event.metaState, event.deviceId, event.scanCode, event.flags, event.source))
        }
        return super.dispatchKeyEvent(event)
    }
}

package io.github.hiramrg.hermes.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.hiramrg.hermes.platform.BottomMode
import io.github.hiramrg.hermes.platform.Hermes
import io.github.hiramrg.hermes.platform.Screens
import java.lang.ref.WeakReference

/** Starts the bottom-screen companion on the second display (if there is one). */
object BottomScreen {
    private var current: WeakReference<BottomActivity>? = null

    internal fun attach(a: BottomActivity?) { current = a?.let { WeakReference(it) } }

    fun show(from: Activity) {
        // Already up (and not on its way out after being closed).
        if (current?.get()?.let { !it.isFinishing && !it.isDestroyed } == true) return
        val bottom = Screens.roles(from, Hermes.prefs.swapScreens).bottom ?: return
        val intent = Intent(from, BottomActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
        runCatching { from.startActivity(intent, Screens.options(bottom)) }
    }
}

/**
 * The bottom screen. Its window never takes focus, so the controller always stays with
 * Stadium (or Hermes) on the top screen; it still reacts to touch.
 */
class BottomActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Hermes.init(this)
        window.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
        BottomScreen.attach(this)
        setContent {
            // Hermes was minimized or closed and nothing needs the bottom screen: go away.
            val idleAlone = Hermes.bottomMode == BottomMode.HOME && !Hermes.mainVisible
            LaunchedEffect(idleAlone) {
                if (idleAlone) {
                    delay(400) // ignore the blink when Hermes opens a folder picker and comes back
                    if (Hermes.bottomMode == BottomMode.HOME && !Hermes.mainVisible) finishAndRemoveTask()
                }
            }
            HermesTheme {
                Box(Modifier.fillMaxSize()) {
                    BottomContent()
                    IconButton(
                        onClick = {
                            if (Hermes.bottomMode == BottomMode.SETUP_GUIDE) Hermes.bottomMode = BottomMode.HOME
                            finishAndRemoveTask()
                        },
                        modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = "Close the bottom screen", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        BottomScreen.attach(null)
        super.onDestroy()
    }
}

@Composable
private fun BottomContent() {
    @Suppress("UNUSED_VARIABLE") val v = Hermes.prefsVersion
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
        when (Hermes.bottomMode) {
            BottomMode.SETUP_GUIDE -> {
                Text("Plug in the Transfer Pak", fontSize = 24.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(4.dp))
                Text("Do this in M64Plus FZ on the top screen:", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(16.dp))
                LinkGuide(Hermes.prefs.slotTree?.let { Hermes.storage.treeLabel(it) } ?: "Transfer Pak", big = true)
                Spacer(Modifier.height(20.dp))
                HButton("Back to Hermes", { Hermes.bringHermesToFront(MainActivity::class.java) }, Modifier.fillMaxWidth(), primary = true)
            }
            BottomMode.PLAYING -> Playing()
            BottomMode.HOME -> Idle()
        }
    }
}

@Composable
private fun Playing() {
    val ins = Hermes.prefs.insertion
    val cart = Hermes.insertedCartridge
    Row(verticalAlignment = Alignment.CenterVertically) {
        cart?.let { CartridgeArt(it.game, width = 64.dp); Spacer(Modifier.width(16.dp)) }
        Column {
            Text("In the Transfer Pak", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
            Text(ins?.gameLabel ?: "Cartridge", fontSize = 24.sp, fontWeight = FontWeight.Black)
        }
    }
    Spacer(Modifier.height(16.dp))
    cart?.card?.let { Panel(Modifier.fillMaxWidth()) { TrainerPanel(it, compact = true) }; Spacer(Modifier.height(16.dp)) }
    Panel(Modifier.fillMaxWidth()) {
        Column {
            Text("When you're done", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(Modifier.height(6.dp))
            Text(
                rich("In M64Plus FZ, open its menu and choose **Exit**. Then tap **I'm done** below. Hermes copies everything Stadium changed back into your Game Boy save."),
                fontSize = 16.sp, lineHeight = 22.sp,
            )
        }
    }
    Spacer(Modifier.height(16.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        HButton("I'm done", {
            Hermes.bringHermesToFront(MainActivity::class.java)
        }, Modifier.weight(1f), icon = Icons.Filled.Check, primary = true)
        HButton("Back to Stadium", { Hermes.returnToStadium() }, Modifier.weight(1f), icon = Icons.Filled.SportsEsports)
    }
}

@Composable
private fun Idle() {
    val cart = Hermes.selected
    if (cart == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Hermes", fontSize = 32.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        CartridgeArt(cart.game, width = 64.dp)
        Spacer(Modifier.width(16.dp))
        Text(cart.title, fontSize = 24.sp, fontWeight = FontWeight.Black)
    }
    Spacer(Modifier.height(16.dp))
    cart.card?.let { Panel(Modifier.fillMaxWidth()) { TrainerPanel(it) } }
        ?: Text("No usable save for this game yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(16.dp))
    Text(
        rich("Press **A** on **Play in Pokémon Stadium** to put this cartridge in the Transfer Pak and start Stadium."),
        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 15.sp,
    )
}

package com.devwithcoffee.hush.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.devwithcoffee.hush.engine.Hush
import com.devwithcoffee.hush.engine.HushListener
import com.devwithcoffee.hush.engine.Store

class MainActivity : ComponentActivity() {
    /** Bumped on every resume so permission state is re-read after visiting Settings. */
    private val resumeTick = mutableIntStateOf(0)
    private val tab = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Store.init(this)
        if (!Store.onboarded && !Setup.coreReady(this)) tab.intValue = 3
        setContent { HushTheme { Root(tab, resumeTick.intValue) } }
    }

    override fun onResume() {
        super.onResume()
        resumeTick.intValue++
        Hush.reconcile(this)
        HushListener.instance?.registerPhone()
    }
}

private data class TabDef(val label: String, val icon: ImageVector)

private val TABS = listOf(
    TabDef("Now", Icons.Rounded.Bedtime),
    TabDef("Allowed", Icons.Rounded.VerifiedUser),
    TabDef("Schedule", Icons.Rounded.Schedule),
    TabDef("Setup", Icons.Rounded.Tune),
)

@Composable
private fun Root(tab: MutableIntState, tick: Int) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val missing = remember(tick) { Setup.items(ctx).count { it.required && !it.done } }
    Scaffold(
        containerColor = C.bg,
        bottomBar = {
            NavigationBar(containerColor = C.surface, tonalElevation = 0.dp) {
                TABS.forEachIndexed { i, t ->
                    NavigationBarItem(
                        selected = tab.intValue == i,
                        onClick = { tab.intValue = i },
                        icon = {
                            if (i == 3 && missing > 0) BadgedBox(badge = { Badge { Text("$missing") } }) { Icon(t.icon, t.label) }
                            else Icon(t.icon, t.label)
                        },
                        label = { Text(t.label) },
                        colors = NavigationBarItemDefaults.colors(indicatorColor = C.moonSoft, selectedIconColor = C.moon, selectedTextColor = C.moon),
                    )
                }
            }
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            AnimatedContent(tab.intValue, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "tab") { i ->
                when (i) {
                    0 -> NowScreen(tick) { tab.intValue = it }
                    1 -> AllowedScreen(tick)
                    2 -> ScheduleScreen()
                    else -> SetupScreen(tick)
                }
            }
        }
    }
}


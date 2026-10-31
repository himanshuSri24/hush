package com.devwithcoffee.hush.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devwithcoffee.hush.core.Kind
import com.devwithcoffee.hush.engine.Alerter
import kotlinx.coroutines.delay

/** Full-screen "someone got through" screen, shown over the lock screen for calls. */
class AlertActivity : ComponentActivity() {
    companion object {
        fun intent(ctx: Context, kind: Kind, who: String, app: String, why: String, id: Int) =
            Intent(ctx, AlertActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION)
                .putExtra("kind", kind.name).putExtra("who", who).putExtra("app", app)
                .putExtra("why", why).putExtra("id", id)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val kind = Kind.valueOf(intent.getStringExtra("kind") ?: Kind.CALL.name)
        val who = intent.getStringExtra("who") ?: ""
        val app = intent.getStringExtra("app") ?: ""
        val why = intent.getStringExtra("why") ?: ""
        val id = intent.getIntExtra("id", -1)
        setContent {
            HushTheme {
                LaunchedEffect(Unit) {
                    // Close once the call stops ringing (answered elsewhere, hung up, or timed out).
                    delay(1500)
                    while (Alerter.isRinging()) delay(500)
                    finish()
                }
                AlertUi(kind, who, app, why,
                    onOpen = {
                        val pi = Alerter.original(id)
                        Alerter.silence(this, id)
                        runCatching {
                            val opts = android.app.ActivityOptions.makeBasic()
                            if (android.os.Build.VERSION.SDK_INT >= 34) opts.setPendingIntentBackgroundActivityStartMode(
                                android.app.ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                            pi?.send(this, 0, null, null, null, null, opts.toBundle())
                        }
                        finish()
                    },
                    onSilence = { Alerter.silence(this, id); finish() },
                )
            }
        }
    }
}

@Composable
private fun AlertUi(kind: Kind, who: String, app: String, why: String, onOpen: () -> Unit, onSilence: () -> Unit) {
    val t = rememberInfiniteTransition(label = "ring")
    val s1 by t.animateFloat(1f, 1.6f, infiniteRepeatable(tween(1600, easing = LinearOutSlowInEasing)), label = "s1")
    val a1 by t.animateFloat(0.6f, 0f, infiniteRepeatable(tween(1600, easing = LinearOutSlowInEasing)), label = "a1")

    Column(
        Modifier.fillMaxSize().background(C.bg).systemBarsPadding().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(40.dp))
        Pill("Got through Hush", C.amber, C.amberSoft)
        Spacer(Modifier.weight(1f))
        Box(contentAlignment = Alignment.Center) {
            Box(Modifier.size(150.dp).scale(s1).clip(CircleShape).background(C.amber.copy(alpha = a1 * 0.7f)))
            Initials(who, 150.dp, C.amberSoft, C.amber)
        }
        Spacer(Modifier.height(32.dp))
        Text(who, fontSize = 34.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, color = C.text)
        Text(if (kind == Kind.CALL) "is calling on $app" else "on $app", fontSize = 18.sp, color = C.muted, modifier = Modifier.padding(top = 6.dp))
        Text(why, color = C.amber, modifier = Modifier.padding(top = 12.dp))
        Spacer(Modifier.weight(1.3f))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            OutlinedButton(onSilence, Modifier.weight(1f).height(60.dp)) {
                Icon(Icons.Rounded.VolumeOff, null); Spacer(Modifier.width(8.dp)); Text("Silence", fontSize = 16.sp)
            }
            Button(onOpen, Modifier.weight(1f).height(60.dp), colors = ButtonDefaults.buttonColors(containerColor = C.amber)) {
                Text("Open $app", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, color = C.bg)
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

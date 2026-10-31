package com.devwithcoffee.hush

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.devwithcoffee.hush.core.Config
import com.devwithcoffee.hush.core.Vip
import com.devwithcoffee.hush.engine.Hush
import com.devwithcoffee.hush.engine.Store

/** adb shell am broadcast -a hush.test.START --ei min 5 -n com.devwithcoffee.hush.debug/com.devwithcoffee.hush.DebugReceiver */
class DebugReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, i: Intent) {
        Store.init(ctx)
        when (i.action) {
            "hush.test.START" -> Hush.startFor(ctx, i.getIntExtra("min", 5))
            "hush.test.STOP" -> Hush.stop(ctx, byUser = true)
            "hush.test.VIP" -> Store.updateConfig {
                it.copy(vips = it.vips + Vip("t" + System.nanoTime(), i.getStringExtra("name") ?: "Test", listOfNotNull(i.getStringExtra("num"))))
            }
            "hush.test.ALLOW" -> Store.updateConfig { it.copy(allowedApps = it.allowedApps + (i.getStringExtra("pkg") ?: return@updateConfig it)) }
            "hush.test.RESET" -> Store.updateConfig { Config() }
        }
        Log.i("HushTest", "STATE " + Store.session.value.toJson().toString())
        Log.i("HushTest", "CONFIG " + Store.config.value.toJson().toString())
    }
}

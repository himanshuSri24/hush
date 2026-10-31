package com.devwithcoffee.hush.engine

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/** Alarm + notification-action entry point. */
class HushReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, i: Intent) {
        Store.init(ctx)
        when (i.action) {
            Hush.ACTION_END -> Hush.reconcile(ctx)                 // timer ran out
            Hush.ACTION_END + ".USER" -> Hush.stop(ctx, byUser = true)
            Hush.ACTION_EXTEND -> Hush.extend(ctx, i.getIntExtra("n", 15))
            Hush.ACTION_SCHEDULE -> Hush.reconcile(ctx)            // a schedule window opened
            Alerter.ACTION_SILENCE -> Alerter.silence(ctx, i.getIntExtra("n", -1))
        }
    }
}

/** Boot, time-zone change, app update: re-arm alarms and re-apply DND if needed. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, i: Intent) {
        Store.init(ctx)
        Notifs.createChannels(ctx)
        Hush.reconcile(ctx)
    }
}

/** Manifest backup for phone-state, in case the listener process was restarted. */
class PhoneStateReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, i: Intent) {
        if (HushListener.instance == null) HushListener.onPhoneState(ctx, i)
    }
}

/** Quick Settings tile: tap to hush for the default duration, tap again to end. */
class HushTile : TileService() {
    override fun onStartListening() = render()

    override fun onClick() {
        Store.init(this)
        if (Hush.isActive(this)) Hush.stop(this, byUser = true)
        else Hush.startFor(this, Store.config.value.tileMinutes, "tile")
        render()
    }

    private fun render() {
        Store.init(this)
        val t = qsTile ?: return
        val s = Store.session.value
        t.state = if (s.active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        t.label = "Hush"
        t.subtitle = when {
            !s.active -> Store.config.value.tileMinutes.let { if (it == 0) "Until off" else if (it % 60 == 0) "${it / 60} h" else "$it min" }
            s.endsAt == 0L -> "On"
            else -> "Until ${Notifs.timeOf(s.endsAt)}"
        }
        t.updateTile()
    }
}

package com.devwithcoffee.hush

import android.app.Application
import com.devwithcoffee.hush.engine.Notifs
import com.devwithcoffee.hush.engine.Store

class HushApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Store.init(this)
        Notifs.createChannels(this)
    }
}

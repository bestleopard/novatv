package com.novaplay.app

import android.app.Application
import com.novaplay.app.data.Store

class NovaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Store.init(this)
    }
}

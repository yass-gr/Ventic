package com.yass.vintageplayer

import android.app.Application

class VintageApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppGraph.init(this)
    }
}

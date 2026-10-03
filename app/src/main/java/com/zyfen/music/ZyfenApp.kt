package com.zyfen.music

import android.app.Application
import com.zyfen.music.di.AppContainer

class ZyfenApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        container = AppContainer(this)
    }

    companion object {
        lateinit var instance: ZyfenApp
            private set

        val container: AppContainer
            get() = instance.container
    }
}

package com.miireader

import android.app.Application
import com.miireader.di.AppContainer
import com.miireader.di.DefaultAppContainer

class MiiReaderApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = DefaultAppContainer(this)
    }
}

package com.mulerun.androidtvremote

import android.app.Application
import com.mulerun.androidtvremote.data.SecureStore

class RemoteApp : Application() {
    lateinit var secureStore: SecureStore
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        secureStore = SecureStore(this)
    }

    companion object {
        lateinit var instance: RemoteApp
            private set
    }
}

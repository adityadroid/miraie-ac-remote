package dev.adityadroid.acremote

import android.app.Application
import dev.adityadroid.acremote.data.AcRepository

class AcApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AcRepository.init(this)
    }
}

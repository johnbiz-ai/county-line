package net.johnbiz.countyline

import android.app.Application
import net.johnbiz.countyline.notify.Notifications

class CountyLineApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Notifications.createChannels(this)
    }
}

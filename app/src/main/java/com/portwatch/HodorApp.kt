package com.portwatch

import android.app.Application
import com.portwatch.data.Filter
import com.portwatch.data.GeoResolver
import com.portwatch.data.Notifier
import com.portwatch.data.Settings
import com.portwatch.data.ThreatIntel

class HodorApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Settings.init(this)
        ThreatIntel.init(this)
        Filter.init(this)
        Notifier.init(this)
        GeoResolver.init(this)
    }
}

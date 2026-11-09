package com.bujiaban.niuqu.pico.platform

import android.app.Application
import com.pico.spatial.ui.foundation.dsl.launch
import com.bujiaban.niuqu.pico.mainApp

class SpatialApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        launch(::mainApp)
    }
}

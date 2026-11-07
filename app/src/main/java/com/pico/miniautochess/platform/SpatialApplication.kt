package com.pico.miniautochess.platform

import android.app.Application
import com.pico.spatial.ui.foundation.dsl.launch
import com.pico.miniautochess.mainApp

class SpatialApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        launch(::mainApp)
    }
}

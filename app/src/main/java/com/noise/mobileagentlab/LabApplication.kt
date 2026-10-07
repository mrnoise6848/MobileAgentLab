package com.noise.mobileagentlab

import android.app.Application
import com.noise.mobileagentlab.agent.LabController

class LabApplication : Application() {

    lateinit var controller: LabController
        private set

    override fun onCreate() {
        super.onCreate()
        controller = LabController(this)
    }
}

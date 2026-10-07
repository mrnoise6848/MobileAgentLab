package com.noise.mobileagentlab

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.noise.mobileagentlab.ui.LabApp

/**
 * Single activity hosting the lab UI. All agent logic lives in
 * [com.noise.mobileagentlab.agent.LabController], owned by [LabApplication].
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val controller = (application as LabApplication).controller
        setContent {
            LabApp(controller)
        }
    }
}

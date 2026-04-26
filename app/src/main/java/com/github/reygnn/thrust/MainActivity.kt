package com.github.reygnn.thrust

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.github.reygnn.thrust.ui.navigation.ThrustNavGraph
import com.github.reygnn.thrust.ui.theme.ThrustTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ThrustTheme {
                ThrustNavGraph(modifier = Modifier.fillMaxSize())
            }
        }
    }
}

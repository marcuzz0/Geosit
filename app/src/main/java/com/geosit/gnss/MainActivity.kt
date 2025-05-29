package com.geosit.gnss

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import com.geosit.gnss.ui.navigation.GeoSitNavigation
import com.geosit.gnss.ui.theme.GeoSitTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Make app edge-to-edge
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            GeoSitTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    GeoSitApp()
                }
            }
        }
    }
}

@Composable
fun GeoSitApp() {
    GeoSitNavigation()
}
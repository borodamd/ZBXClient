// MainActivity.kt
package com.itsoul.zbxclient

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

class MainActivity : ComponentActivity() {
    private lateinit var preferencesManager: PreferencesManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        preferencesManager = PreferencesManager(this)

        setContent {
            ZabbixApp(preferencesManager = preferencesManager)
        }
    }
}

@Preview(showBackground = true)
@Composable
fun MainActivityPreview() {
    val context = androidx.compose.ui.platform.LocalContext.current
    ZabbixApp(preferencesManager = PreferencesManager(context))
}
package com.fws.cookinghandbook

import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.fws.cookinghandbook.ui.AppRoot
import com.fws.cookinghandbook.ui.CookingHandbookTheme
import com.fws.cookinghandbook.ui.isAppDark
import com.fws.cookinghandbook.util.AdsConsent
import com.fws.cookinghandbook.util.InAppUpdater
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : AppCompatActivity() {
    private val vm: AppViewModel by viewModels()
    private lateinit var updater: InAppUpdater
    private val adsReady = MutableStateFlow(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        updater = InAppUpdater(this) // must be created before the activity is STARTED
        setContent {
            val theme by vm.prefs.theme.collectAsState()
            val dark = isAppDark(theme)
            DisposableEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = if (dark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
                    navigationBarStyle = if (dark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
                )
                onDispose { }
            }
            CookingHandbookTheme(theme) { AppRoot(vm, updater, adsReady) }
        }
        AdsConsent.init(this) { adsReady.value = true }
    }

    override fun onStart() { super.onStart(); updater.start() }
    override fun onResume() { super.onResume(); updater.check(); vm.ensureFeatured() }
    override fun onStop() { updater.stop(); super.onStop() }
}

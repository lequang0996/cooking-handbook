package com.fws.cookinghandbook.ui

import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.fws.cookinghandbook.AppViewModel
import com.fws.cookinghandbook.BuildConfig
import com.fws.cookinghandbook.R
import com.fws.cookinghandbook.util.BannerAd
import com.fws.cookinghandbook.util.InAppUpdater
import com.fws.cookinghandbook.util.Lang
import kotlinx.coroutines.flow.StateFlow

private data class Tab(val route: String, val label: Int, val icon: androidx.compose.ui.graphics.vector.ImageVector)

@Composable
fun AppRoot(vm: AppViewModel, updater: InAppUpdater, adsReady: StateFlow<Boolean>) {
    val context = LocalContext.current
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val snackbar = remember { SnackbarHostState() }

    // recipes are translated by Gemini into the UI language (see TranslationManager)
    val lang = remember { Lang.current(context) }
    val tr = remember(lang) { Tr(vm.translations, lang) }

    val tabs = listOf(
        Tab("home", R.string.tab_home, Icons.Filled.Home),
        Tab("favorites", R.string.tab_favorites, Icons.Filled.Favorite),
        Tab("settings", R.string.tab_settings, Icons.Filled.Settings),
    )
    val showBar = route in tabs.map { it.route }
    val ads by adsReady.collectAsState()

    // flexible in-app update finished downloading -> offer restart
    val downloaded by updater.downloaded.collectAsState()
    val readyText = stringResource(R.string.update_ready)
    val restartText = stringResource(R.string.update_restart)
    LaunchedEffect(downloaded) {
        if (downloaded) {
            val r = snackbar.showSnackbar(readyText, restartText, duration = androidx.compose.material3.SnackbarDuration.Indefinite)
            if (r == SnackbarResult.ActionPerformed) updater.completeUpdate()
        }
    }

    CompositionLocalProvider(LocalTr provides tr) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                Column(Modifier.background(MaterialTheme.colorScheme.surface)) {
                    if (ads) BannerAd(BuildConfig.ADMOB_BANNER_ID, if (showBar) Modifier else Modifier.navigationBarsPadding())
                    if (showBar) NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                        tabs.forEach { t ->
                            NavigationBarItem(
                                selected = route == t.route,
                                onClick = {
                                    nav.navigate(t.route) {
                                        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = { Icon(t.icon, null) },
                                label = { Text(stringResource(t.label)) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                    selectedTextColor = MaterialTheme.colorScheme.secondary,
                                    indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                                ),
                            )
                        }
                    }
                }
            },
        ) { pad ->
            val openDish = { path: String -> nav.navigate("dish/" + Uri.encode(path)) }
            // detail screens own their top bar (and its status-bar inset); tab screens use the outer padding as-is
            val content = PaddingValues(top = if (showBar) pad.calculateTopPadding() else 0.dp, bottom = pad.calculateBottomPadding())
            NavHost(nav, startDestination = "home", modifier = Modifier.padding(content)) {
                composable("home") { HomeScreen(vm, openDish) { nav.navigate("category/$it") } }
                composable("favorites") { FavoritesScreen(vm, openDish) }
                composable("settings") { SettingsScreen(vm) { nav.navigate("guide") } }
                composable("guide") { GuideScreen { nav.popBackStack() } }
                composable("category/{id}", listOf(navArgument("id") { type = NavType.StringType })) { e ->
                    CategoryScreen(vm, e.arguments?.getString("id").orEmpty(), { nav.popBackStack() }, openDish)
                }
                composable("dish/{path}", listOf(navArgument("path") { type = NavType.StringType })) { e ->
                    DishScreen(vm, e.arguments?.getString("path").orEmpty()) { nav.popBackStack() }
                }
            }
        }
    }
}

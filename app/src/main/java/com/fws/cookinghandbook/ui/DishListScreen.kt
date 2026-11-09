package com.fws.cookinghandbook.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.fws.cookinghandbook.AppViewModel
import com.fws.cookinghandbook.R

/** Dishes of one category (with back arrow). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryScreen(vm: AppViewModel, categoryId: String, onBack: () -> Unit, onDish: (String) -> Unit) {
    val dishes by vm.dishes.collectAsState()
    val favorites by vm.prefs.favorites.collectAsState()
    val list = remember(dishes, categoryId) { dishes.filter { it.categoryId == categoryId }.sortedBy { it.title } }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(categoryName(categoryId), style = MaterialTheme.typography.titleLarge) },
                navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(list, key = { it.path }) { d -> DishRow(d, d.path in favorites, { onDish(d.path) }) }
        }
    }
}

/** Favorites tab. */
@Composable
fun FavoritesScreen(vm: AppViewModel, onDish: (String) -> Unit) {
    val dishes by vm.dishes.collectAsState()
    val favorites by vm.prefs.favorites.collectAsState()
    val list = remember(dishes, favorites) { dishes.filter { it.path in favorites }.sortedBy { it.title } }
    Box(Modifier.fillMaxSize()) {
        if (list.isEmpty()) {
            Text(
                stringResource(R.string.fav_empty), Modifier.align(Alignment.Center).padding(40.dp),
                textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyLarge,
            )
        } else {
            LazyColumn(
                Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item { Text(stringResource(R.string.tab_favorites), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.fillMaxWidth()) }
                items(list, key = { it.path }) { d -> DishRow(d, true, { onDish(d.path) }) }
            }
        }
    }
}

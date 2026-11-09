package com.fws.cookinghandbook.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fws.cookinghandbook.AppViewModel
import com.fws.cookinghandbook.R
import com.fws.cookinghandbook.data.Categories
import com.fws.cookinghandbook.data.Dish
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(vm: AppViewModel, onDish: (String) -> Unit, onCategory: (String) -> Unit) {
    val dishes by vm.dishes.collectAsState()
    val featured by vm.featured.collectAsState()
    val favorites by vm.prefs.favorites.collectAsState()
    val tr = LocalTr.current
    var query by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Dish>>(emptyList()) }

    // Debounced search; a non-Chinese query is also translated to Chinese so recipes can be found in any language.
    LaunchedEffect(query, dishes, tr?.key) {
        if (query.isBlank()) { results = emptyList(); return@LaunchedEffect }
        results = vm.search(query, null)
        delay(450)
        val hasCjk = query.any { it.code in 0x4E00..0x9FFF }
        if (!hasCjk && tr != null && tr.enabled) {
            val zh = tr.mgr.translate(query.trim(), tr.target, "zh")
            if (zh != null) results = vm.search(query, zh)
        }
    }

    // categories sorted by popularity = number of dishes
    val counts = remember(dishes) { dishes.groupingBy { it.categoryId }.eachCount() }
    val sorted = remember(counts) { Categories.all.filter { (counts[it.id] ?: 0) > 0 }.sortedByDescending { counts[it.id] } }
    val slides = remember(featured, dishes, sorted) {
        val byPath = dishes.associateBy { it.path }
        sorted.mapNotNull { c -> featured[c.id]?.let { byPath[it] } }
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp)) {
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.primary)
                Text(stringResource(R.string.home_tagline), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            OutlinedTextField(
                value = query, onValueChange = { query = it }, singleLine = true,
                placeholder = { Text(stringResource(R.string.search_hint)) },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                trailingIcon = { if (query.isNotEmpty()) IconButton({ query = "" }) { Icon(Icons.Filled.Close, null) } },
                shape = RoundedCornerShape(28.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    focusedBorderColor = MaterialTheme.colorScheme.secondary,
                ),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            )
        }
        if (query.isNotBlank()) {
            if (results.isEmpty()) item {
                Text(stringResource(R.string.no_results), Modifier.fillMaxWidth().padding(32.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
            }
            items(results.take(80), key = { it.path }) { d ->
                DishRow(d, d.path in favorites, { onDish(d.path) }, Modifier.padding(horizontal = 20.dp))
            }
        } else {
            if (slides.isNotEmpty()) item {
                Column {
                    Column(Modifier.padding(horizontal = 20.dp)) {
                        Text(stringResource(R.string.featured_title), style = MaterialTheme.typography.titleLarge)
                        Text(stringResource(R.string.featured_sub), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(10.dp))
                    val pager = rememberPagerState { slides.size }
                    HorizontalPager(pager, contentPadding = PaddingValues(horizontal = 20.dp), pageSpacing = 12.dp) { i ->
                        FeaturedCard(slides[i], onClick = { onDish(slides[i].path) })
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        repeat(slides.size) { i ->
                            val on = pager.currentPage == i
                            Box(
                                Modifier.padding(horizontal = 3.dp).size(width = if (on) 18.dp else 6.dp, height = 6.dp)
                                    .clip(CircleShape)
                                    .background(if (on) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outline)
                            )
                        }
                    }
                }
            }
            item { Ornament(Modifier.padding(top = 8.dp)) }
            item { Text(stringResource(R.string.topics_title), Modifier.padding(horizontal = 20.dp), style = MaterialTheme.typography.titleLarge) }
            items(sorted, key = { it.id }) { c ->
                ElegantCard(Modifier.padding(horizontal = 20.dp).fillMaxWidth(), onClick = { onCategory(c.id) }) {
                    Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(categoryIcon(c.id))
                        Spacer(Modifier.width(14.dp))
                        Text(stringResource(c.nameRes), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                        Box(
                            Modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.secondaryContainer)
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Text("${counts[c.id]}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSecondaryContainer)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FeaturedCard(dish: Dish, onClick: () -> Unit) {
    val title = rememberTranslated(dish.title)
    val intro = rememberTranslated(dish.intro)
    Box(
        Modifier.fillMaxWidth().height(224.dp).clip(RoundedCornerShape(28.dp)).background(HeroBrush)
            .drawBehind {
                drawCircle(Gold.copy(alpha = 0.10f), radius = size.minDimension * 0.62f, center = Offset(size.width * 0.96f, size.height * 0.04f))
                drawCircle(Gold.copy(alpha = 0.35f), radius = size.minDimension * 0.62f, center = Offset(size.width * 0.96f, size.height * 0.04f), style = Stroke(1.5.dp.toPx()))
                drawCircle(Gold.copy(alpha = 0.18f), radius = size.minDimension * 0.34f, center = Offset(size.width * 0.96f, size.height * 0.04f), style = Stroke(1.dp.toPx()))
            }
            .clickable(onClick = onClick)
    ) {
        Column(Modifier.padding(20.dp).fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
            Row(
                Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.12f)).padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(categoryIcon(dish.categoryId), null, tint = Gold, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(categoryName(dish.categoryId), color = Gold, style = MaterialTheme.typography.labelLarge)
            }
            Column {
                Text(title, color = Color.White, style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(6.dp))
                Text(intro, color = Color.White.copy(alpha = 0.78f), style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Stars(dish.difficulty, tint = Gold)
                dish.calories?.let {
                    Text("  ·  " + stringResource(R.string.kcal_unit, it), color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

package com.fws.cookinghandbook.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import com.fws.cookinghandbook.AppViewModel
import com.fws.cookinghandbook.R
import com.fws.cookinghandbook.data.asText
import com.fws.cookinghandbook.util.Lang
import com.fws.cookinghandbook.util.RemoteImage
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DishScreen(vm: AppViewModel, path: String, onBack: () -> Unit) {
    val dishes by vm.dishes.collectAsState()
    val favorites by vm.prefs.favorites.collectAsState()
    val rate by vm.prefs.ttsRate.collectAsState()
    val dish = dishes.firstOrNull { it.path == path }
    if (dish == null) { Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)); return }

    // Translation: use what is available now (bundled / already translated by Gemini); otherwise ask Gemini and show
    // the Chinese text until it arrives. The screen refreshes itself when `version` changes.
    val tr = LocalTr.current
    val lang = tr?.lang ?: "zh"
    val keys by vm.prefs.apiKeys.collectAsState()
    val trVersion by vm.translations.version.collectAsState()
    val trBusy by vm.translations.busy.collectAsState()
    val trError by vm.translations.lastError.collectAsState()
    LaunchedEffect(dish.path, dish.sha, lang, keys.size) { if (lang != "zh") vm.translations.request(dish, lang) }
    val translated = remember(dish.path, dish.sha, lang, trVersion) { if (lang == "zh") null else vm.translations.text(dish, lang) }
    val content = translated ?: dish.asText() // read aloud in the same language as the text, so keep it consistent
    val shownTitle = remember(dish.path, dish.sha, lang, trVersion) { vm.translations.title(dish, lang) }
    val contentLang = if (translated != null) lang else "zh"
    val needsTranslation = lang != "zh" && translated == null

    val tts = vm.tts
    val speaking by tts.speaking.collectAsState()
    val current by tts.current.collectAsState()
    val missingVoice by tts.missingVoice.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val missingMsg = stringResource(R.string.tts_missing)
    LaunchedEffect(missingVoice) { if (missingVoice) { snackbar.showSnackbar(missingMsg); tts.clearMissingVoice() } }
    DisposableEffect(Unit) { onDispose { tts.stop() } }

    // everything that is read aloud, in order; steps start at stepOffset
    val chunks = remember(content) {
        buildList {
            add(content.title); add(content.intro)
            addAll(content.ingredients); add(content.servingNote); addAll(content.quantities)
        } to content.steps
    }
    val stepOffset = chunks.first.size
    val allChunks = chunks.first + chunks.second
    fun play(from: Int) = tts.speak(allChunks, from, Lang.ttsLocale(contentLang), rate)
    val currentStep = (current - stepOffset).takeIf { speaking && it >= 0 }

    val locale = LocalConfiguration.current.locales[0]
    val dateText = remember(dish.updatedAt, locale) { DateFormat.getDateInstance(DateFormat.MEDIUM, locale).format(Date(dish.updatedAt)) }
    val isFav = dish.path in favorites

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(categoryName(dish.categoryId), style = MaterialTheme.typography.titleMedium) },
                navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
                actions = {
                    IconButton({ if (speaking) tts.stop() else play(0) }) {
                        Icon(
                            if (speaking) Icons.Filled.StopCircle else Icons.AutoMirrored.Filled.VolumeUp,
                            stringResource(if (speaking) R.string.tts_stop else R.string.tts_play),
                            tint = MaterialTheme.colorScheme.secondary,
                        )
                    }
                    IconButton({ vm.prefs.toggleFavorite(dish.path) }) {
                        Icon(
                            if (isFav) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                            stringResource(if (isFav) R.string.fav_remove else R.string.fav_add),
                            tint = MaterialTheme.colorScheme.secondary,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // photos referenced by the recipe (downloaded on demand from GitHub)
            if (dish.images.isNotEmpty()) {
                val pager = rememberPagerState { dish.images.size }
                Box(Modifier.fillMaxWidth().aspectRatio(1.4f).clip(RoundedCornerShape(24.dp))) {
                    HorizontalPager(pager, Modifier.fillMaxSize()) { i -> RemoteImage(dish.images[i], Modifier.fillMaxSize()) }
                    if (dish.images.size > 1) Text(
                        "${pager.currentPage + 1}/${dish.images.size}", color = androidx.compose.ui.graphics.Color.White,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp).clip(RoundedCornerShape(50))
                            .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.5f)).padding(horizontal = 10.dp, vertical = 3.dp),
                    )
                }
            }
            // header
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(HeroBrush).padding(20.dp)) {
                Text(shownTitle, color = androidx.compose.ui.graphics.Color.White, style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(6.dp))
                Text(stringResource(R.string.dish_updated, dateText), color = Gold, style = MaterialTheme.typography.labelLarge)
                if (content.intro.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Text(content.intro, color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (needsTranslation) when {
                keys.isEmpty() -> Text(stringResource(R.string.tr_no_key), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                trBusy > 0 || trError == null -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.tr_pending), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                else -> Text(stringResource(R.string.tr_failed), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            // rating: difficulty + estimated calories
            ElegantCard(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.dish_difficulty), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(4.dp))
                        Difficulty(dish.difficulty, withLabel = false)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.dish_calories), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(4.dp))
                        Text(dish.calories?.let { stringResource(R.string.kcal_unit, it) } ?: "—", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }

            // merged ingredients + tools + quantities
            ElegantCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.dish_ingredients), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(8.dp))
                    content.ingredients.forEach { BulletLine(it) }
                    if (content.servingNote.isNotBlank() || content.quantities.isNotEmpty()) {
                        HorizontalDivider(Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.outlineVariant)
                        if (content.servingNote.isNotBlank()) {
                            Text(content.servingNote, style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(6.dp))
                        }
                        content.quantities.forEach { BulletLine(it) }
                    }
                }
            }

            // steps
            ElegantCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.dish_steps), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(8.dp))
                    content.steps.forEachIndexed { i, s ->
                        val active = currentStep == i
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(14.dp))
                                .background(if (active) MaterialTheme.colorScheme.secondaryContainer else androidx.compose.ui.graphics.Color.Transparent)
                                .clickable { play(stepOffset + i) }.padding(8.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Box(
                                Modifier.size(26.dp).clip(CircleShape)
                                    .background(if (active) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("${i + 1}", style = MaterialTheme.typography.labelLarge,
                                    color = if (active) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                            Spacer(Modifier.width(12.dp))
                            Text(s, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun BulletLine(text: String) {
    Row(Modifier.padding(vertical = 2.dp)) {
        Text("•", color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

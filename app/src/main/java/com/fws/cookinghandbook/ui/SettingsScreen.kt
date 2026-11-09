package com.fws.cookinghandbook.ui

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.os.LocaleListCompat
import com.fws.cookinghandbook.AppViewModel
import com.fws.cookinghandbook.BuildConfig
import com.fws.cookinghandbook.R
import com.fws.cookinghandbook.util.AdsConsent
import com.fws.cookinghandbook.util.Lang
import java.util.Locale

private data class LangOption(val tag: String, val flag: String, val native: String)

private val languageOptions = listOf(
    LangOption("en", "🇬🇧", "English"), LangOption("zh-CN", "🇨🇳", "简体中文"),
    LangOption("es", "🇪🇸", "Español"), LangOption("pt", "🇧🇷", "Português"),
    LangOption("ru", "🇷🇺", "Русский"), LangOption("ja", "🇯🇵", "日本語"),
    LangOption("ko", "🇰🇷", "한국어"), LangOption("vi", "🇻🇳", "Tiếng Việt"),
)

@Composable
fun SettingsScreen(vm: AppViewModel, onGuide: () -> Unit) {
    val context = LocalContext.current
    val uri = LocalUriHandler.current
    val theme by vm.prefs.theme.collectAsState()
    val translatePref by vm.prefs.translate.collectAsState()
    val rate by vm.prefs.ttsRate.collectAsState()
    val appLang = remember { Lang.current(context) }
    var langDialog by remember { mutableStateOf(false) }

    val currentTag = AppCompatDelegate.getApplicationLocales().toLanguageTags()
    val selected = languageOptions.firstOrNull { it.tag.substringBefore('-') == currentTag.substringBefore('-') && currentTag.isNotEmpty() }
    val currentLabel = selected?.let { "${it.flag}  ${it.native}" } ?: ("🌐  " + stringResource(R.string.lang_system))

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(stringResource(R.string.tab_settings), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)

        ElegantCard(Modifier.fillMaxWidth(), onClick = onGuide) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.guide_title), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.secondary)
            }
        }

        ElegantCard(Modifier.fillMaxWidth(), onClick = { langDialog = true }) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.settings_language), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                Text(currentLabel, color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.labelLarge)
            }
        }

        ElegantCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text(stringResource(R.string.settings_theme), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0 to R.string.theme_system, 1 to R.string.theme_light, 2 to R.string.theme_dark).forEach { (v, label) ->
                        FilterChip(selected = theme == v, onClick = { vm.prefs.setTheme(v) }, label = { Text(stringResource(label)) })
                    }
                }
            }
        }

        if (appLang != "zh") ElegantCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.settings_translate), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    Switch(checked = translatePref == true, onCheckedChange = { vm.prefs.setTranslate(it) })
                }
                Text(stringResource(R.string.settings_translate_desc), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        ElegantCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.settings_tts_speed), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    Text(rateLabel(rate), color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.labelLarge)
                }
                // 0.5x .. 4x in 0.25 steps, default 2x
                Slider(value = rate, onValueChange = { vm.prefs.setTtsRate(Math.round(it * 4) / 4f) }, valueRange = 0.5f..4f, steps = 13)
            }
        }

        ElegantCard(Modifier.fillMaxWidth(), onClick = { uri.openUri(BuildConfig.PRIVACY_URL) }) {
            Text(stringResource(R.string.settings_privacy), Modifier.padding(16.dp), style = MaterialTheme.typography.titleMedium)
        }
        val activity = context.findActivity()
        if (activity != null && AdsConsent.privacyOptionsRequired(activity)) {
            ElegantCard(Modifier.fillMaxWidth(), onClick = { AdsConsent.showPrivacyOptions(activity) }) {
                Text(stringResource(R.string.settings_ad_privacy), Modifier.padding(16.dp), style = MaterialTheme.typography.titleMedium)
            }
        }
    }

    if (langDialog) LanguageDialog(selectedTag = if (currentTag.isEmpty()) "" else selected?.tag.orEmpty(), onDismiss = { langDialog = false }) { tag ->
        langDialog = false
        AppCompatDelegate.setApplicationLocales(
            if (tag.isEmpty()) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(tag)
        )
    }
}

private fun rateLabel(rate: Float): String = "%.2f".format(Locale.US, rate).trimEnd('0').trimEnd('.') + "x"

/** Flag + the language written in its own script + the same language's name in the current UI language. */
@Composable
private fun LanguageDialog(selectedTag: String, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    val uiLocale = LocalConfiguration.current.locales[0]
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
            Column(Modifier.padding(vertical = 20.dp)) {
                Text(stringResource(R.string.settings_language), Modifier.padding(horizontal = 24.dp),
                    style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(12.dp))
                Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val system = LangOption("", "🌐", stringResource(R.string.lang_system))
                    (listOf(system) + languageOptions).forEach { o ->
                        val on = o.tag == selectedTag
                        val translated = if (o.tag.isEmpty()) null else
                            Locale.forLanguageTag(o.tag).getDisplayLanguage(uiLocale).replaceFirstChar { it.titlecase(uiLocale) }
                                .takeIf { !it.equals(o.native, ignoreCase = true) }
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                                .background(if (on) MaterialTheme.colorScheme.secondaryContainer else androidx.compose.ui.graphics.Color.Transparent)
                                .clickable { onPick(o.tag) }.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.size(38.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                                Text(o.flag, style = MaterialTheme.typography.titleMedium)
                            }
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(o.native, style = MaterialTheme.typography.titleMedium)
                                if (translated != null) Text(translated, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (on) Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.secondary)
                        }
                    }
                }
                Box(Modifier.fillMaxWidth().padding(end = 12.dp, top = 8.dp), contentAlignment = Alignment.CenterEnd) {
                    TextButton(onDismiss) { Text(stringResource(R.string.back)) }
                }
            }
        }
    }
}

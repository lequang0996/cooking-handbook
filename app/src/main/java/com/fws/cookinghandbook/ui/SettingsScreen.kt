package com.fws.cookinghandbook.ui

import android.text.format.DateUtils
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import com.fws.cookinghandbook.AppViewModel
import com.fws.cookinghandbook.BuildConfig
import com.fws.cookinghandbook.R
import com.fws.cookinghandbook.util.AdsConsent
import com.fws.cookinghandbook.util.Lang

@Composable
fun SettingsScreen(vm: AppViewModel) {
    val context = LocalContext.current
    val uri = LocalUriHandler.current
    val theme by vm.prefs.theme.collectAsState()
    val translatePref by vm.prefs.translate.collectAsState()
    val rate by vm.prefs.ttsRate.collectAsState()
    val syncing by vm.syncing.collectAsState()
    val lastSync by vm.lastSync.collectAsState()
    val appLang = remember { Lang.current(context) }
    var langDialog by remember { mutableStateOf(false) }

    val currentTag = AppCompatDelegate.getApplicationLocales().toLanguageTags()
    val currentLabel = Lang.options.firstOrNull { it.first.equals(currentTag, true) || (currentTag.isNotEmpty() && it.first.substringBefore('-') == currentTag.substringBefore('-')) }?.second
        ?: stringResource(R.string.lang_system)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(stringResource(R.string.tab_settings), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)

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
                    Switch(checked = translatePref ?: true, onCheckedChange = { vm.prefs.setTranslate(it) })
                }
                Text(stringResource(R.string.settings_translate_desc), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        ElegantCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.settings_tts_speed), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    Text("%.1fx".format(rate), color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.labelLarge)
                }
                Slider(value = rate, onValueChange = { vm.prefs.setTtsRate((Math.round(it * 10) / 10f)) }, valueRange = 0.5f..1.5f, steps = 9)
            }
        }

        ElegantCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text(stringResource(R.string.settings_sync), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.sync_desc), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                val lastText = if (lastSync == 0L) stringResource(R.string.sync_never)
                else DateUtils.getRelativeTimeSpanString(lastSync, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.last_sync, lastText), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Button({ vm.syncNow() }, enabled = !syncing) { Text(stringResource(if (syncing) R.string.syncing else R.string.sync_now)) }
                }
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

        ElegantCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text(stringResource(R.string.about_title), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.about_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.version_label, BuildConfig.VERSION_NAME), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
            }
        }
    }

    if (langDialog) {
        val options = listOf("" to stringResource(R.string.lang_system)) + Lang.options
        AlertDialog(
            onDismissRequest = { langDialog = false },
            confirmButton = { TextButton({ langDialog = false }) { Text(stringResource(R.string.back)) } },
            title = { Text(stringResource(R.string.settings_language)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    options.forEach { (tag, label) ->
                        val selected = if (tag.isEmpty()) currentTag.isEmpty() else currentLabel == label
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                langDialog = false
                                AppCompatDelegate.setApplicationLocales(
                                    if (tag.isEmpty()) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(tag)
                                )
                            }.padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected, onClick = null)
                            Spacer(Modifier.width(12.dp))
                            Text(label)
                        }
                    }
                }
            },
        )
    }
}

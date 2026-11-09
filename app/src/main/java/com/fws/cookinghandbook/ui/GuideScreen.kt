package com.fws.cookinghandbook.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.fws.cookinghandbook.R

@Composable
fun GuideScreen() {
    val items = listOf(
        R.string.guide_t1 to R.string.guide_b1, R.string.guide_t2 to R.string.guide_b2,
        R.string.guide_t3 to R.string.guide_b3, R.string.guide_t4 to R.string.guide_b4,
        R.string.guide_t5 to R.string.guide_b5, R.string.guide_t6 to R.string.guide_b6,
    )
    LazyColumn(
        Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(stringResource(R.string.guide_title), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(4.dp))
            Ornament()
        }
        itemsIndexed(items) { i, (t, b) ->
            ElegantCard(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
                    Box(
                        Modifier.size(34.dp).clip(CircleShape).background(HeroBrush),
                        contentAlignment = Alignment.Center,
                    ) { Text("${i + 1}", color = Gold, style = MaterialTheme.typography.titleMedium) }
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(stringResource(t), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(4.dp))
                        Text(stringResource(b), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

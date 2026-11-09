package com.fws.cookinghandbook.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.Cake
import androidx.compose.material.icons.outlined.Eco
import androidx.compose.material.icons.outlined.Grain
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.LocalCafe
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.Opacity
import androidx.compose.material.icons.outlined.SetMeal
import androidx.compose.material.icons.outlined.SoupKitchen
import androidx.compose.material.icons.outlined.WbTwilight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fws.cookinghandbook.R
import com.fws.cookinghandbook.data.Categories
import com.fws.cookinghandbook.data.Dish
import com.fws.cookinghandbook.util.TranslationManager

/** Translation settings visible to every composable. */
class Tr(val enabled: Boolean, val target: String, val mgr: TranslationManager) {
    val key = "$enabled-$target"
}

val LocalTr = staticCompositionLocalOf<Tr?> { null }

/** Shows the cached translation immediately, otherwise the original, then swaps in the fresh translation. */
@Composable
fun rememberTranslated(text: String): String {
    val tr = LocalTr.current
    val active = tr != null && tr.enabled
    var out by remember(text, tr?.key) {
        mutableStateOf(if (active) tr!!.mgr.cached(text, "zh", tr.target) ?: text else text)
    }
    LaunchedEffect(text, tr?.key) {
        if (active && out == text) tr!!.mgr.translate(text, "zh", tr.target)?.let { out = it }
    }
    return if (active) out else text
}

/** Dish title with the curated/translated name when translation is on. */
@Composable
fun rememberTranslatedTitle(zh: String): String {
    val tr = LocalTr.current
    val active = tr != null && tr.enabled
    var out by remember(zh, tr?.key) { mutableStateOf(if (active) tr!!.mgr.titleCached(zh, tr.target) ?: zh else zh) }
    LaunchedEffect(zh, tr?.key) {
        if (active && out == zh) tr!!.mgr.title(zh, tr.target)?.let { out = it }
    }
    return if (active) out else zh
}

fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) { if (c is Activity) return c; c = c.baseContext }
    return null
}

fun categoryIcon(id: String): ImageVector = when (id) {
    "aquatic" -> Icons.Outlined.SetMeal
    "breakfast" -> Icons.Outlined.WbTwilight
    "condiment" -> Icons.Outlined.Opacity
    "dessert" -> Icons.Outlined.Cake
    "drink" -> Icons.Outlined.LocalCafe
    "meat_dish" -> Icons.Outlined.LocalFireDepartment
    "semi-finished" -> Icons.Outlined.Inventory2
    "soup" -> Icons.Outlined.SoupKitchen
    "staple" -> Icons.Outlined.Grain
    else -> Icons.Outlined.Eco
}

@Composable
fun categoryName(id: String): String = Categories.find(id)?.let { stringResource(it.nameRes) } ?: id

/** Difficulty as "Difficulty 4/5 ●●●●○" (dots, not stars, so it is not mistaken for a rating). */
@Composable
fun Difficulty(
    level: Int, modifier: Modifier = Modifier, withLabel: Boolean = true,
    dotColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.secondary,
    textColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(
            if (withLabel) stringResource(R.string.difficulty_value, level) else "$level/5",
            style = MaterialTheme.typography.bodyMedium, color = textColor,
        )
        Spacer(Modifier.width(6.dp))
        for (i in 1..5) {
            Box(
                Modifier.padding(horizontal = 1.5.dp).size(8.dp).clip(CircleShape)
                    .then(if (i <= level) Modifier.background(dotColor) else Modifier.border(1.dp, dotColor.copy(alpha = 0.55f), CircleShape))
            )
        }
    }
}

@Composable
fun IconBadge(icon: ImageVector, modifier: Modifier = Modifier, size: Int = 44) {
    Box(
        modifier.size(size.dp).clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size((size * 0.52f).dp)) }
}

@Composable
fun Ornament(modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(width = 36.dp, height = 1.dp).background(MaterialTheme.colorScheme.outline))
        Text("  ✦  ", color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.labelLarge)
        Box(Modifier.size(width = 36.dp, height = 1.dp).background(MaterialTheme.colorScheme.outline))
    }
}

@Composable
fun ElegantCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    val colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    val border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    if (onClick != null) Card(onClick, modifier, shape = shape, colors = colors, border = border) { content() }
    else Card(modifier, shape = shape, colors = colors, border = border) { content() }
}

@Composable
fun DishRow(dish: Dish, favorite: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val title = rememberTranslatedTitle(dish.title)
    ElegantCard(modifier.fillMaxWidth(), onClick) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(categoryIcon(dish.categoryId))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.size(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Difficulty(dish.difficulty)
                    dish.calories?.let {
                        Text(
                            "  ·  " + stringResource(R.string.kcal_unit, it),
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (favorite) Icon(Icons.Filled.Favorite, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(20.dp))
        }
    }
}

package com.fws.cookinghandbook.data

import androidx.annotation.StringRes
import com.fws.cookinghandbook.R

/** One recipe, already parsed from the upstream markdown. Text stays in the source language (Chinese). */
data class Dish(
    val path: String,            // e.g. dishes/aquatic/水煮鱼.md  (stable id)
    val categoryId: String,      // folder name under dishes/
    val title: String,
    val intro: String,
    val difficulty: Int,         // 0..5 stars
    val calories: Int?,          // kcal, null when the recipe has none
    val ingredients: List<String>, // "必备原料和工具" bullets
    val servingNote: String,     // e.g. "以下用量适合 3 至 5 人食用。"
    val quantities: List<String>,  // "计算" bullets
    val steps: List<String>,
    val updatedAt: Long,         // epoch millis of the last upstream commit touching the file
    val sha: String,
)

/** Raw upstream file as stored on disk (markdown + git blob sha + last change). */
data class RawDish(val path: String, val sha: String, val updatedAt: Long, val markdown: String)

data class Category(val id: String, @StringRes val nameRes: Int)

object Categories {
    val all = listOf(
        Category("aquatic", R.string.cat_aquatic),
        Category("breakfast", R.string.cat_breakfast),
        Category("condiment", R.string.cat_condiment),
        Category("dessert", R.string.cat_dessert),
        Category("drink", R.string.cat_drink),
        Category("meat_dish", R.string.cat_meat),
        Category("semi-finished", R.string.cat_semi),
        Category("soup", R.string.cat_soup),
        Category("staple", R.string.cat_staple),
        Category("vegetable_dish", R.string.cat_veg),
    )
    private val byId = all.associateBy { it.id }
    fun find(id: String): Category? = byId[id]
}

data class SyncResult(val ok: Boolean, val changed: Int = 0)

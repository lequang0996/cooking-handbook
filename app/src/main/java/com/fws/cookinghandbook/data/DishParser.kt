package com.fws.cookinghandbook.data

/**
 * Parses the HowToCook dish template. Only the parts the app shows are kept:
 * intro, difficulty, calories, ingredients+tools, quantities, steps. "附加内容" and the rest are dropped.
 */
object DishParser {
    private val caloriesRe = Regex("""预估卡路里[:：]\s*(\d+)""")
    private val stepRe = Regex("""^\s*\d+\s*[.、．]\s*(.*)$""")
    private val imageRe = Regex("""!\[[^\]]*]\([^)]*\)""")
    private val linkRe = Regex("""\[([^\]]*)]\([^)]*\)""")
    private val htmlRe = Regex("""<[^>]+>""")

    private enum class Section { NONE, ING, CALC, STEPS, OTHER }

    fun clean(s: String): String = s
        .replace(imageRe, "")
        .replace(linkRe, "$1")
        .replace(htmlRe, "")
        .replace("**", "").replace("__", "").replace("`", "")
        .trim()

    private fun bullet(line: String): String? {
        val t = line.trim()
        return if (t.startsWith("- ") || t.startsWith("* ") || t.startsWith("+ ")) clean(t.substring(2)) else null
    }

    fun parse(raw: RawDish): Dish? {
        val parts = raw.path.split("/")
        val categoryId = parts.getOrNull(1) ?: return null
        val lines = raw.markdown.replace("\r", "").lines()
        val titleLine = lines.firstOrNull { it.startsWith("# ") } ?: return null
        val title = clean(titleLine.removePrefix("# ")).removeSuffix("的做法").trim()
        if (title.isEmpty()) return null

        val intro = StringBuilder()
        var difficulty = 0
        var calories: Int? = null
        val ingredients = ArrayList<String>()
        val quantities = ArrayList<String>()
        val notes = ArrayList<String>()
        val steps = ArrayList<String>()
        var section = Section.NONE

        for (line in lines.dropWhile { !it.startsWith("# ") }.drop(1)) {
            if (line.startsWith("## ")) {
                val name = line.removePrefix("## ")
                section = when {
                    name.contains("原料") || name.contains("工具") -> Section.ING
                    name.contains("计算") -> Section.CALC
                    name.contains("操作") -> Section.STEPS
                    else -> Section.OTHER
                }
                continue
            }
            if (line.startsWith("#")) continue
            val t = line.trim()
            if (t.isEmpty()) continue
            when (section) {
                Section.NONE -> when {
                    t.startsWith("预估烹饪难度") -> difficulty = t.count { it == '★' }
                    t.startsWith("预估卡路里") -> calories = caloriesRe.find(t)?.groupValues?.get(1)?.toIntOrNull()
                    else -> { val c = clean(t); if (c.isNotEmpty()) { if (intro.isNotEmpty()) intro.append(' '); intro.append(c) } }
                }
                Section.ING -> bullet(line)?.let { if (it.isNotEmpty()) ingredients += it }
                Section.CALC -> {
                    val b = bullet(line)
                    if (b != null) { if (b.isNotEmpty()) quantities += b } else notes += clean(t)
                }
                Section.STEPS -> {
                    val m = stepRe.find(line)
                    if (m != null) {
                        steps += clean(m.groupValues[1])
                    } else if (steps.isNotEmpty()) {
                        val extra = bullet(line)?.let { "• $it" } ?: clean(t)
                        if (extra.isNotEmpty() && extra != "•") steps[steps.lastIndex] = steps.last() + "\n" + extra
                    }
                }
                Section.OTHER -> Unit
            }
        }
        if (steps.isEmpty() && ingredients.isEmpty()) return null
        return Dish(
            path = raw.path, categoryId = categoryId, title = title, intro = intro.toString(),
            difficulty = difficulty.coerceIn(0, 5), calories = calories,
            ingredients = ingredients, servingNote = notes.joinToString(" "), quantities = quantities,
            steps = steps, updatedAt = raw.updatedAt, sha = raw.sha,
        )
    }
}

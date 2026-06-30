package com.etext.editor

import android.graphics.Color

/**
 * A single editor color scheme. All colors are packed ARGB ints.
 *
 * Schemes are intentionally high-contrast: a near-black canvas with a bright,
 * saturated accent — the JetBrains "dark editor" feel.
 */
data class EditorTheme(
    val id: String,
    val displayName: String,
    val isDark: Boolean,
    val background: Int,
    val text: Int,
    val gutterBackground: Int,
    val gutterText: Int,
    val gutterCurrentText: Int,
    val currentLine: Int,
    val accent: Int,
    val selection: Int,
    val toolbarBackground: Int,
    val toolbarText: Int,
    val statusBar: Int,
)

private fun c(hex: String): Int = Color.parseColor(hex)

object Themes {

    // ----- Base themes -----

    val Light = EditorTheme(
        id = "light",
        displayName = "Light",
        isDark = false,
        background = c("#FFFFFF"),
        text = c("#080808"),
        gutterBackground = c("#F2F2F2"),
        gutterText = c("#9A9A9A"),
        gutterCurrentText = c("#1A1A1A"),
        currentLine = c("#FBF9E8"),
        accent = c("#2667D6"),
        selection = c("#A6D2FF"),
        toolbarBackground = c("#E9E9E9"),
        toolbarText = c("#1A1A1A"),
        statusBar = c("#D6D6D6"),
    )

    val Dark = EditorTheme(
        id = "dark",
        displayName = "Dark",
        isDark = true,
        background = c("#1E1F22"),
        text = c("#D7DAE0"),
        gutterBackground = c("#2B2D30"),
        gutterText = c("#5C6066"),
        gutterCurrentText = c("#C9CCD2"),
        currentLine = c("#2A2C32"),
        accent = c("#4D9EFF"),
        selection = c("#2F537A"),
        toolbarBackground = c("#2B2D30"),
        toolbarText = c("#DEE1E6"),
        statusBar = c("#1B1C1F"),
    )

    // ----- Extra high-contrast dark themes -----

    val Shadow = EditorTheme(
        id = "shadow",
        displayName = "Shadow",
        isDark = true,
        background = c("#000000"),
        text = c("#F2F2F2"),
        gutterBackground = c("#0A0A0A"),
        gutterText = c("#4A4A4A"),
        gutterCurrentText = c("#FFFFFF"),
        currentLine = c("#141414"),
        accent = c("#BFC7D1"),
        selection = c("#333A42"),
        toolbarBackground = c("#0A0A0A"),
        toolbarText = c("#F2F2F2"),
        statusBar = c("#000000"),
    )

    val DarkRed = EditorTheme(
        id = "dark_red",
        displayName = "Dark-Red",
        isDark = true,
        background = c("#160A0A"),
        text = c("#FFDFDF"),
        gutterBackground = c("#241010"),
        gutterText = c("#8A4242"),
        gutterCurrentText = c("#FF8A8A"),
        currentLine = c("#2A1010"),
        accent = c("#FF3B3B"),
        selection = c("#5E1A1A"),
        toolbarBackground = c("#1F0C0C"),
        toolbarText = c("#FFC9C9"),
        statusBar = c("#120808"),
    )

    val DarkPurple = EditorTheme(
        id = "dark_purple",
        displayName = "Dark-Purple",
        isDark = true,
        background = c("#120B1E"),
        text = c("#EADCFF"),
        gutterBackground = c("#1E1430"),
        gutterText = c("#7A5AAA"),
        gutterCurrentText = c("#C9A6FF"),
        currentLine = c("#221634"),
        accent = c("#B388FF"),
        selection = c("#3C2168"),
        toolbarBackground = c("#190F2A"),
        toolbarText = c("#D9C6FF"),
        statusBar = c("#0E0817"),
    )

    val DarkPink = EditorTheme(
        id = "dark_pink",
        displayName = "Dark-Pink",
        isDark = true,
        background = c("#1C0A16"),
        text = c("#FFD9EC"),
        gutterBackground = c("#2C1322"),
        gutterText = c("#A85A80"),
        gutterCurrentText = c("#FF8FC4"),
        currentLine = c("#2A1020"),
        accent = c("#FF45A0"),
        selection = c("#5E1B3C"),
        toolbarBackground = c("#240E1B"),
        toolbarText = c("#FFC6E2"),
        statusBar = c("#150712"),
    )

    val DarkYellow = EditorTheme(
        id = "dark_yellow",
        displayName = "Dark-Yellow",
        isDark = true,
        background = c("#161300"),
        text = c("#FFF4C2"),
        gutterBackground = c("#262100"),
        gutterText = c("#8F8030"),
        gutterCurrentText = c("#FFE34D"),
        currentLine = c("#221E00"),
        accent = c("#FFD60A"),
        selection = c("#4E4410"),
        toolbarBackground = c("#1F1A00"),
        toolbarText = c("#FFEBA0"),
        statusBar = c("#100E00"),
    )

    val all: List<EditorTheme> = listOf(
        Light, Dark, Shadow, DarkRed, DarkPurple, DarkPink, DarkYellow
    )

    val default: EditorTheme = Dark

    fun byId(id: String?): EditorTheme = all.firstOrNull { it.id == id } ?: default
}

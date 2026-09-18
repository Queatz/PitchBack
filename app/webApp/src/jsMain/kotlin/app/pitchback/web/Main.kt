package app.pitchback.web

import kotlinx.browser.document
import kotlinx.browser.window
import org.jetbrains.compose.web.renderComposable

fun main() {
    applyTheme()
    renderComposable(rootElementId = "root") {
        App()
    }
}

fun applyTheme() {
    val dark = window.matchMedia("(prefers-color-scheme: dark)").matches
    document.documentElement?.setAttribute("data-theme", if (dark) "dark" else "light")
}

fun systemLang(): AppLang =
    if (window.navigator.language.lowercase().startsWith("vi")) AppLang.VI else AppLang.EN

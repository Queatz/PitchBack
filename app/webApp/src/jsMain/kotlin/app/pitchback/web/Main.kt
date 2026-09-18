package app.pitchback.web

import kotlinx.browser.document
import kotlinx.browser.window
import org.jetbrains.compose.web.renderComposable

fun main() {
    applyTheme()
    watchTheme()
    renderComposable(rootElementId = "root") {
        App()
    }
}

fun applyTheme() {
    val dark = window.matchMedia("(prefers-color-scheme: dark)").matches
    document.documentElement?.setAttribute("data-theme", if (dark) "dark" else "light")
    document.querySelector("meta[name='theme-color']")
        ?.setAttribute("content", if (dark) "#0B0712" else "#f3effa")
}

fun watchTheme() {
    val mq = window.matchMedia("(prefers-color-scheme: dark)")
    val onChange: (dynamic) -> Unit = { applyTheme() }
    try {
        mq.asDynamic().addEventListener("change", onChange)
    } catch (_: Throwable) {
        mq.asDynamic().addListener(onChange)
    }
}

fun systemLang(): AppLang =
    if (window.navigator.language.lowercase().startsWith("vi")) AppLang.VI else AppLang.EN

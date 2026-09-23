package app.pitchback.web

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import app.pitchback.api.AdminPendingDto
import app.pitchback.api.InboxDto
import app.pitchback.api.MeDto
import app.pitchback.api.PublicUser
import kotlinx.browser.window
import kotlinx.coroutines.launch
import org.jetbrains.compose.web.attributes.ButtonType
import org.jetbrains.compose.web.attributes.type
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text
import org.w3c.dom.events.Event

sealed class Screen {
    data object Feed : Screen()
    data class Pitch(val id: String) : Screen()
    data object Inbox : Screen()
    data object Me : Screen()
    data object Admin : Screen()
    data object Places : Screen()
}

fun parsePath(pathname: String): Screen {
    val parts = pathname.trim('/').split('/').filter { it.isNotEmpty() }
    return when {
        parts.isEmpty() -> Screen.Feed
        parts[0] == "inbox" -> Screen.Inbox
        parts[0] == "me" -> Screen.Me
        parts[0] == "admin" -> Screen.Admin
        parts[0] == "places" -> Screen.Places
        parts[0] == "pitch" && parts.size > 1 -> Screen.Pitch(parts[1])
        else -> Screen.Feed
    }
}

fun parseHash(hash: String): Screen {
    val parts = hash.removePrefix("#").trim('/').split('/').filter { it.isNotEmpty() }
    return when {
        parts.isEmpty() -> Screen.Feed
        parts[0] == "inbox" -> Screen.Inbox
        parts[0] == "me" -> Screen.Me
        parts[0] == "admin" -> Screen.Admin
        parts[0] == "places" -> Screen.Places
        parts[0] == "pitch" && parts.size > 1 -> Screen.Pitch(parts[1])
        else -> Screen.Feed
    }
}

fun Screen.path(): String = when (this) {
    Screen.Feed -> "/"
    Screen.Inbox -> "/inbox"
    Screen.Me -> "/me"
    Screen.Admin -> "/admin"
    Screen.Places -> "/places"
    is Screen.Pitch -> "/pitch/$id"
}

var route by mutableStateOf<Screen>(Screen.Feed)

fun readScreen(): Screen {
    val hash = window.location.hash
    if (hash.startsWith("#/") || hash == "#") {
        val fromHash = parseHash(hash)
        window.history.replaceState(null, "", fromHash.path())
        return fromHash
    }
    return parsePath(window.location.pathname)
}

fun go(screen: Screen) {
    val path = screen.path()
    if (window.location.pathname != path) {
        window.history.pushState(null, "", path)
    }
    route = screen
    window.scrollTo(0.0, 0.0)
    kotlinx.browser.document.documentElement?.scrollTop = 0.0
    kotlinx.browser.document.body?.scrollTop = 0.0
}

@Composable
fun App() {
    val screen = route
    LaunchedEffect(screen) {
        window.scrollTo(0.0, 0.0)
        kotlinx.browser.document.documentElement?.scrollTop = 0.0
        kotlinx.browser.document.body?.scrollTop = 0.0
    }
    var user by remember { mutableStateOf<PublicUser?>(null) }
    var remainingSlots by remember { mutableStateOf(0) }
    var adminPending by remember { mutableStateOf<AdminPendingDto?>(null) }
    var verificationPending by remember { mutableStateOf(false) }
    var inbox by remember { mutableStateOf(InboxDto()) }
    var langOverride by remember { mutableStateOf<AppLang?>(null) }
    var feedTick by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()
    val lang = langOverride ?: systemLang()
    requestLang = if (lang == AppLang.VI) "vi" else "en"
    val t = T(lang)

    fun applyMe(me: MeDto) {
        user = me.user
        remainingSlots = me.remainingSlots
        adminPending = me.adminPending
        verificationPending = me.verificationPending
        langOverride = when (me.user.language) {
            "vi" -> AppLang.VI
            "en" -> AppLang.EN
            else -> null
        }
    }

    fun refreshMe() {
        scope.launch {
            try {
                applyMe(Api.me())
            } catch (_: Throwable) {
                user = null
                adminPending = null
            }
        }
    }

    fun refreshInbox() {
        if (user == null) return
        scope.launch {
            try {
                inbox = Api.inbox()
            } catch (_: Throwable) {
            }
        }
    }

    DisposableEffect(Unit) {
        route = readScreen()
        val listener: (Event) -> Unit = { route = readScreen() }
        window.addEventListener("popstate", listener)
        onDispose { window.removeEventListener("popstate", listener) }
    }

    LaunchedEffect(Unit) {
        refreshMe()
        runCatching { Api.recordVisit() }
    }

    DisposableEffect(user?.id) {
        refreshInbox()
        if (user == null) {
            onDispose { }
        } else {
            val url = apiBase() + "/api/events"
            val es = createEventSource(url)
            val onInbox: (dynamic) -> Unit = { refreshInbox(); refreshMe() }
            val onFeed: (dynamic) -> Unit = { feedTick += 1 }
            val onMe: (dynamic) -> Unit = { refreshMe() }
            es.addEventListener("inbox", onInbox)
            es.addEventListener("feed", onFeed)
            es.addEventListener("me", onMe)
            onDispose { es.close() }
        }
    }

    Div({ classes("app-shell") }) {
        Div({ classes("desktop-nav") }) {
            Div({ classes("brand") }) { Text("✦ ${t.appName}") }
            NavButtons(t, screen, inbox.badge, desktop = true)
        }
        Div({ classes("main") }) {
            when (val s = screen) {
                Screen.Feed -> FeedScreen(t, user, remainingSlots, feedTick)
                is Screen.Pitch -> PitchScreen(t, s.id, user, lang)
                Screen.Inbox -> InboxScreen(t, user, inbox, lang) { refreshInbox() }
                Screen.Me -> MeScreen(t, user, remainingSlots, adminPending, verificationPending, lang, langOverride, ::applyMe) {
                    user = null
                    verificationPending = false
                    inbox = InboxDto()
                    go(Screen.Feed)
                }
                Screen.Admin -> AdminScreen(t, user, lang)
                Screen.Places -> PlacesScreen(t, user, lang)
            }
        }
        Div({ classes("bottom-nav") }) {
            NavButtons(t, screen, inbox.badge, desktop = false)
        }
        NoticeDialog(t)
        ConfirmDialog(t)
    }
}

@Composable
private fun NavButtons(t: T, screen: Screen, badge: Int, desktop: Boolean) {
    NavItem(t.inbox, screen is Screen.Inbox, desktop, badge, true) { go(Screen.Inbox) }
    NavItem(t.pitches, screen is Screen.Feed || screen is Screen.Pitch, desktop, badge, false) { go(Screen.Feed) }
    NavItem(t.me, screen is Screen.Me || screen is Screen.Admin || screen is Screen.Places, desktop, badge, false) { go(Screen.Me) }
}

@Composable
private fun NavItem(
    label: String,
    active: Boolean,
    desktop: Boolean,
    badge: Int,
    showBadge: Boolean,
    onClick: () -> Unit,
) {
    Button({
        classes("nav-item")
        if (desktop) classes("btn", "btn-ghost")
        if (active) classes("active")
        type(ButtonType.Button)
        onClick { onClick() }
    }) {
        Text(label)
        if (showBadge && badge > 0) Span({ classes("badge") }) { Text(badge.toString()) }
    }
}

fun createEventSource(url: String): dynamic =
    js("new EventSource(url, { withCredentials: true })")

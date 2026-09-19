package app.pitchback.web

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import app.pitchback.api.AdminPendingDto
import app.pitchback.api.CreatePitchRequest
import app.pitchback.api.InboxDto
import app.pitchback.api.LocationDto
import app.pitchback.api.LocationRecDto
import app.pitchback.api.LocationWriteRequest
import app.pitchback.api.LoginRequest
import app.pitchback.api.MeDto
import app.pitchback.api.MeetDto
import app.pitchback.api.NoShowDto
import app.pitchback.api.OpeningHours
import app.pitchback.api.PitchCardDto
import app.pitchback.api.PublicUser
import app.pitchback.api.RecommendLocationRequest
import app.pitchback.api.RegisterRequest
import app.pitchback.api.TopicDto
import app.pitchback.api.UpdateProfileRequest
import app.pitchback.api.UserAdminDto
import app.pitchback.api.VerificationDto
import app.pitchback.api.WeeklyAvailability
import app.pitchback.domain.OfferedSlot
import app.pitchback.domain.PITCH_BACK_TEXT_MIN
import app.pitchback.domain.PITCH_TEXT_MIN
import app.pitchback.domain.meetTimeHasStarted
import app.pitchback.domain.nextOfferedSlots
import app.pitchback.domain.validateAvailability
import kotlin.js.Date
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.web.attributes.ButtonType
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.attributes.placeholder
import org.jetbrains.compose.web.attributes.selected
import org.jetbrains.compose.web.attributes.type
import org.jetbrains.compose.web.attributes.value
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.H1
import org.jetbrains.compose.web.dom.H3
import org.jetbrains.compose.web.dom.Img
import org.jetbrains.compose.web.dom.Input
import org.jetbrains.compose.web.dom.Label
import org.jetbrains.compose.web.dom.Option
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.PasswordInput
import org.jetbrains.compose.web.dom.Select
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text
import org.jetbrains.compose.web.dom.TextInput

@Composable
fun FeedScreen(t: T, user: PublicUser?, remainingSlots: Int, tick: Int) {
    var items by remember { mutableStateOf(listOf<PitchCardDto>()) }
    var loading by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    fun load(reset: Boolean) {
        if (loading) return
        loading = true
        scope.launch {
            try {
                val before = if (reset) null else items.lastOrNull()?.createdAt
                val res = Api.feed(before)
                items = if (reset) res.items else items + res.items
                done = res.items.isEmpty()
            } catch (e: Throwable) {
                showNotice(e.message)
            }
            loading = false
        }
    }
    LaunchedEffect(tick) { load(true) }
    Div({
        onScroll {
            val el = it.target.unsafeCast<org.w3c.dom.HTMLElement>()
            if (!done && !loading && el.scrollTop + el.clientHeight > el.scrollHeight - 240) load(false)
        }
    }) {
        Div({ classes("topbar") }) {
            H1 { Text(t.pitches); Span({ classes("spark") }) { Text("✦") } }
            if (user != null) Div({ classes("flame") }) { Text("🔥 "); Span { Text("$remainingSlots") } }
        }
        if (items.isEmpty() && !loading) Div({ classes("empty") }) { Text(t.emptyFeed) }
        Div({ classes("feed-grid") }) {
            items.forEach { card ->
                PitchCardView(t, card, onOpen = { go(Screen.Pitch(card.id)) }, onPitchBack = {
                    if (user == null) go(Screen.Me) else go(Screen.Pitch(card.id))
                })
            }
        }
        if (loading) Div({ classes("empty") }) { Text(t.loading) }
    }
}

@Composable
fun PitchScreen(t: T, id: String, user: PublicUser?, lang: AppLang) {
    var card by remember { mutableStateOf<PitchCardDto?>(null) }
    var text by remember { mutableStateOf("") }
    var selectedAt by remember { mutableStateOf<Long?>(null) }
    var flash by remember { mutableStateOf(false) }
    var profileUser by remember { mutableStateOf<PublicUser?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(id) {
        try {
            card = Api.pitch(id)
        } catch (e: Throwable) {
            showCaught(e)
        }
    }
    val c = card
    Div {
        Div({ classes("topbar") }) {
            Button({ classes("icon-btn"); type(ButtonType.Button); onClick { go(Screen.Feed) } }) { Text("←") }
            H1 { Text(if (c == null) t.pitchBack else "${t.pitchBack} ${c.author.name}") }
            Span {}
        }
        if (c == null) {
            Div({ classes("empty") }) { Text(t.loading) }
        } else {
            PhotoScroller(c.photoUrls)
            Div({ classes("card-body") }) {
                PersonDetails(c.author) { profileUser = c.author }
                Div({ classes("meta") }) {
                    Span({ classes("chip"); classes("chip-green") }) { Text("⚡ ${t.pitchesLeft(c.remainingPitchBacks)}") }
                    if (c.topic.isNotBlank()) Span({ classes("chip"); classes("chip-topic") }) { Text(c.topic) }
                }
                P({ classes("quote-label") }) { Text(t.thePitch) }
                P({ classes("quote") }) { Text(c.text) }
                LocationBlock(t, c.location, header = t.theLocation)
                if (c.isOwner) {
                    GhostButton(t.yourPitch) { go(Screen.Me) }
                } else if (user == null) {
                    GradButton(t.signInToPitch) { go(Screen.Me) }
                } else {
                    val slots = c.nextSlots.map { OfferedSlot(it.at, it.time) }
                    SlotPicker(t, slots, selectedAt) { selectedAt = it }
                    AiField(t, t.pitchBack, text, { text = it }, "pitchBack", t.enterPitch, null, lang, PITCH_BACK_TEXT_MIN)
                    GradButton("${t.pitchBack} ⚡", flash) {
                        val slot = selectedAt
                        if (slot == null) {
                            showNotice(t.pickTimeSlot)
                            return@GradButton
                        }
                        scope.launch {
                            try {
                                Api.pitchBack(id, text, slot)
                                flash = true
                                showNotice(t.pitchedBackTo(c.author.name))
                                go(Screen.Inbox)
                            } catch (e: Throwable) {
                                showCaught(e)
                            }
                        }
                    }
                }
            }
        }
    }
    profileUser?.let { u -> ProfileDialog(t, u) { profileUser = null } }
}

@Composable
fun InboxScreen(t: T, user: PublicUser?, inbox: InboxDto, lang: AppLang, refresh: () -> Unit) {
    val scope = rememberCoroutineScope()
    var sticker by remember { mutableStateOf<MeetDto?>(null) }
    var endId by remember { mutableStateOf<String?>(null) }
    var finalText by remember { mutableStateOf("") }
    var noShowMeet by remember { mutableStateOf<MeetDto?>(null) }
    var noShowText by remember { mutableStateOf("") }
    var noShowBusy by remember { mutableStateOf(false) }
    var profileUser by remember { mutableStateOf<PublicUser?>(null) }
    var nowMs by remember { mutableStateOf(Date.now().toLong()) }
    LaunchedEffect(inbox.meets) {
        while (true) {
            nowMs = Date.now().toLong()
            val next = inbox.meets.mapNotNull { it.slotAt }.filter { it > nowMs }.minOrNull() ?: break
            delay((next - nowMs).coerceIn(250L, 30_000L))
        }
    }
    Div {
        Div({ classes("topbar") }) { H1 { Text(t.inbox) } }
        if (user == null) {
            Div({ classes("empty") }) { Text(t.signInInbox); GradButton(t.signIn) { go(Screen.Me) } }
            return@Div
        }
        if (inbox.finalMessages.isEmpty() && inbox.meets.isEmpty() && inbox.cancelledMeets.isEmpty() && inbox.incoming.isEmpty() && inbox.outgoing.isEmpty()) {
            Div({ classes("empty") }) { Text(t.emptyInbox) }
        }
        if (inbox.finalMessages.isNotEmpty()) {
            Div({ classes("section-title") }) { Text(t.finalMessages) }
            inbox.finalMessages.forEach { meet ->
                Div({ classes("inbox-card") }) {
                    PersonDetails(meet.otherUser) { profileUser = meet.otherUser }
                    meet.finalMessage?.let { msg -> P({ classes("preview") }) { Text(msg) } }
                    GhostButton(t.dismiss) { scope.launch { runCatching { Api.dismissMeet(meet.id) }; refresh() } }
                }
            }
        }
        if (inbox.meets.isNotEmpty()) {
            Div({ classes("section-title") }) { Text(t.activeMeets) }
            inbox.meets.forEach { meet ->
                Div({ classes("inbox-card") }) {
                    PersonDetails(meet.otherUser) { profileUser = meet.otherUser }
                    P({ classes("preview") }) { Text(meet.pitchPreview) }
                    P({ classes("muted") }) { Text("📍 ${t.meetAt} ${meet.locationName}") }
                    MeetSlotLine(t, meet.slotAt)
                    val meetStarted = meetTimeHasStarted(meet.slotAt, nowMs)
                    Div({ classes("row-btns") }) {
                        if (meetStarted) {
                            GradButton(t.endMeet) { endId = meet.id }
                        }
                        GhostButton(t.cancelMeet) { scope.launch { runCatching { Api.cancelMeet(meet.id) }; refresh() } }
                        if (meet.reportedNoShow) {
                            P({ classes("muted") }) { Text(t.reportedNoShow) }
                        } else if (meetStarted) {
                            GhostButton(t.noShow) { noShowMeet = meet; noShowText = "" }
                        }
                    }
                }
            }
        }
        if (inbox.cancelledMeets.isNotEmpty()) {
            Div({ classes("section-title") }) { Text(t.cancelledMeets) }
            inbox.cancelledMeets.forEach { meet ->
                Div({ classes("inbox-card") }) {
                    H3 { Text(meet.otherUser.name) }
                    P { Text("${t.cancelledMeets}${meet.cancelledByName?.let { " · $it" } ?: ""}") }
                    GhostButton(t.dismiss) { scope.launch { runCatching { Api.dismissMeet(meet.id) }; refresh() } }
                }
            }
        }
        if (inbox.incoming.isNotEmpty()) {
            Div({ classes("section-title") }) { Text(t.incoming) }
            inbox.incoming.forEachIndexed { i, pb ->
                Div({ classes("inbox-card"); if (i % 2 == 1) classes("alt") }) {
                    PersonDetails(pb.fromUser) { profileUser = pb.fromUser }
                    P({ classes("preview") }) { Text("“${pb.pitchPreview}”") }
                    P { Text(pb.text) }
                    P({ classes("muted") }) { Text("📍 ${t.meetAt} ${pb.locationName}") }
                    MeetSlotLine(t, pb.slotAt)
                    Div({ classes("row-btns") }) {
                        GradButton(t.acceptPitch) { scope.launch { runCatching { Api.accept(pb.id) }.onFailure { showNotice(it.message) }; refresh() } }
                        GhostButton(t.dismiss) { scope.launch { runCatching { Api.pass(pb.id) }; refresh() } }
                    }
                }
            }
        }
        if (inbox.outgoing.isNotEmpty()) {
            Div({ classes("section-title") }) { Text(t.outgoing) }
            inbox.outgoing.forEach { pb ->
                Div({ classes("inbox-card") }) {
                    PersonDetails(pb.toUser) { profileUser = pb.toUser }
                    P { Text(pb.text) }
                    MeetSlotLine(t, pb.slotAt)
                    GhostButton(t.cancelPitchBack) { scope.launch { runCatching { Api.cancelPitchBack(pb.id) }; refresh() } }
                }
            }
        }
    }
    profileUser?.let { u -> ProfileDialog(t, u) { profileUser = null } }
    if (noShowMeet != null) {
        Div({ classes("modal-back"); onClick { noShowMeet = null } }) {
            Div({ classes("modal"); onClick { it.stopPropagation() } }) {
                H3 { Text(t.noShow) }
                P { Text(t.confirmNoShow) }
                AiField(t, "", noShowText, { noShowText = it }, "noShow", t.noShowExplain, null, lang)
                GradButton(t.noShow) {
                    val meet = noShowMeet ?: return@GradButton
                    if (meet.reportedNoShow || noShowBusy) return@GradButton
                    noShowBusy = true
                    scope.launch {
                        try {
                            Api.noShow(meet.id, noShowText)
                            noShowMeet = null
                            noShowText = ""
                            refresh()
                        } catch (e: Throwable) {
                            showCaught(e)
                        }
                        noShowBusy = false
                    }
                }
                GhostButton(t.cancel) { noShowMeet = null }
            }
        }
    }
    if (endId != null) {
        Div({ classes("modal-back"); onClick { endId = null } }) {
            Div({ classes("modal"); onClick { it.stopPropagation() } }) {
                H3 { Text(t.finalMessage) }
                AiField(t, "", finalText, { finalText = it }, "final", t.optional, null, lang)
                GradButton(t.endMeet) {
                    val id = endId!!
                    scope.launch {
                        try {
                            val meet = Api.endMeet(id, finalText)
                            endId = null
                            finalText = ""
                            refresh()
                            if (meet.allowSocialPosting) sticker = meet
                        } catch (e: Throwable) {
                            showCaught(e)
                        }
                    }
                }
                GhostButton(t.skipEnd) {
                    val id = endId!!
                    scope.launch {
                        try {
                            val meet = Api.endMeet(id, "")
                            endId = null
                            finalText = ""
                            refresh()
                            if (meet.allowSocialPosting) sticker = meet
                        } catch (e: Throwable) {
                            showCaught(e)
                        }
                    }
                }
            }
        }
    }
    sticker?.let { m ->
        WeMetSticker(t, m.myPhotoUrl, m.otherUser.photoUrl) { sticker = null }
    }
}

@Composable
fun MeScreen(
    t: T,
    user: PublicUser?,
    remainingSlots: Int,
    adminPending: AdminPendingDto?,
    verificationPending: Boolean,
    lang: AppLang,
    langOverride: AppLang?,
    applyMe: (MeDto) -> Unit,
    onLogout: () -> Unit,
) {
    if (user == null) AuthPanel(t, applyMe) else ProfilePanel(t, user, remainingSlots, adminPending, verificationPending, lang, langOverride, applyMe, onLogout)
}

@Composable
private fun AuthPanel(t: T, applyMe: (MeDto) -> Unit) {
    var register by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    Div {
        Div({ classes("topbar") }) { H1 { Text(t.me) } }
        P({ classes("muted") }) { Text(t.guestMe) }
        if (register) {
            Div({ classes("field") }) { Label { Text(t.name) }; TextInput(name) { onInput { name = it.value } } }
        }
        Div({ classes("field") }) { Label { Text(t.phone) }; TextInput(phone) { onInput { phone = it.value } } }
        Div({ classes("field") }) { Label { Text(t.password) }; PasswordInput(password) { onInput { password = it.value } } }
        GradButton(if (register) t.register else t.signIn) {
            scope.launch {
                try {
                    val me = if (register) Api.register(RegisterRequest(name, phone, password))
                    else Api.login(LoginRequest(phone, password))
                    applyMe(me)
                } catch (e: Throwable) {
                    showNotice(e.message)
                }
            }
        }
        Spacer()
        GhostButton(if (register) t.haveAccount else t.needAccount) { register = !register }
    }
}

@Composable
private fun ProfilePanel(
    t: T,
    user: PublicUser,
    remainingSlots: Int,
    adminPending: AdminPendingDto?,
    verificationPending: Boolean,
    lang: AppLang,
    langOverride: AppLang?,
    applyMe: (MeDto) -> Unit,
    onLogout: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(user.name) }
    var intro by remember { mutableStateOf(user.intro) }
    var photo by remember { mutableStateOf(user.photoUrl ?: "") }
    var phone by remember { mutableStateOf(user.phone ?: "") }
    var zalo by remember { mutableStateOf(user.phone ?: "") }
    var myPitches by remember { mutableStateOf(listOf<PitchCardDto>()) }
    var topics by remember { mutableStateOf(listOf<TopicDto>()) }
    var locations by remember { mutableStateOf(listOf<LocationDto>()) }
    var editing by remember { mutableStateOf<PitchCardDto?>(null) }
    LaunchedEffect(user.id) {
        runCatching { myPitches = Api.myPitches().items }
        runCatching { topics = Api.topics() }
        runCatching { locations = Api.locations() }
    }
    Div {
        Div({ classes("topbar") }) { H1 { Text(t.me) } }
        if (photo.isNotBlank()) Img(src = assetUrl(photo), alt = "") { attr("style", "width:88px;height:88px;border-radius:50%;object-fit:cover") }
        PhotoPicker(t, if (photo.isBlank()) emptyList() else listOf(photo), t.profilePhoto, multiple = false) { photo = it.lastOrNull().orEmpty() }
        Div({ classes("field") }) { Label { Text(t.name) }; TextInput(name) { onInput { name = it.value } } }
        Div({ classes("field") }) { Label { Text(t.phone) }; TextInput(phone) { onInput { phone = it.value } } }
        AiField(t, t.intro, intro, { intro = it }, "intro", t.intro, null, lang)
        Div({ classes("field") }) {
            Label { Text(t.language) }
            Select({
                onChange { ev ->
                    val v = ev.value
                    scope.launch {
                        try {
                            applyMe(Api.updateMe(UpdateProfileRequest(language = v)))
                        } catch (e: Throwable) {
                            showNotice(e.message)
                        }
                    }
                }
            }) {
                Option("", { if (langOverride == null) selected() }) { Text(t.systemLang) }
                Option("en", { if (langOverride == AppLang.EN) selected() }) { Text("English") }
                Option("vi", { if (langOverride == AppLang.VI) selected() }) { Text("Tiếng Việt") }
            }
        }
        GradButton(t.save) {
            scope.launch {
                try {
                    applyMe(
                        Api.updateMe(
                            UpdateProfileRequest(
                                name = name,
                                intro = intro,
                                photoUrl = photo.ifBlank { null },
                                phone = phone,
                            ),
                        ),
                    )
                    showNotice(t.profileUpdated)
                } catch (e: Throwable) {
                    showNotice(e.message)
                }
            }
        }
        if (user.verified) P { Span({ classes("verified") }) { Text("✔ ${t.verified}") } }
        else {
            Div({ classes("field") }) { Label { Text(t.zaloPhone) }; TextInput(zalo) { onInput { zalo = it.value } } }
            GhostButton(if (verificationPending) t.verificationRequested else t.requestVerify) {
                if (verificationPending) {
                    showNotice(t.verifyPending)
                    return@GhostButton
                }
                scope.launch {
                    runCatching { Api.verify(zalo) }
                        .onFailure { showNotice(it.message) }
                        .onSuccess {
                            showNotice(t.verifySent)
                            runCatching { applyMe(Api.me()) }
                        }
                }
            }
        }
        if (user.isAdmin) {
            val total = (adminPending?.pitches ?: 0) + (adminPending?.verifications ?: 0) + (adminPending?.noShows ?: 0) + (adminPending?.locationRecs ?: 0)
            Div({ classes("pending-pill") }) {
                Div {
                    H3 { Text(t.adminEntry) }
                    P({ classes("muted") }) { Text(t.pendingTasks(total)) }
                }
                Button({ classes("btn", "btn-lime"); type(ButtonType.Button); attr("style", "width:auto"); onClick { go(Screen.Admin) } }) { Text(t.admin) }
            }
        }
        Div({ classes("section-title") }) { Text("${t.myPitches} · 🔥 $remainingSlots") }
        myPitches.forEach { p ->
            Div({ classes("card") }) {
                Div({ classes("card-body") }) {
                    P { Text(p.text.take(100)) }
                    P({ classes("muted") }) { Text(when (p.status) { "pending" -> t.pending; "approved" -> t.approved; else -> t.dismissed }) }
                    Div({ classes("grid-2") }) {
                        Button({ classes("btn", "btn-ghost"); type(ButtonType.Button); onClick {
                            val n = (p.remainingPitchBacks - 1).coerceAtLeast(0)
                            scope.launch { runCatching { Api.updateRemaining(p.id, n) }; runCatching { myPitches = Api.myPitches().items } }
                        } }) { Text("−") }
                        Button({ classes("btn", "btn-ghost"); type(ButtonType.Button); onClick {
                            val n = (p.remainingPitchBacks + 1).coerceAtMost(99)
                            scope.launch { runCatching { Api.updateRemaining(p.id, n) }; runCatching { myPitches = Api.myPitches().items } }
                        } }) { Text("+ ${p.remainingPitchBacks}") }
                    }
                    Spacer()
                    GhostButton(t.edit) { editing = p }
                    Spacer()
                    GhostButton(t.dismiss) {
                        showConfirm(t.dismiss, t.confirmDismissPitch, t.dismiss) {
                            scope.launch {
                                runCatching { Api.deletePitch(p.id) }
                                    .onFailure { showNotice(it.message) }
                                    .onSuccess {
                                        runCatching { myPitches = Api.myPitches().items }
                                        runCatching { applyMe(Api.me()) }
                                    }
                            }
                        }
                    }
                }
            }
        }
        CreatePitchForm(t, lang, topics, locations) {
            scope.launch { runCatching { myPitches = Api.myPitches().items } }
        }
        editing?.let { pitch ->
            Div({ classes("modal-back"); onClick { editing = null } }) {
                Div({
                    classes("modal", "modal-scroll")
                    onClick { it.stopPropagation() }
                }) {
                    CreatePitchForm(t, lang, topics, locations, existing = pitch) {
                        editing = null
                        scope.launch { runCatching { myPitches = Api.myPitches().items } }
                    }
                    GhostButton(t.close) { editing = null }
                }
            }
        }
        Spacer()
        GhostButton(t.recs) { go(Screen.Places) }
        Spacer()
        GhostButton(t.signOut) {
            scope.launch { runCatching { Api.logout() }; onLogout() }
        }
    }
}

@Composable
private fun CreatePitchForm(
    t: T,
    lang: AppLang,
    topics: List<TopicDto>,
    locations: List<LocationDto>,
    existing: PitchCardDto? = null,
    onDone: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val key = existing?.id
    var text by remember(key) { mutableStateOf(existing?.text.orEmpty()) }
    var photos by remember(key) { mutableStateOf(existing?.photoUrls.orEmpty()) }
    var remaining by remember(key) { mutableStateOf(existing?.remainingPitchBacks ?: 3) }
    var until by remember(key) { mutableStateOf(existing?.availableUntil?.let { epochToDateTimeLocal(it) }.orEmpty()) }
    var topic by remember(key) { mutableStateOf(existing?.topic.orEmpty()) }
    var locationId by remember(key) { mutableStateOf(existing?.location?.id.orEmpty()) }
    var social by remember(key) { mutableStateOf(existing?.allowSocialPosting ?: true) }
    var availability by remember(key) { mutableStateOf(existing?.availability ?: WeeklyAvailability()) }
    var pickOpen by remember { mutableStateOf(false) }
    val selected = locations.find { it.id == locationId }
    val untilMs = until.takeIf { it.isNotBlank() }?.let { Date.parse(it).toLong() }
    val preview = nextOfferedSlots(availability, Date.now().toLong(), availableUntil = untilMs)
    Div({ classes("section-title") }) { Text(if (existing == null) t.createPitch else t.edit) }
    AiField(t, t.thePitch, text, { text = it }, "pitch", t.enterPitch, null, lang, PITCH_TEXT_MIN)
    PhotoPicker(t, photos) { photos = it }
    Div({ classes("field") }) {
        Label { Text(t.remaining) }
        Div({ classes("grid-2") }) {
            Button({ classes("btn", "btn-ghost"); type(ButtonType.Button); onClick { remaining = (remaining - 1).coerceAtLeast(1) } }) { Text("−") }
            Button({ classes("btn", "btn-ghost"); type(ButtonType.Button); onClick { remaining = (remaining + 1).coerceAtMost(99) } }) { Text("+ $remaining") }
        }
    }
    Div({ classes("field") }) {
        Label { Text(t.availableUntil) }
        Input(InputType.DateTimeLocal) { value(until); onInput { until = it.value } }
    }
    Div({ classes("field") }) {
        Label { Text(t.topic) }
        TextInput(topic) { onInput { topic = it.value }; placeholder(topics.joinToString { it.topic }) }
    }
    Div({ classes("field") }) {
        Label { Text(t.location) }
        Button({
            classes("btn", "btn-ghost", "location-pick")
            type(ButtonType.Button)
            onClick { pickOpen = true }
        }) { Text(selected?.name ?: t.pickLocation) }
    }
    ThemeSwitch(t.allowSocial, social) { social = it }
    AvailabilityEditor(t, availability) { availability = it }
    if (preview.isNotEmpty()) {
        Div({ classes("hint") }) { Text(t.nextTimes) }
        preview.forEach { slot ->
            P({ classes("slot-preview") }) { Text("⏱ " + formatMeetSlot(t, slot.at)) }
        }
    }
    if (pickOpen) {
        LocationPickerDialog(t, locations, onPick = {
            locationId = it.id
            pickOpen = false
        }, onClose = { pickOpen = false })
    }
    Spacer()
    GradButton(if (existing == null) t.submitReview else t.save) {
        if (locationId.isBlank()) {
            showNotice(t.pickLocation)
            return@GradButton
        }
        if (validateAvailability(availability) != null) {
            showNotice(t.pickTimeSlot)
            return@GradButton
        }
        scope.launch {
            try {
                val body = CreatePitchRequest(
                    text = text,
                    photoUrls = photos,
                    remainingPitchBacks = remaining,
                    availableUntil = untilMs,
                    topic = topic,
                    locationId = locationId,
                    allowSocialPosting = social,
                    availability = availability,
                )
                if (existing != null) {
                    Api.updatePitch(existing.id, body)
                } else {
                    Api.createPitch(body)
                    text = ""
                    photos = emptyList()
                    remaining = 3
                    until = ""
                    topic = ""
                    locationId = ""
                    social = true
                    availability = WeeklyAvailability()
                    showNotice(t.pitchSubmitted)
                }
                onDone()
            } catch (e: Throwable) {
                showNotice(e.message)
            }
        }
    }
}

@Composable
fun PlacesScreen(t: T, user: PublicUser?, lang: AppLang) {
    val scope = rememberCoroutineScope()
    var locations by remember { mutableStateOf(listOf<LocationDto>()) }
    var name by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { runCatching { locations = Api.locations() } }
    Div {
        Div({ classes("topbar") }) {
            Button({ classes("icon-btn"); type(ButtonType.Button); onClick { go(Screen.Me) } }) { Text("←") }
            H1 { Text(t.recs) }
            Span {}
        }
        P({ classes("muted") }) { Text(t.placeForumIntro) }
        locations.forEach { loc ->
            LocationBlock(t, loc)
        }
        Div({ classes("section-title") }) { Text(t.recommendPlace) }
        if (user == null) {
            GradButton(t.signIn) { go(Screen.Me) }
        } else {
            Div({ classes("field") }) { Label { Text(t.placeName) }; TextInput(name) { onInput { name = it.value } } }
            Div({ classes("field") }) { Label { Text(t.address) }; TextInput(address) { onInput { address = it.value } } }
            AiField(t, t.note, note, { note = it }, "location", t.note, null, lang)
            GradButton(t.submitPlace) {
                scope.launch {
                    try {
                        Api.recommendLocation(RecommendLocationRequest(name = name, description = note, address = address))
                        name = ""; address = ""; note = ""
                        showNotice(t.placeSubmitted)
                    } catch (e: Throwable) {
                        showNotice(e.message)
                    }
                }
            }
        }
    }
}

@Composable
fun AdminScreen(t: T, user: PublicUser?, lang: AppLang) {
    if (user?.isAdmin != true) {
        Div({ classes("empty") }) { Text(t.signIn) }
        return
    }
    var tab by remember { mutableStateOf("pitches") }
    val scope = rememberCoroutineScope()
    var pitches by remember { mutableStateOf(listOf<PitchCardDto>()) }
    var verifications by remember { mutableStateOf(listOf<VerificationDto>()) }
    var users by remember { mutableStateOf(listOf<UserAdminDto>()) }
    var locations by remember { mutableStateOf(listOf<LocationDto>()) }
    var recs by remember { mutableStateOf(listOf<LocationRecDto>()) }
    var editing by remember { mutableStateOf<LocationDto?>(null) }
    var noShows by remember { mutableStateOf(listOf<NoShowDto>()) }
    fun reload() {
        scope.launch {
            runCatching { pitches = Api.adminPitches().items }
            runCatching { verifications = Api.adminVerifications() }
            runCatching { users = Api.adminUsers() }
            runCatching { locations = Api.adminLocations() }
            runCatching { recs = Api.locationRecs() }
            runCatching { noShows = Api.noShows() }
        }
    }
    LaunchedEffect(Unit) { reload() }
    Div {
        Div({ classes("topbar") }) {
            Button({ classes("icon-btn"); type(ButtonType.Button); onClick { go(Screen.Me) } }) { Text("←") }
            H1 { Text(t.admin) }
            Span {}
        }
        Div({ classes("admin-tabs") }) {
            listOf("pitches" to t.pitches, "verify" to t.verify, "noshow" to t.noShows, "users" to t.users, "places" to t.locations, "recs" to t.recs).forEach { (id, label) ->
                Button({
                    if (tab == id) classes("on")
                    type(ButtonType.Button)
                    onClick { tab = id }
                }) { Text(label) }
            }
        }
        when (tab) {
            "pitches" -> pitches.forEach { p ->
                Div({ classes("card") }) {
                    PhotoScroller(p.photoUrls)
                    Div({ classes("card-body") }) {
                        H3 { Text(p.author.name) }
                        P { Text(p.text) }
                        Div({ classes("grid-2") }) {
                            GradButton(t.approve) { scope.launch { Api.approvePitch(p.id); reload() } }
                            GhostButton(t.dismiss) { scope.launch { Api.dismissPitch(p.id); reload() } }
                        }
                    }
                }
            }
            "verify" -> verifications.forEach { v ->
                Div({ classes("inbox-card") }) {
                    H3 { Text(v.user.name) }
                    P { Text(v.zaloPhone) }
                    Div({ classes("grid-2") }) {
                        GradButton(t.approve) { scope.launch { Api.approveVerification(v.id); reload() } }
                        GhostButton(t.dismiss) { scope.launch { Api.dismissVerification(v.id); reload() } }
                    }
                }
            }
            "noshow" -> noShows.forEach { n ->
                Div({ classes("inbox-card") }) {
                    PersonDetails(n.reporter)
                    P({ classes("muted") }) { Text("${t.noShow} →") }
                    PersonDetails(n.accused)
                    if (n.locationName.isNotBlank()) P({ classes("muted") }) { Text("📍 ${n.locationName}") }
                    if (n.pitchPreview.isNotBlank()) P({ classes("preview") }) { Text("“${n.pitchPreview}”") }
                    if (n.message.isNotBlank()) P { Text(n.message) }
                    GradButton(t.review) { scope.launch { Api.reviewNoShow(n.id); reload() } }
                }
            }
            "users" -> users.forEach { u ->
                Div({ classes("inbox-card") }) {
                    H3 { Text(u.name + if (u.isAdmin) " · admin" else "") }
                    P({ classes("muted") }) { Text(u.phone) }
                    Div({ classes("grid-2") }) {
                        if (!u.isAdmin) GhostButton(t.promote) { scope.launch { Api.promote(u.id); reload() } }
                        if (u.id != user.id) GhostButton(if (u.disabled) t.enable else t.disable) { scope.launch { Api.toggleDisable(u.id); reload() } }
                    }
                }
            }
            "places" -> {
                LocationEditor(t, lang, null) { reload() }
                locations.forEach { loc ->
                    Div({ classes("card") }) {
                        PhotoScroller(loc.photoUrls)
                        Div({ classes("card-body") }) {
                            H3 { Text(loc.name) }
                            P { Text(loc.description) }
                            Div({ classes("grid-2") }) {
                                GhostButton(t.edit) { editing = loc }
                                GhostButton(t.dismiss) {
                                    showConfirm(t.dismiss, t.confirmDeleteLocation, t.dismiss) {
                                        scope.launch { runCatching { Api.deleteLocation(loc.id) }; reload() }
                                    }
                                }
                            }
                        }
                    }
                }
                editing?.let { loc ->
                    Div({ classes("modal-back"); onClick { editing = null } }) {
                        Div({ classes("modal", "modal-scroll"); onClick { it.stopPropagation() } }) {
                            LocationEditor(t, lang, loc) {
                                editing = null
                                reload()
                            }
                            GhostButton(t.close) { editing = null }
                        }
                    }
                }
            }
            "recs" -> recs.forEach { r ->
                Div({ classes("inbox-card") }) {
                    H3 { Text(r.name) }
                    P { Text(r.description) }
                    P({ classes("muted") }) { Text(r.user.name) }
                    GhostButton(t.dismiss) { scope.launch { Api.dismissRec(r.id); reload() } }
                }
            }
        }
    }
}

@Composable
private fun LocationEditor(t: T, lang: AppLang, existing: LocationDto?, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    var name by remember(existing?.id) { mutableStateOf(existing?.name.orEmpty()) }
    var description by remember(existing?.id) { mutableStateOf(existing?.description.orEmpty()) }
    var photos by remember(existing?.id) { mutableStateOf(existing?.photoUrls.orEmpty()) }
    var address by remember(existing?.id) { mutableStateOf(existing?.address.orEmpty()) }
    var directions by remember(existing?.id) { mutableStateOf(existing?.directionsUrl.orEmpty()) }
    var h1 by remember(existing?.id) { mutableStateOf(existing?.highlights?.getOrNull(0).orEmpty()) }
    var h2 by remember(existing?.id) { mutableStateOf(existing?.highlights?.getOrNull(1).orEmpty()) }
    var h3 by remember(existing?.id) { mutableStateOf(existing?.highlights?.getOrNull(2).orEmpty()) }
    fun clear() {
        name = ""
        description = ""
        photos = emptyList()
        address = ""
        directions = ""
        h1 = ""
        h2 = ""
        h3 = ""
    }
    Div({ classes("card") }) {
        Div({ classes("card-body") }) {
            H3 { Text(if (existing == null) t.addLocation else t.edit) }
            Div({ classes("field") }) { Label { Text(t.name) }; TextInput(name) { onInput { name = it.value } } }
            AiField(t, t.description, description, { description = it }, "location", t.description, null, lang)
            PhotoPicker(t, photos) { photos = it }
            Div({ classes("field") }) { Label { Text(t.address) }; TextInput(address) { onInput { address = it.value } } }
            Div({ classes("field") }) { Label { Text(t.directions) }; TextInput(directions) { onInput { directions = it.value } } }
            Div({ classes("field") }) { Label { Text(t.highlights) }; TextInput(h1) { onInput { h1 = it.value } } }
            Div({ classes("field") }) { TextInput(h2) { onInput { h2 = it.value } } }
            Div({ classes("field") }) { TextInput(h3) { onInput { h3 = it.value } } }
            GradButton(t.save) {
                scope.launch {
                    try {
                        val body = LocationWriteRequest(
                            name = name,
                            description = description,
                            photoUrls = photos,
                            openingHours = OpeningHours(),
                            address = address,
                            directionsUrl = directions,
                            highlights = listOf(h1, h2, h3),
                        )
                        if (existing == null) {
                            Api.createLocation(body)
                            clear()
                        } else {
                            Api.updateLocation(existing.id, body)
                        }
                        onDone()
                    } catch (e: Throwable) {
                        showNotice(e.message)
                    }
                }
            }
        }
    }
}

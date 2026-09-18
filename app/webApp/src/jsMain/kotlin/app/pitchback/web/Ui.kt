package app.pitchback.web

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import app.pitchback.api.AiStreamRequest
import app.pitchback.api.DayHours
import app.pitchback.api.LocationDto
import app.pitchback.api.OpeningHours
import app.pitchback.api.PitchCardDto
import kotlin.math.abs
import kotlin.math.max
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.jetbrains.compose.web.attributes.ButtonType
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.attributes.multiple
import org.jetbrains.compose.web.attributes.placeholder
import org.jetbrains.compose.web.attributes.type
import org.jetbrains.compose.web.attributes.value
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.H2
import org.jetbrains.compose.web.dom.H3
import org.jetbrains.compose.web.dom.Img
import org.jetbrains.compose.web.dom.Input
import org.jetbrains.compose.web.dom.Label
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text
import org.jetbrains.compose.web.dom.TextArea
import org.w3c.dom.CanvasRenderingContext2D
import org.w3c.dom.HTMLAnchorElement
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.HTMLImageElement
import org.w3c.dom.HTMLInputElement
import org.w3c.files.FileList
import org.w3c.files.get
import kotlin.coroutines.resume

@Composable
fun GradButton(label: String, flash: Boolean = false, onClick: () -> Unit) {
    Button({
        classes("btn", "btn-grad")
        if (flash) classes("flash")
        type(ButtonType.Button)
        onClick { onClick() }
    }) { Text(label) }
}

@Composable
fun GhostButton(label: String, onClick: () -> Unit) {
    Button({
        classes("btn", "btn-ghost")
        type(ButtonType.Button)
        onClick { onClick() }
    }) { Text(label) }
}

@Composable
fun PhotoScroller(urls: List<String>, tall: Boolean = false, onOpen: (() -> Unit)? = null) {
    if (urls.isEmpty()) return
    var index by remember { mutableStateOf(0) }
    var startX by remember { mutableStateOf(0.0) }
    var dragging by remember { mutableStateOf(false) }
    var drag by remember { mutableStateOf(0.0) }
    fun step(delta: Int) {
        index = (index + delta).coerceIn(0, urls.lastIndex)
        drag = 0.0
        dragging = false
    }
    fun finishDrag() {
        if (!dragging) return
        when {
            drag > 50 -> step(-1)
            drag < -50 -> step(1)
            abs(drag) < 12 -> onOpen?.invoke()
        }
        drag = 0.0
        dragging = false
    }
    Div({
        classes("photo-scroller")
        if (tall) classes("pitch-photo")
        if (onOpen != null) classes("clickable")
        attr("style", "height:${if (tall) "280px" else "210px"}")
        onMouseDown {
            dragging = true
            startX = it.clientX.toDouble()
        }
        onMouseMove {
            if (dragging) drag = it.clientX.toDouble() - startX
        }
        onMouseUp { finishDrag() }
        onMouseLeave {
            dragging = false
            drag = 0.0
        }
        onTouchStart {
            val t = it.touches.item(0) ?: return@onTouchStart
            dragging = true
            startX = t.clientX.toDouble()
        }
        onTouchMove {
            val t = it.touches.item(0) ?: return@onTouchMove
            if (dragging) drag = t.clientX.toDouble() - startX
        }
        onTouchEnd { finishDrag() }
        onClick {
            if (onOpen != null && abs(drag) < 12) {
                it.stopPropagation()
            }
        }
    }) {
        Div({
            classes("photo-track")
            if (dragging) classes("dragging")
            val pct = -(index * 100.0) + if (dragging) (drag / 4.0) else 0.0
            attr("style", "transform:translateX(${pct}%)")
        }) {
            urls.forEach { url ->
                Img(src = assetUrl(url), alt = "") {
                    attr("draggable", "false")
                }
            }
        }
        if (urls.size > 1) {
            Button({
                classes("scroller-btn", "prev")
                type(ButtonType.Button)
                onClick { it.stopPropagation(); step(-1) }
            }) { Text("‹") }
            Button({
                classes("scroller-btn", "next")
                type(ButtonType.Button)
                onClick { it.stopPropagation(); step(1) }
            }) { Text("›") }
            Div({ classes("dots") }) {
                urls.forEachIndexed { i, _ ->
                    Div({ classes("dot"); if (i == index) classes("on") })
                }
            }
        }
    }
}

@Composable
fun AiField(
    t: T,
    label: String,
    value: String,
    onValue: (String) -> Unit,
    kind: String,
    placeholderText: String,
    hint: String? = null,
    lang: AppLang,
    minChars: Int? = null,
) {
    var open by remember { mutableStateOf(false) }
    val left = minChars?.let { (it - value.trim().length).coerceAtLeast(0) }
    val shownHint = when {
        left != null && left > 0 -> t.needMoreChars(left)
        hint != null -> hint
        else -> null
    }
    Div({ classes("field") }) {
        if (label.isNotBlank()) Label { Text(label) }
        TextArea(value) {
            placeholder(placeholderText)
            onInput { onValue(it.value) }
        }
        Button({
            classes("ai-btn")
            type(ButtonType.Button)
            title(t.generateAi)
            onClick { open = true }
        }) { Text("✦") }
        if (shownHint != null) Div({ classes("hint") }) { Text(shownHint) }
    }
    if (open) {
        AiDialog(t, kind, value, lang, onInsert = {
            onValue(if (value.isBlank()) it else "$value\n$it")
            open = false
        }, onReplace = {
            onValue(it)
            open = false
        }, onClose = { open = false })
    }
}

@Composable
fun AiDialog(
    t: T,
    kind: String,
    existing: String,
    lang: AppLang,
    onInsert: (String) -> Unit,
    onReplace: (String) -> Unit,
    onClose: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val starters = remember(kind, lang) { AiStarters.forKind(kind, lang) }
    var extra by remember { mutableStateOf("") }
    var starter by remember { mutableStateOf("") }
    var stream by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    Div({ classes("modal-back"); onClick { onClose() } }) {
        Div({
            classes("modal")
            onClick { it.stopPropagation() }
        }) {
            org.jetbrains.compose.web.dom.H3 { Text(t.generateAi) }
            if (starters.isNotEmpty()) {
                Div({ classes("starters") }) {
                    starters.forEach { s ->
                        val on = starter == s
                        Button({
                            classes("starter")
                            if (on) classes("on")
                            type(ButtonType.Button)
                            onClick { starter = if (on) "" else s }
                        }) { Text(s) }
                    }
                }
            }
            Div({ classes("field") }) {
                TextArea(extra) {
                    placeholder(t.extraAi)
                    onInput { extra = it.value }
                }
            }
            Div({ classes("stream") }) {
                Text(if (busy && stream.isEmpty()) t.writing else stream.ifEmpty { "✦" })
            }
            if (error != null) Div({ classes("error") }) { Text(error!!) }
            Div({ classes("row-btns"); attr("style", "margin-top:10px") }) {
                GradButton(if (busy) t.writing else t.generate) {
                    if (busy) return@GradButton
                    busy = true
                    stream = ""
                    error = null
                    scope.launch {
                        try {
                            Api.streamAi(
                                AiStreamRequest(
                                    kind = kind,
                                    extra = extra,
                                    language = if (lang == AppLang.VI) "vi" else "en",
                                    existing = existing,
                                    starter = starter,
                                ),
                                onChunk = { stream += it },
                                onError = { error = it },
                            )
                        } catch (e: Throwable) {
                            error = e.message
                        }
                        busy = false
                    }
                }
                if (stream.isNotBlank()) {
                    GradButton(t.replace) { onReplace(stream.trim()) }
                    GhostButton(t.insert) { onInsert(stream.trim()) }
                }
                GhostButton(t.close, onClose)
            }
        }
    }
}

@Composable
fun PitchCardView(t: T, card: PitchCardDto, onOpen: () -> Unit, onPitchBack: () -> Unit) {
    Div({ classes("card") }) {
        PhotoScroller(card.photoUrls, onOpen = onOpen)
        Div({ classes("card-body") }) {
            Div({ classes("person") }) {
                org.jetbrains.compose.web.dom.H3 {
                    Text(card.author.name + " ")
                    if (card.author.verified) Span({ classes("verified") }) { Text("✔") }
                }
            }
            Div({ classes("meta") }) {
                Span({ classes("lefts") }) { Text("⚡ ${card.remainingPitchBacks} ${t.pitchesLeft}") }
                Text(" · ${card.location.name}")
            }
            org.jetbrains.compose.web.dom.P({
                classes("quote")
                onClick { onOpen() }
            }) {
                Text("“${card.text.take(140)}${if (card.text.length > 140) "…" else ""}”")
            }
            if (card.isOwner) {
                GhostButton(t.yourPitch, onOpen)
            } else {
                GradButton(t.pitchBack, onClick = onPitchBack)
            }
        }
    }
}

@Composable
fun PhotoPicker(
    t: T,
    urls: List<String>,
    label: String = t.photos,
    multiple: Boolean = true,
    onUrls: (List<String>) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var hover by remember { mutableStateOf(false) }
    fun ingest(files: FileList?) {
        if (files == null || files.length == 0) return
        scope.launch {
            val next = if (multiple) urls.toMutableList() else mutableListOf()
            val limit = if (multiple) files.length else minOf(files.length, 1)
            for (i in 0 until limit) {
                val f = files.get(i) ?: continue
                try {
                    next += Api.upload(f)
                } catch (e: Throwable) {
                    showNotice(e.message)
                }
            }
            onUrls(if (multiple) next else next.takeLast(1))
        }
    }
    Div({ classes("field") }) {
        Label { Text(label) }
        Label(attrs = {
            classes("drop-box")
            if (hover) classes("hover")
            onDragOver {
                it.preventDefault()
                hover = true
            }
            onDragLeave { hover = false }
            onDrop {
                it.preventDefault()
                hover = false
                ingest(it.nativeEvent.asDynamic().dataTransfer.files.unsafeCast<FileList?>())
            }
        }) {
            Input(InputType.File) {
                classes("sr-only")
                attr("accept", "image/*")
                if (multiple) multiple()
                onChange {
                    val el = it.nativeEvent.target as? HTMLInputElement ?: return@onChange
                    ingest(el.files)
                    el.value = ""
                }
            }
            Text(if (multiple) t.dropPhotos else t.dropPhoto)
        }
        if (urls.isNotEmpty()) {
            Spacer()
            Div({ classes("photo-thumbs") }) {
                urls.forEachIndexed { index, url ->
                    Div({
                        classes("photo-thumb")
                        attr("draggable", "true")
                        onDragStart {
                            it.asDynamic().dataTransfer.setData("text/plain", index.toString())
                        }
                        onDragOver { it.preventDefault() }
                        onDrop {
                            it.preventDefault()
                            val from = it.asDynamic().dataTransfer.getData("text/plain").toString().toIntOrNull()
                                ?: return@onDrop
                            if (from == index) return@onDrop
                            val next = urls.toMutableList()
                            val item = next.removeAt(from)
                            next.add(index, item)
                            onUrls(next)
                        }
                    }) {
                        Img(src = assetUrl(url), alt = "") { attr("draggable", "false") }
                        Button({
                            classes("photo-del")
                            type(ButtonType.Button)
                            onClick { ev ->
                                ev.stopPropagation()
                                onUrls(urls.filterIndexed { i, _ -> i != index })
                            }
                        }) { Text("✕") }
                    }
                }
            }
            if (multiple) {
                Spacer()
                PhotoScroller(urls)
            }
        }
    }
}

@Composable
fun Spacer() {
    Div({ classes("spacer") })
}

var appNotice by mutableStateOf<String?>(null)

fun isBenignCancel(message: String?): Boolean {
    val m = message.orEmpty().lowercase()
    return "left the composition" in m || "was cancelled" in m
}

fun showNotice(message: String?) {
    if (isBenignCancel(message)) return
    appNotice = message?.ifBlank { null } ?: "Something went wrong"
}

fun showCaught(e: Throwable) {
    if (isBenignCancel(e.message)) return
    showNotice(e.message)
}

data class ConfirmSpec(
    val title: String,
    val message: String,
    val confirmLabel: String,
    val onConfirm: () -> Unit,
)

var appConfirm by mutableStateOf<ConfirmSpec?>(null)

fun showConfirm(title: String, message: String, confirmLabel: String, onConfirm: () -> Unit) {
    appConfirm = ConfirmSpec(title, message, confirmLabel, onConfirm)
}

fun dismissConfirm() {
    appConfirm = null
}

@Composable
fun ConfirmDialog(t: T) {
    val spec = appConfirm ?: return
    Div({
        classes("modal-back")
        onClick { dismissConfirm() }
    }) {
        Div({
            classes("modal")
            onClick { it.stopPropagation() }
        }) {
            H3 { Text(spec.title) }
            P { Text(spec.message) }
            Div({ classes("grid-2") }) {
                GhostButton(t.cancel) { dismissConfirm() }
                GradButton(spec.confirmLabel) {
                    val action = spec.onConfirm
                    dismissConfirm()
                    action()
                }
            }
        }
    }
}

@Composable
fun ThemeSwitch(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Label(attrs = {
        classes("switch")
        onClick {
            it.preventDefault()
            onChecked(!checked)
        }
    }) {
        Span({
            classes("switch-ui")
            if (checked) classes("on")
        }) { Span({ classes("switch-knob") }) }
        Span { Text(label) }
    }
}

@Composable
fun LocationBlock(t: T, loc: LocationDto, header: String? = null, selectLabel: String? = null, onSelect: (() -> Unit)? = null) {
    if (header != null) Div({ classes("section-title") }) { Text(header) }
    Div({ classes("card") }) {
        PhotoScroller(loc.photoUrls)
        Div({ classes("card-body") }) {
            H3 { Text(loc.name) }
            if (loc.description.isNotBlank()) P { Text(loc.description) }
            if (loc.address.isNotBlank()) P({ classes("muted") }) { Text("📍 ${loc.address}") }
            loc.highlights.filter { it.isNotBlank() }.forEach { P({ classes("muted") }) { Text("✦ $it") } }
            HoursList(t, loc.openingHours)
            val mapUrl = loc.directionsUrl.ifBlank {
                val q = loc.address.ifBlank { loc.name }
                "https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(q)}"
            }
            if (mapUrl.isNotBlank()) GhostButton(t.viewOnMap) { window.open(mapUrl, "_blank") }
            if (onSelect != null && selectLabel != null) {
                Spacer()
                GradButton(selectLabel, onClick = onSelect)
            }
        }
    }
}

@Composable
private fun HoursList(t: T, hours: OpeningHours) {
    val rows = listOf(
        t.mon to hours.mon,
        t.tue to hours.tue,
        t.wed to hours.wed,
        t.thu to hours.thu,
        t.fri to hours.fri,
        t.sat to hours.sat,
        t.sun to hours.sun,
    )
    Div({ classes("hours") }) {
        rows.forEach { (day, h) ->
            P({ classes("muted") }) { Text("$day · ${formatHours(t, h)}") }
        }
    }
}

private fun formatHours(t: T, h: DayHours): String =
    if (h.closed) t.closed else listOfNotNull(h.open, h.close).joinToString("–").ifBlank { t.open }

fun encodeURIComponent(value: String): String = js("encodeURIComponent")(value) as String

@Composable
fun LocationPickerDialog(
    t: T,
    locations: List<LocationDto>,
    onPick: (LocationDto) -> Unit,
    onClose: () -> Unit,
) {
    var q by remember { mutableStateOf("") }
    val filtered = remember(q, locations) {
        val needle = q.trim().lowercase()
        if (needle.isEmpty()) locations
        else locations.filter {
            (it.name + " " + it.address + " " + it.description + " " + it.highlights.joinToString(" ")).lowercase()
                .contains(needle)
        }
    }
    Div({ classes("modal-back"); onClick { onClose() } }) {
        Div({
            classes("modal", "modal-scroll")
            onClick { it.stopPropagation() }
        }) {
            H3 { Text(t.pickLocation) }
            Div({ classes("field") }) {
                Input(InputType.Text) {
                    placeholder(t.searchPlaces)
                    value(q)
                    onInput { q = it.value }
                }
            }
            if (filtered.isEmpty()) P({ classes("muted") }) { Text(t.emptyFeed) }
            filtered.forEach { loc ->
                LocationBlock(t, loc, selectLabel = t.chooseThisPlace, onSelect = { onPick(loc) })
            }
            GhostButton(t.close, onClose)
        }
    }
}

@Composable
fun WeMetSticker(t: T, meUrl: String?, otherUrl: String?, onClose: () -> Unit) {
    var dataUrl by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(meUrl, otherUrl, t.weMet) {
        dataUrl = renderWeMetPng(assetUrl(meUrl.orEmpty()), assetUrl(otherUrl.orEmpty()), t.weMet)
    }
    Div({ classes("sticker-wrap"); onClick { onClose() } }) {
        Div({
            classes("sticker")
            onClick { it.stopPropagation() }
        }) {
            if (dataUrl != null) {
                Img(src = dataUrl!!, alt = t.weMet) { classes("sticker-img") }
            } else {
                Div({ classes("empty") }) { Text(t.loading) }
            }
            H2 { Text(t.weMet) }
            if (dataUrl != null) GradButton(t.download) { downloadDataUrl(dataUrl!!, "we-met.png") }
            GhostButton(t.close, onClose)
        }
    }
}

private suspend fun renderWeMetPng(leftUrl: String, rightUrl: String, caption: String): String {
    val canvas = document.createElement("canvas") as HTMLCanvasElement
    canvas.width = 720
    canvas.height = 720
    val ctx = canvas.getContext("2d") as CanvasRenderingContext2D
    val grad = ctx.createLinearGradient(0.0, 0.0, 720.0, 720.0)
    grad.addColorStop(0.0, "#24102c")
    grad.addColorStop(1.0, "#ff4d94")
    ctx.fillStyle = grad
    ctx.fillRect(0.0, 0.0, 720.0, 720.0)
    val left = loadCanvasImage(leftUrl)
    val right = loadCanvasImage(rightUrl)
    ctx.save()
    heartPath(ctx, 360.0, 310.0, 250.0)
    ctx.clip()
    drawCover(ctx, left, 70.0, 70.0, 290.0, 430.0)
    drawCover(ctx, right, 360.0, 70.0, 290.0, 430.0)
    ctx.restore()
    ctx.strokeStyle = "#d4ff3f"
    ctx.lineWidth = 10.0
    heartPath(ctx, 360.0, 310.0, 250.0)
    ctx.stroke()
    ctx.fillStyle = "#ffffff"
    ctx.font = "800 54px Be Vietnam Pro, sans-serif"
    ctx.asDynamic().textAlign = "center"
    ctx.fillText(caption, 360.0, 620.0)
    ctx.fillStyle = "#d4ff3f"
    ctx.font = "700 22px Be Vietnam Pro, sans-serif"
    ctx.fillText("Saigon.Social", 360.0, 662.0)
    return canvas.toDataURL("image/png")
}

private fun heartPath(ctx: CanvasRenderingContext2D, cx: Double, cy: Double, size: Double) {
    ctx.beginPath()
    ctx.moveTo(cx, cy + size * 0.32)
    ctx.bezierCurveTo(cx - size, cy + size * 0.08, cx - size, cy - size * 0.42, cx, cy - size * 0.08)
    ctx.bezierCurveTo(cx + size, cy - size * 0.42, cx + size, cy + size * 0.08, cx, cy + size * 0.32)
    ctx.closePath()
}

private fun drawCover(ctx: CanvasRenderingContext2D, img: HTMLImageElement?, x: Double, y: Double, w: Double, h: Double) {
    if (img == null || img.naturalWidth == 0 || img.naturalHeight == 0) {
        ctx.fillStyle = "#3a243f"
        ctx.fillRect(x, y, w, h)
        return
    }
    val iw = img.naturalWidth.toDouble()
    val ih = img.naturalHeight.toDouble()
    val scale = max(w / iw, h / ih)
    val dw = iw * scale
    val dh = ih * scale
    ctx.save()
    ctx.beginPath()
    ctx.rect(x, y, w, h)
    ctx.clip()
    ctx.drawImage(img, x + (w - dw) / 2, y + (h - dh) / 2, dw, dh)
    ctx.restore()
}

private suspend fun loadCanvasImage(url: String): HTMLImageElement? {
    if (url.isBlank()) return null
    return try {
        val init = js("{}")
        init.mode = "cors"
        init.credentials = "include"
        val res = window.fetch(url, init).await()
        if (!res.ok) return null
        val blob = res.blob().await()
        val obj = kotlinx.browser.window.asDynamic().URL.createObjectURL(blob) as String
        suspendCancellableCoroutine { cont ->
            val img = document.createElement("img") as HTMLImageElement
            img.addEventListener("load", {
                kotlinx.browser.window.asDynamic().URL.revokeObjectURL(obj)
                cont.resume(img)
            })
            img.addEventListener("error", {
                kotlinx.browser.window.asDynamic().URL.revokeObjectURL(obj)
                cont.resume(null)
            })
            img.src = obj
        }
    } catch (_: Throwable) {
        null
    }
}

private fun downloadDataUrl(dataUrl: String, name: String) {
    val a = document.createElement("a") as HTMLAnchorElement
    a.href = dataUrl
    a.download = name
    a.click()
}

fun dismissNotice() {
    appNotice = null
}

@Composable
fun NoticeDialog(t: T) {
    val msg = appNotice ?: return
    Div({
        classes("modal-back")
        onClick { dismissNotice() }
    }) {
        Div({
            classes("modal")
            onClick { it.stopPropagation() }
        }) {
            P { Text(msg) }
            GradButton(t.close) { dismissNotice() }
        }
    }
}

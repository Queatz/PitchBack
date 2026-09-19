package app.pitchback.web

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import app.pitchback.api.AiStreamRequest
import app.pitchback.api.LocationDto
import app.pitchback.api.OpeningHours
import app.pitchback.api.PitchCardDto
import app.pitchback.api.PublicUser
import app.pitchback.api.WeeklyAvailability
import app.pitchback.domain.DEFAULT_MEET_TIME
import app.pitchback.domain.OfferedSlot
import app.pitchback.domain.OpenStatus
import app.pitchback.domain.availabilityFromMonFirst
import app.pitchback.domain.condensedHours
import app.pitchback.domain.formatMinutes
import app.pitchback.domain.monFirstDays
import app.pitchback.domain.openStatus
import app.pitchback.domain.parseMinutes
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.js.Date
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
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLImageElement
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.events.Event
import org.w3c.dom.events.MouseEvent
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
fun PhotoScroller(urls: List<String>, onOpen: (() -> Unit)? = null) {
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
        if (onOpen != null) classes("clickable")
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
                onClick { it.stopPropagation(); it.preventDefault(); step(-1) }
            }) { Text("‹") }
            Button({
                classes("scroller-btn", "next")
                type(ButtonType.Button)
                onClick { it.stopPropagation(); it.preventDefault(); step(1) }
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
                Span({ classes("lefts") }) { Text("⚡ ${t.pitchesLeft(card.remainingPitchBacks)}") }
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
            PhotoThumbs(urls, onUrls)
            if (multiple) {
                Spacer()
                PhotoScroller(urls)
            }
        }
    }
}

@Composable
private fun PhotoThumbs(urls: List<String>, onUrls: (List<String>) -> Unit) {
    var drag by remember { mutableStateOf<PhotoThumbDrag?>(null) }
    val session = remember { PhotoThumbSession() }
    session.urls = urls
    session.onUrls = onUrls
    session.setDrag = { drag = it }

    DisposableEffect(Unit) {
        onDispose { session.cancel() }
    }
    DisposableEffect(drag?.over) {
        val id = window.requestAnimationFrame {
            val el = session.container ?: return@requestAnimationFrame
            session.rects = animateThumbRects(el, session.rects)
        }
        onDispose { window.cancelAnimationFrame(id) }
    }

    val display = if (drag == null) urls else movedPhotoUrls(urls, drag!!.from, drag!!.over)
    Div({
        classes("photo-thumbs")
        if (drag != null) classes("dragging")
        ref { el ->
            session.container = el
            onDispose { if (session.container === el) session.container = null }
        }
    }) {
        display.forEachIndexed { index, url ->
            val isSlot = drag != null && index == drag!!.over
            key(url) {
                Div({
                    classes("photo-thumb")
                    if (isSlot) classes("slot")
                    attr("data-thumb", url)
                    attr("draggable", "false")
                    onDragStart { it.preventDefault() }
                    onMouseDown { ev ->
                        if (ev.button.toInt() != 0) return@onMouseDown
                        val target = (ev.nativeEvent.currentTarget as? HTMLElement) ?: return@onMouseDown
                        session.press(indexForUrl(urls, url), ev.clientX.toDouble(), ev.clientY.toDouble(), target)
                    }
                    onTouchStart { ev ->
                        val t = ev.touches.item(0) ?: return@onTouchStart
                        val target = (ev.nativeEvent.currentTarget as? HTMLElement) ?: return@onTouchStart
                        session.press(indexForUrl(urls, url), t.clientX.toDouble(), t.clientY.toDouble(), target)
                        ev.preventDefault()
                    }
                }) {
                    Img(src = assetUrl(url), alt = "") { attr("draggable", "false") }
                    Button({
                        classes("photo-del")
                        type(ButtonType.Button)
                        onMouseDown { it.stopPropagation() }
                        onTouchStart { it.stopPropagation() }
                        onClick { ev ->
                            ev.stopPropagation()
                            val original = indexForUrl(urls, url)
                            if (original >= 0) onUrls(urls.filterIndexed { i, _ -> i != original })
                        }
                    }) { Text("✕") }
                }
            }
        }
    }
    val active = drag
    if (active != null && active.from in urls.indices) {
        Div({
            classes("photo-thumb", "ghost")
            attr(
                "style",
                "left:${active.pointerX - active.grabX}px;top:${active.pointerY - active.grabY}px;" +
                    "width:${active.width}px;height:${active.height}px",
            )
            ref { el ->
                session.ghost = el
                onDispose { if (session.ghost === el) session.ghost = null }
            }
        }) {
            Img(src = assetUrl(urls[active.from]), alt = "") { attr("draggable", "false") }
        }
    }
}

private data class PhotoThumbDrag(
    val from: Int,
    val over: Int,
    val pointerX: Double,
    val pointerY: Double,
    val grabX: Double,
    val grabY: Double,
    val width: Double,
    val height: Double,
)

private class PhotoThumbSession {
    var urls: List<String> = emptyList()
    var onUrls: (List<String>) -> Unit = {}
    var container: HTMLElement? = null
    var ghost: HTMLElement? = null
    var drag: PhotoThumbDrag? = null
    var setDrag: (PhotoThumbDrag?) -> Unit = {}
    var rects: Map<String, Pair<Double, Double>> = emptyMap()
    private var pressFrom: Int = -1
    private var pressX: Double = 0.0
    private var pressY: Double = 0.0
    private var grabX: Double = 0.0
    private var grabY: Double = 0.0
    private var width: Double = 0.0
    private var height: Double = 0.0
    private var started: Boolean = false
    private var listening: Boolean = false
    private val onMove: (Event) -> Unit = { move(it) }
    private val onUp: (Event) -> Unit = { finish() }

    fun press(index: Int, x: Double, y: Double, target: HTMLElement) {
        if (started || index !in urls.indices) return
        val r = target.getBoundingClientRect()
        pressFrom = index
        pressX = x
        pressY = y
        grabX = x - r.left
        grabY = y - r.top
        width = r.width
        height = r.height
        started = false
        drag = null
        listen()
    }

    fun cancel() {
        unlisten()
        started = false
        drag = null
        clearThumbMotion(container)
        setDrag(null)
    }

    private fun listen() {
        if (listening) return
        listening = true
        window.addEventListener("mousemove", onMove)
        window.addEventListener("mouseup", onUp)
        window.asDynamic().addEventListener("touchmove", onMove, js("{ passive: false }"))
        window.addEventListener("touchend", onUp)
        window.addEventListener("touchcancel", onUp)
    }

    private fun unlisten() {
        if (!listening) return
        listening = false
        window.removeEventListener("mousemove", onMove)
        window.removeEventListener("mouseup", onUp)
        window.removeEventListener("touchmove", onMove)
        window.removeEventListener("touchend", onUp)
        window.removeEventListener("touchcancel", onUp)
    }

    private fun move(event: Event) {
        val point = pointerPoint(event) ?: return
        val x = point.first
        val y = point.second
        var current = drag
        if (current == null) {
            if (abs(x - pressX) < 8 && abs(y - pressY) < 8) return
            event.preventDefault()
            rects = captureThumbRects(container)
            current = PhotoThumbDrag(pressFrom, pressFrom, x, y, grabX, grabY, width, height)
            started = true
            drag = current
            setDrag(current)
            return
        }
        event.preventDefault()
        val over = container?.let { pointerThumbIndex(it, x, y, current.over) } ?: current.over
        val prevOver = current.over
        if (over != prevOver) rects = captureThumbRects(container)
        current = current.copy(over = over, pointerX = x, pointerY = y)
        drag = current
        ghost?.style?.setProperty("left", "${x - current.grabX}px")
        ghost?.style?.setProperty("top", "${y - current.grabY}px")
        if (over != prevOver) setDrag(current)
    }

    private fun finish() {
        unlisten()
        val current = drag
        val didDrag = started
        started = false
        drag = null
        clearThumbMotion(container)
        setDrag(null)
        if (!didDrag || current == null) return
        if (current.from != current.over) onUrls(movedPhotoUrls(urls, current.from, current.over))
    }
}

private fun indexForUrl(urls: List<String>, url: String): Int = urls.indexOf(url)

internal fun movedPhotoUrls(urls: List<String>, from: Int, to: Int): List<String> {
    if (from == to || from !in urls.indices || to !in urls.indices) return urls
    val next = urls.toMutableList()
    val item = next.removeAt(from)
    next.add(to, item)
    return next
}

private fun pointerPoint(event: Event): Pair<Double, Double>? {
    if (event.type.startsWith("touch")) {
        val touch = event.asDynamic().touches.item(0) ?: event.asDynamic().changedTouches.item(0) ?: return null
        return (touch.clientX as Double) to (touch.clientY as Double)
    }
    val mouse = event as? MouseEvent ?: return null
    return mouse.clientX.toDouble() to mouse.clientY.toDouble()
}

internal data class PhotoThumbBox(
    val left: Double,
    val top: Double,
    val width: Double,
    val height: Double,
) {
    val centerX get() = left + width / 2
    val centerY get() = top + height / 2
    fun contains(x: Double, y: Double, pad: Double = 0.0): Boolean =
        x >= left - pad && x <= left + width + pad &&
            y >= top - pad && y <= top + height + pad
}

internal fun dropThumbIndex(
    boxes: List<PhotoThumbBox>,
    x: Double,
    y: Double,
    current: Int,
    stickPx: Double = 24.0,
): Int {
    if (boxes.isEmpty()) return 0
    if (current in boxes.indices && boxes[current].contains(x, y, stickPx)) return current
    var best = 0
    var bestDist = Double.POSITIVE_INFINITY
    for (i in boxes.indices) {
        val b = boxes[i]
        val dx = x - b.centerX
        val dy = y - b.centerY
        val dist = dx * dx + dy * dy
        if (dist < bestDist) {
            bestDist = dist
            best = i
        }
    }
    return best
}

private fun pointerThumbIndex(container: HTMLElement, x: Double, y: Double, current: Int): Int {
    val nodes = container.querySelectorAll(".photo-thumb")
    val boxes = ArrayList<PhotoThumbBox>(nodes.length)
    for (i in 0 until nodes.length) {
        val el = nodes.item(i) as? HTMLElement ?: continue
        boxes += layoutBox(el)
    }
    return dropThumbIndex(boxes, x, y, current)
}

private fun captureThumbRects(container: HTMLElement?): Map<String, Pair<Double, Double>> {
    if (container == null) return emptyMap()
    val nodes = container.querySelectorAll(".photo-thumb")
    val next = mutableMapOf<String, Pair<Double, Double>>()
    for (i in 0 until nodes.length) {
        val el = nodes.item(i) as? HTMLElement ?: continue
        val key = el.getAttribute("data-thumb") ?: continue
        next[key] = layoutPos(el)
    }
    return next
}

private fun animateThumbRects(
    container: HTMLElement,
    previous: Map<String, Pair<Double, Double>>,
): Map<String, Pair<Double, Double>> {
    val nodes = container.querySelectorAll(".photo-thumb")
    val next = mutableMapOf<String, Pair<Double, Double>>()
    for (i in 0 until nodes.length) {
        val el = nodes.item(i) as? HTMLElement ?: continue
        val key = el.getAttribute("data-thumb") ?: continue
        val pos = layoutPos(el)
        next[key] = pos
        val last = previous[key] ?: continue
        val dx = last.first - pos.first
        val dy = last.second - pos.second
        if (abs(dx) < 1 && abs(dy) < 1) {
            el.style.removeProperty("transition")
            el.style.removeProperty("transform")
            continue
        }
        el.style.setProperty("transition", "none")
        el.style.setProperty("transform", "translate(${dx}px, ${dy}px)")
        el.offsetWidth
        el.style.setProperty("transition", "transform 200ms ease")
        el.style.setProperty("transform", "translate(0, 0)")
    }
    return next
}

private fun clearThumbMotion(container: HTMLElement?) {
    if (container == null) return
    val nodes = container.querySelectorAll(".photo-thumb")
    for (i in 0 until nodes.length) {
        val el = nodes.item(i) as? HTMLElement ?: continue
        el.style.removeProperty("transition")
        el.style.removeProperty("transform")
    }
}

private fun layoutPos(el: HTMLElement): Pair<Double, Double> {
    val box = layoutBox(el)
    return box.left to box.top
}

private fun layoutBox(el: HTMLElement): PhotoThumbBox {
    val r = el.getBoundingClientRect()
    val (tx, ty) = computedTranslation(el)
    return PhotoThumbBox(r.left - tx, r.top - ty, r.width, r.height)
}

private fun computedTranslation(el: HTMLElement): Pair<Double, Double> {
    val transform = window.getComputedStyle(el).transform
    if (transform.isEmpty() || transform == "none") return 0.0 to 0.0
    return try {
        val matrix = js("new DOMMatrixReadOnly(transform)")
        (matrix.m41 as Double) to (matrix.m42 as Double)
    } catch (_: Throwable) {
        0.0 to 0.0
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
    if (header != null) Div({ classes("quote-label") }) { Text(header) }
    Div({ classes("card") }) {
        PhotoScroller(loc.photoUrls)
        Div({ classes("card-body") }) {
            H3 { Text(loc.name) }
            HoursList(t, loc.openingHours)
            if (loc.description.isNotBlank()) P { Text(loc.description) }
            if (loc.address.isNotBlank()) P({ classes("muted") }) { Text("📍 ${loc.address}") }
            loc.highlights.filter { it.isNotBlank() }.forEachIndexed { i, h ->
                P({ classes("highlight", "c${i % 3}") }) { Text("✦ $h") }
            }
            val mapUrl = loc.directionsUrl.ifBlank {
                val q = loc.address.ifBlank { loc.name }
                "https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(q)}"
            }
            Spacer()
            if (mapUrl.isNotBlank()) GhostButton(t.viewOnMap) { window.open(mapUrl, "_blank") }
            if (onSelect != null && selectLabel != null) {
                Spacer()
                GradButton(selectLabel, onClick = onSelect)
            }
        }
    }
}

@Composable
fun PersonDetails(user: PublicUser, onOpen: (() -> Unit)? = null) {
    Div({ classes("person-card") }) {
        if (!user.photoUrl.isNullOrBlank()) {
            Img(src = assetUrl(user.photoUrl!!), alt = user.name) {
                classes("person-photo")
                if (onOpen != null) {
                    classes("person-open")
                    onClick { onOpen() }
                }
            }
        } else {
            Div({
                classes("person-photo", "placeholder")
                if (onOpen != null) {
                    classes("person-open")
                    onClick { onOpen() }
                }
            }) { Text(user.name.take(1).uppercase()) }
        }
        Div {
            H3({
                if (onOpen != null) {
                    classes("person-open")
                    onClick { onOpen() }
                }
            }) {
                Text(user.name + " ")
                if (user.verified) Span({ classes("verified") }) { Text("✔") }
            }
            if (user.intro.isNotBlank()) P({ classes("intro") }) { Text(user.intro) }
        }
    }
}

@Composable
fun ProfileDialog(t: T, user: PublicUser, onClose: () -> Unit) {
    Div({
        classes("modal-back")
        onClick { onClose() }
    }) {
        Div({
            classes("modal", "profile-sheet")
            onClick { it.stopPropagation() }
        }) {
            if (!user.photoUrl.isNullOrBlank()) {
                Img(src = assetUrl(user.photoUrl!!), alt = user.name) { classes("profile-sheet-photo") }
            } else {
                Div({ classes("profile-sheet-photo", "placeholder") }) { Text(user.name.take(1).uppercase()) }
            }
            H2 {
                Text(user.name + " ")
                if (user.verified) Span({ classes("verified") }) { Text("✔ ${t.verifiedBadge}") }
            }
            if (user.intro.isNotBlank()) P({ classes("intro") }) { Text(user.intro) }
            GradButton(t.close) { onClose() }
        }
    }
}

@Composable
private fun HoursList(t: T, hours: OpeningHours) {
    val now = remember {
        val d = Date()
        d.getDay() to (d.getHours() * 60 + d.getMinutes())
    }
    val status = remember(hours, now) { openStatus(hours, now.first, now.second) }
    val groups = remember(hours) { condensedHours(hours) }
    val monFirst = listOf(t.mon, t.tue, t.wed, t.thu, t.fri, t.sat, t.sun)
    val jsDays = listOf(t.sun, t.mon, t.tue, t.wed, t.thu, t.fri, t.sat)
    Div({ classes("hours") }) {
        Div({
            classes("hours-now")
            if (status.open) classes("open") else classes("closed")
        }) {
            Text(hoursNowLine(t, status, now.first, jsDays))
        }
        groups.forEach { g ->
            val days = if (g.startDay == g.endDay) monFirst[g.startDay]
            else "${monFirst[g.startDay]}–${monFirst[g.endDay]}"
            val time = when {
                g.closed -> t.closed
                else -> listOfNotNull(g.open, g.close).joinToString("–").ifBlank { t.open }
            }
            P({ classes("hours-line") }) { Text("$days  $time") }
        }
    }
}

private fun hoursNowLine(t: T, status: OpenStatus, today: Int, jsDays: List<String>): String {
    val time = status.nextMinutes?.let { formatMinutes(it) }
    return when {
        status.open && time != null -> "${t.openNow} · ${t.closesAt(time)}"
        status.open -> t.openNow
        time != null && status.nextDay != null && status.nextDay == today ->
            "${t.closedNow} · ${t.opensAt(time)}"
        time != null && status.nextDay != null ->
            "${t.closedNow} · ${t.opensAt("${jsDays[status.nextDay!!]} $time")}"
        else -> t.closedNow
    }
}

fun encodeURIComponent(value: String): String = js("encodeURIComponent")(value) as String

fun formatMeetSlot(t: T, at: Long): String {
    val d = Date(at.toDouble())
    val days = listOf(t.sun, t.mon, t.tue, t.wed, t.thu, t.fri, t.sat)
    val month = t.months.getOrElse(d.getMonth()) { "" }
    val hh = d.getHours().toString().padStart(2, '0')
    val mm = d.getMinutes().toString().padStart(2, '0')
    return "${days[d.getDay()]} ${d.getDate()} $month · $hh:$mm"
}

fun epochToDateTimeLocal(ms: Long): String {
    val d = Date(ms.toDouble())
    fun p(n: Int) = n.toString().padStart(2, '0')
    return "${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}T${p(d.getHours())}:${p(d.getMinutes())}"
}

@Composable
fun AvailabilityEditor(
    t: T,
    value: WeeklyAvailability,
    onValue: (WeeklyAvailability) -> Unit,
) {
    val days = value.monFirstDays()
    val labels = listOf(t.mon, t.tue, t.wed, t.thu, t.fri, t.sat, t.sun)
    var drafts by remember { mutableStateOf(List(7) { "" }) }
    fun setDay(index: Int, times: List<String>) {
        val next = days.toMutableList()
        next[index] = times
        onValue(availabilityFromMonFirst(next))
    }
    Div({ classes("field") }) {
        Label { Text(t.meetTimes) }
        Div({ classes("day-toggles") }) {
            labels.forEachIndexed { i, label ->
                val on = days[i].isNotEmpty()
                Button({
                    classes("day-toggle")
                    if (on) classes("on")
                    type(ButtonType.Button)
                    onClick {
                        setDay(i, if (on) emptyList() else listOf(DEFAULT_MEET_TIME))
                    }
                }) { Text(label) }
            }
        }
        days.forEachIndexed { i, times ->
            if (times.isEmpty()) return@forEachIndexed
            Div({ classes("avail-day") }) {
                Div({ classes("avail-day-name") }) { Text(labels[i]) }
                Div({ classes("time-chips") }) {
                    times.forEach { time ->
                        Span({ classes("time-chip") }) {
                            Text(time)
                            Button({
                                type(ButtonType.Button)
                                onClick { setDay(i, times.filter { it != time }) }
                            }) { Text("✕") }
                        }
                    }
                }
                Div({ classes("time-add") }) {
                    Input(InputType.Time) {
                        value(drafts[i])
                        onInput { ev ->
                            val next = drafts.toMutableList()
                            next[i] = ev.value
                            drafts = next
                        }
                    }
                    Button({
                        type(ButtonType.Button)
                        onClick {
                            val mins = parseMinutes(drafts[i]) ?: return@onClick
                            val label = formatMinutes(mins)
                            if (label !in times) setDay(i, (times + label).distinct().sorted())
                            val next = drafts.toMutableList()
                            next[i] = ""
                            drafts = next
                        }
                    }) { Text(t.addTime) }
                }
            }
        }
        Div({ classes("hint") }) { Text(t.meetTimesHint) }
    }
}

@Composable
fun SlotPicker(
    t: T,
    slots: List<OfferedSlot>,
    selectedAt: Long?,
    onSelect: (Long) -> Unit,
) {
    Div({ classes("quote-label") }) { Text(t.nextTimes) }
    if (slots.isEmpty()) {
        P({ classes("muted") }) { Text(t.noTimes) }
        return
    }
    Div({ classes("slot-grid") }) {
        slots.forEach { slot ->
            Button({
                classes("slot-option")
                if (selectedAt == slot.at) classes("on")
                type(ButtonType.Button)
                onClick { onSelect(slot.at) }
            }) { Text(formatMeetSlot(t, slot.at)) }
        }
    }
}

@Composable
fun MeetSlotLine(t: T, slotAt: Long?) {
    if (slotAt == null) return
    P({ classes("muted") }) { Text("🕒 ${t.meetOn} ${formatMeetSlot(t, slotAt)}") }
}

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
            if (dataUrl != null) {
                Div({ classes("sticker-download") }) {
                    GradButton(t.download) { downloadDataUrl(dataUrl!!, "we-met.png") }
                }
            }
            GhostButton(t.close, onClose)
        }
    }
}

private suspend fun renderWeMetPng(leftUrl: String, rightUrl: String, caption: String): String {
    val canvas = document.createElement("canvas") as HTMLCanvasElement
    canvas.width = 720
    canvas.height = 720
    val ctx = canvas.getContext("2d") as CanvasRenderingContext2D
    ctx.clearRect(0.0, 0.0, 720.0, 720.0)
    ctx.save()
    ctx.beginPath()
    ctx.arc(360.0, 360.0, 360.0, 0.0, PI * 2)
    ctx.closePath()
    ctx.clip()
    val grad = ctx.createLinearGradient(0.0, 0.0, 720.0, 720.0)
    grad.addColorStop(0.0, "#24102c")
    grad.addColorStop(1.0, "#ff4d94")
    ctx.fillStyle = grad
    ctx.fillRect(0.0, 0.0, 720.0, 720.0)
    val left = loadCanvasImage(leftUrl)
    val right = loadCanvasImage(rightUrl)
    ctx.save()
    heartPath(ctx, 360.0, 300.0, 250.0)
    ctx.clip()
    drawCover(ctx, left, 110.0, 84.0, 250.0, 430.0)
    drawCover(ctx, right, 360.0, 84.0, 250.0, 430.0)
    ctx.restore()
    ctx.strokeStyle = "#d4ff3f"
    ctx.lineWidth = 10.0
    heartPath(ctx, 360.0, 300.0, 250.0)
    ctx.stroke()
    ctx.fillStyle = "#ffffff"
    ctx.font = "800 48px Be Vietnam Pro, sans-serif"
    ctx.asDynamic().textAlign = "center"
    ctx.fillText(caption, 360.0, 600.0)
    ctx.fillStyle = "#d4ff3f"
    ctx.font = "700 22px Be Vietnam Pro, sans-serif"
    ctx.fillText("ranh.app", 360.0, 640.0)
    ctx.restore()
    return canvas.toDataURL("image/png")
}

private fun heartPath(ctx: CanvasRenderingContext2D, cx: Double, cy: Double, size: Double) {
    val s = size / 55.0
    fun x(v: Double) = cx + (v - 75.0) * s
    fun y(v: Double) = cy + (v - 72.5) * s
    ctx.beginPath()
    ctx.moveTo(x(75.0), y(40.0))
    ctx.bezierCurveTo(x(75.0), y(37.0), x(70.0), y(25.0), x(50.0), y(25.0))
    ctx.bezierCurveTo(x(20.0), y(25.0), x(20.0), y(62.5), x(20.0), y(62.5))
    ctx.bezierCurveTo(x(20.0), y(80.0), x(40.0), y(102.0), x(75.0), y(120.0))
    ctx.bezierCurveTo(x(110.0), y(102.0), x(130.0), y(80.0), x(130.0), y(62.5))
    ctx.bezierCurveTo(x(130.0), y(62.5), x(130.0), y(25.0), x(100.0), y(25.0))
    ctx.bezierCurveTo(x(85.0), y(25.0), x(75.0), y(37.0), x(75.0), y(40.0))
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

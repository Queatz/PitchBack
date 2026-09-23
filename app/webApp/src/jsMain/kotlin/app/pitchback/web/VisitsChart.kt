package app.pitchback.web

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.pitchback.api.VisitDayDto
import app.pitchback.domain.nearestVisitIndex
import app.pitchback.domain.visitChartXPositions
import app.pitchback.domain.visitChartY
import kotlin.js.Date
import kotlin.math.roundToInt
import kotlinx.browser.document
import org.jetbrains.compose.web.attributes.ButtonType
import org.jetbrains.compose.web.attributes.type
import org.jetbrains.compose.web.dom.AttrBuilderContext
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.ContentBuilder
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.ElementBuilder
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.TagElement
import org.jetbrains.compose.web.dom.Text
import org.w3c.dom.HTMLElement
import org.w3c.dom.svg.SVGElement

private const val PAD_L = 10.0
private const val PAD_R = 10.0
private const val PAD_T = 18.0
private const val PAD_B = 18.0

@Composable
fun VisitsChart(t: T, days: List<VisitDayDto>) {
    if (days.isEmpty()) return
    var hover by remember { mutableStateOf<Int?>(null) }
    var showUsers by remember { mutableStateOf(true) }
    var showGuests by remember { mutableStateOf(true) }
    var plotW by remember { mutableStateOf(0.0) }
    var plotH by remember { mutableStateOf(0.0) }
    val max = days.maxOf { maxOf(it.users, it.guests) }.coerceAtLeast(1)
    val xs = visitChartXPositions(days.size, plotW, PAD_L, PAD_R)
    val hovered = hover?.let { days.getOrNull(it) }

    Div({ classes("visits-card") }) {
        Div({ classes("visits-head") }) {
            P({ classes("stat-label") }) { Text(t.visitors) }
            P({ classes("stat-sub") }) { Text(t.last7Days) }
        }
        Div({ classes("visits-legend") }) {
            LegendItem(t.users, "lime", showUsers) { showUsers = !showUsers }
            LegendItem(t.nonUsers, "pink", showGuests) { showGuests = !showGuests }
        }
        Div({
            classes("visits-plot")
            ref { el ->
                val stop = observeElementSize(el) { w, h ->
                    plotW = w
                    plotH = h
                }
                onDispose { stop() }
            }
            onMouseMove { ev -> hoverFromEvent(ev.currentTarget as HTMLElement, ev.nativeEvent, days.size)?.let { hover = it } }
            onMouseLeave { hover = null }
            onTouchStart { ev -> hoverFromEvent(ev.currentTarget as HTMLElement, ev.nativeEvent, days.size)?.let { hover = it } }
            onTouchMove { ev -> hoverFromEvent(ev.currentTarget as HTMLElement, ev.nativeEvent, days.size)?.let { hover = it } }
        }) {
            if (plotW > 0 && plotH > 0) SvgRoot({
                attr("viewBox", "0 0 ${fmt(plotW)} ${fmt(plotH)}")
                attr("preserveAspectRatio", "none")
                attr("role", "img")
                attr("aria-label", t.visitors)
            }) {
                gridLines(plotW, plotH)
                if (showUsers) seriesPath(days.map { it.users }, max, xs, plotH, "var(--lime)", 0.16)
                if (showGuests) seriesPath(days.map { it.guests }, max, xs, plotH, "var(--pink)", 0.12)
                hover?.let { idx ->
                    val x = xs.getOrElse(idx) { PAD_L }
                    SvgLine({
                        attr("x1", fmt(x))
                        attr("x2", fmt(x))
                        attr("y1", fmt(PAD_T))
                        attr("y2", fmt(plotH - PAD_B))
                        attr("stroke", "var(--muted)")
                        attr("stroke-width", "1")
                        attr("stroke-dasharray", "3 3")
                        attr("opacity", "0.7")
                    })
                }
                if (showUsers) seriesDots(days.map { it.users }, max, xs, plotH, "var(--lime)", hover)
                if (showGuests) seriesDots(days.map { it.guests }, max, xs, plotH, "var(--pink)", hover)
            }
            hovered?.let { day ->
                val idx = hover ?: 0
                val pct = if (days.size <= 1) 50.0 else idx * 100.0 / (days.size - 1)
                Div({
                    classes("visits-tooltip")
                    attr("style", "left:${pct.coerceIn(14.0, 86.0)}%")
                }) {
                    P({ classes("visits-tip-day") }) { Text(visitDayTitle(t, day.dayStart)) }
                    if (showUsers) P { Text("${t.users} · ${day.users}") }
                    if (showGuests) P { Text("${t.nonUsers} · ${day.guests}") }
                }
            }
        }
        Div({ classes("visits-axis") }) {
            days.forEachIndexed { i, day ->
                Span({
                    classes("visits-tick")
                    if (hover == i) classes("on")
                }) { Text(visitWeekday(t, day.dayStart)) }
            }
        }
    }
}

@Composable
private fun LegendItem(label: String, tone: String, on: Boolean, toggle: () -> Unit) {
    Button({
        classes("visits-leg")
        if (on) classes("on")
        classes(tone)
        type(ButtonType.Button)
        onClick { toggle() }
    }) {
        Span({ classes("visits-swatch") })
        Text(label)
    }
}

@Composable
private fun gridLines(width: Double, height: Double) {
    val inner = height - PAD_T - PAD_B
    listOf(0.0, 0.5, 1.0).forEach { t ->
        val y = PAD_T + inner * t
        SvgLine({
            attr("x1", fmt(PAD_L))
            attr("x2", fmt(width - PAD_R))
            attr("y1", fmt(y))
            attr("y2", fmt(y))
            attr("stroke", "var(--line)")
            attr("stroke-width", "1")
        })
    }
}

@Composable
private fun seriesPath(
    values: List<Int>,
    max: Int,
    xs: List<Double>,
    height: Double,
    color: String,
    fillOpacity: Double,
) {
    if (values.isEmpty()) return
    val line = values.mapIndexed { i, v -> "${fmt(xs[i])},${fmt(yOf(v, max, height))}" }.joinToString(" ")
    val baseY = fmt(height - PAD_B)
    val area = buildString {
        append(line)
        append(" ${fmt(xs.last())},$baseY ${fmt(xs.first())},$baseY")
    }
    SvgPolygon({
        attr("points", area)
        attr("fill", color)
        attr("opacity", fillOpacity.toString())
    })
    SvgPolyline({
        attr("points", line)
        attr("fill", "none")
        attr("stroke", color)
        attr("stroke-width", "2.5")
        attr("stroke-linejoin", "round")
        attr("stroke-linecap", "round")
    })
}

@Composable
private fun seriesDots(
    values: List<Int>,
    max: Int,
    xs: List<Double>,
    height: Double,
    color: String,
    hover: Int?,
) {
    values.forEachIndexed { i, v ->
        val active = hover == i
        SvgCircle({
            attr("cx", fmt(xs[i]))
            attr("cy", fmt(yOf(v, max, height)))
            attr("r", if (active) "5" else "3.2")
            attr("fill", color)
            attr("stroke", "var(--card)")
            attr("stroke-width", "1.5")
        })
    }
}

private fun yOf(value: Int, max: Int, height: Double): Double =
    visitChartY(value, max, height, PAD_T, PAD_B)

private fun observeElementSize(el: HTMLElement, onSize: (Double, Double) -> Unit): () -> Unit {
    fun emit() {
        val w = el.clientWidth.toDouble()
        val h = el.clientHeight.toDouble()
        if (w > 0 && h > 0) onSize(w, h)
    }
    emit()
    val observer = ResizeObserver { emit() }
    observer.observe(el)
    return { observer.disconnect() }
}

private external class ResizeObserver(callback: () -> Unit) {
    fun observe(target: org.w3c.dom.Element)
    fun disconnect()
}

private fun fmt(n: Double): String = ((n * 10).roundToInt() / 10.0).toString()

private fun hoverFromEvent(el: HTMLElement, event: org.w3c.dom.events.Event, count: Int): Int? {
    val dyn = event.asDynamic()
    val x = if (event.type.startsWith("touch")) {
        val touch = dyn.touches.item(0) ?: dyn.changedTouches.item(0) ?: return null
        (touch.clientX as Double)
    } else {
        (dyn.clientX as? Number)?.toDouble() ?: return null
    }
    return nearestVisitIndex(x - el.getBoundingClientRect().left, el.clientWidth.toDouble(), count)
}

private fun ictDate(dayStart: Long): Date = Date(dayStart.toDouble() + 7 * 3_600_000)

private fun visitWeekday(t: T, dayStart: Long): String {
    val days = listOf(t.sun, t.mon, t.tue, t.wed, t.thu, t.fri, t.sat)
    return days[ictDate(dayStart).getUTCDay()]
}

private fun visitDayTitle(t: T, dayStart: Long): String {
    val d = ictDate(dayStart)
    val month = t.months.getOrElse(d.getUTCMonth()) { "" }
    return "${visitWeekday(t, dayStart)} ${d.getUTCDate()} $month"
}

private fun svgBuilder(tag: String): ElementBuilder<SVGElement> =
    object : ElementBuilder<SVGElement> {
        override fun create(): SVGElement =
            document.createElementNS("http://www.w3.org/2000/svg", tag).unsafeCast<SVGElement>()
    }

@Composable
private fun SvgRoot(attrs: AttrBuilderContext<SVGElement>? = null, content: ContentBuilder<SVGElement>? = null) {
    TagElement(svgBuilder("svg"), attrs, content)
}

@Composable
private fun SvgLine(attrs: AttrBuilderContext<SVGElement>? = null) {
    TagElement(svgBuilder("line"), attrs, null)
}

@Composable
private fun SvgPolyline(attrs: AttrBuilderContext<SVGElement>? = null) {
    TagElement(svgBuilder("polyline"), attrs, null)
}

@Composable
private fun SvgPolygon(attrs: AttrBuilderContext<SVGElement>? = null) {
    TagElement(svgBuilder("polygon"), attrs, null)
}

@Composable
private fun SvgCircle(attrs: AttrBuilderContext<SVGElement>? = null) {
    TagElement(svgBuilder("circle"), attrs, null)
}

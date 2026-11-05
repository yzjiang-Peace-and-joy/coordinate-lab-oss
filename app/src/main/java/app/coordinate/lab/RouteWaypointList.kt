package app.coordinate.lab

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import app.coordinate.lab.core.RoutePointOrder
import app.coordinate.lab.core.RouteWaypointSwipe
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.util.Locale
import kotlin.math.roundToInt

/** Vertical browsing never edits; reorder is local until release and swipe commits only on release. */
@Composable internal fun RouteWaypointList(
    points: List<Place>,
    enabled: Boolean,
    onMove: (Int, Int) -> Unit,
    onRemove: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val scroll = rememberLazyListState()
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val rowHeight = with(density) { 64.dp.toPx() }
    val edge = with(density) { 36.dp.toPx() }
    val scrollStep = with(density) { 5.dp.toPx() }
    var viewportHeight by remember { mutableIntStateOf(0) }
    var from by remember(points, enabled) { mutableIntStateOf(-1) }
    var target by remember(points, enabled) { mutableIntStateOf(-1) }
    var dragOffset by remember(points, enabled) { mutableFloatStateOf(0f) }
    var dragScroll by remember(points, enabled) { mutableFloatStateOf(0f) }
    var pointerY by remember(points, enabled) { mutableFloatStateOf(0f) }
    val moveNow by rememberUpdatedState(onMove)
    val removeNow by rememberUpdatedState(onRemove)
    fun previewTarget(next: Int) {
        if (target != next) {
            // Keep the viewport at its numeric slot while stable item keys change order.
            scroll.requestScrollToItem(scroll.firstVisibleItemIndex, scroll.firstVisibleItemScrollOffset)
            target = next
        }
    }
    val edgeDirection = when {
        from < 0 -> 0
        pointerY < edge -> -1
        pointerY > viewportHeight - edge -> 1
        else -> 0
    }
    LaunchedEffect(from, edgeDirection) {
        if (from < 0 || edgeDirection == 0) return@LaunchedEffect
        while (isActive) {
            dragScroll += scroll.scrollBy(edgeDirection * scrollStep)
            if (from !in points.indices) break
            previewTarget(RoutePointOrder.target(from, dragOffset + dragScroll, rowHeight, points.size))
            delay(16)
        }
    }
    val order = if (from >= 0 && target >= 0) {
        RoutePointOrder.move(points.indices.toList(), from, target)
    } else {
        points.indices.toList()
    }
    Box(modifier.fillMaxWidth().clipToBounds().onSizeChanged { viewportHeight = it.height }
        .pointerInput(points, enabled, rowHeight) {
            if (!enabled || points.isEmpty()) return@pointerInput
            detectDragGesturesAfterLongPress(
                onDragStart = { position ->
                    // Keys are original indices, not coordinates: repeated waypoints remain distinct.
                    val index = scroll.layoutInfo.visibleItemsInfo.firstOrNull {
                        position.y >= it.offset && position.y < it.offset + it.size
                    }?.key as? Int
                    if (index != null && index in points.indices) {
                        from = index
                        target = index
                        dragOffset = 0f
                        dragScroll = 0f
                        pointerY = position.y
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                },
                onDrag = { change, amount ->
                    if (from >= 0) {
                        change.consume()
                        dragOffset += amount.y
                        pointerY = change.position.y
                        previewTarget(RoutePointOrder.target(from, dragOffset + dragScroll, rowHeight, points.size))
                    }
                },
                onDragEnd = {
                    val old = from
                    val next = target
                    from = -1
                    target = -1
                    if (old in points.indices && next in points.indices && old != next) moveNow(old, next)
                },
                onDragCancel = {
                    scroll.requestScrollToItem(scroll.firstVisibleItemIndex, scroll.firstVisibleItemScrollOffset)
                    from = -1; target = -1
                }
            )
        }) {
        LazyColumn(Modifier.fillMaxSize(), state = scroll, userScrollEnabled = from < 0) {
            items(order, key = { it }) { originalIndex ->
                val position = order.indexOf(originalIndex)
                val dragging = originalIndex == from
                RouteWaypointRow(
                    place = points[originalIndex],
                    position = position,
                    last = position == points.lastIndex,
                    enabled = enabled && from < 0,
                    dragging = dragging,
                    dragTranslation = if (dragging) dragOffset + dragScroll - (target - from) * rowHeight else 0f,
                    onRemove = { removeNow(originalIndex) },
                    onMoveUp = { moveNow(originalIndex, originalIndex - 1) },
                    onMoveDown = { moveNow(originalIndex, originalIndex + 1) }
                )
            }
            if (points.isEmpty()) {
                item(key = "empty") {
                    Box(Modifier.fillMaxWidth().height(104.dp),contentAlignment=Alignment.Center) {
                        Icon(AppIcons.Route,"暂无路线点",Modifier.size(32.dp),tint=MaterialTheme.colorScheme.outline)
                    }
                }
            }
        }
    }
}

@Composable private fun RouteWaypointRow(
    place: Place,
    position: Int,
    last: Boolean,
    enabled: Boolean,
    dragging: Boolean,
    dragTranslation: Float,
    onRemove: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit
) {
    val density = LocalDensity.current
    val maxSwipe = with(density) { 96.dp.toPx() }
    val colors = MaterialTheme.colorScheme
    val label = place.name.takeUnless {
        it.isBlank() || it in setOf("地图路线点", "地图选点", "选定位置", "自定义地点")
    } ?: String.format(Locale.US, "%.5f, %.5f", place.point.latitude, place.point.longitude)
    var rowWidth by remember { mutableIntStateOf(0) }
    var swipeOffset by remember(place, enabled) { mutableFloatStateOf(0f) }
    var swiping by remember(place, enabled) { mutableStateOf(false) }
    val removeNow by rememberUpdatedState(onRemove)
    val visibleSwipe by animateFloatAsState(swipeOffset,
        animationSpec = if (swiping) snap() else tween(160), label = "waypointSwipe")
    Box(Modifier.fillMaxWidth().height(64.dp).zIndex(if (dragging) 1f else 0f)
        .graphicsLayer { translationY = dragTranslation }
        .onSizeChanged { rowWidth = it.width }
        .pointerInput(place, enabled, maxSwipe) {
            if (!enabled) return@pointerInput
            detectHorizontalDragGestures(
                onDragStart = { swiping = true },
                onHorizontalDrag = { change, amount ->
                    change.consume()
                    swipeOffset = (swipeOffset + amount).coerceIn(-rowWidth.toFloat(), 0f)
                },
                onDragEnd = {
                    val remove = RouteWaypointSwipe.shouldRemove(swipeOffset, rowWidth.toFloat(), maxSwipe)
                    swiping = false
                    swipeOffset = 0f
                    if (remove) removeNow()
                },
                onDragCancel = { swiping = false; swipeOffset = 0f }
            )
        }) {
        if (visibleSwipe < 0f) {
            Box(Modifier.fillMaxSize().background(colors.errorContainer), contentAlignment = Alignment.CenterEnd) {
                Icon(AppIcons.ClearAll, null, Modifier.padding(end = 24.dp).size(22.dp), tint = colors.onErrorContainer)
            }
        }
        Row(
            Modifier.fillMaxSize().offset { IntOffset(visibleSwipe.roundToInt(), 0) }
                .background(if (dragging) colors.surfaceContainerHigh else colors.surface)
                .semantics(mergeDescendants = true) {
                    contentDescription = "第 ${position + 1} 个路线点，$label"
                    if (enabled) customActions = buildList {
                        if (position > 0) add(CustomAccessibilityAction("上移") { onMoveUp(); true })
                        if (!last) add(CustomAccessibilityAction("下移") { onMoveDown(); true })
                        add(CustomAccessibilityAction("删除路线点") { removeNow(); true })
                    }
                }.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.width(36.dp).fillMaxHeight(), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val x = size.width / 2
                    val y = size.height / 2
                    val radius = 15.dp.toPx()
                    if (position > 0) drawLine(colors.outlineVariant, Offset(x, 0f), Offset(x, y - radius), 1.dp.toPx())
                    if (!last) drawLine(colors.outlineVariant, Offset(x, y + radius), Offset(x, size.height), 1.dp.toPx())
                }
                Box(Modifier.size(30.dp).clip(CircleShape).background(
                    if (dragging) colors.primary else colors.primaryContainer
                ), contentAlignment = Alignment.Center) {
                    Text("${position + 1}", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold,
                        color = if (dragging) colors.onPrimary else colors.onPrimaryContainer)
                }
            }
            Spacer(Modifier.width(12.dp))
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (enabled || dragging) {
                Spacer(Modifier.width(12.dp))
                Canvas(Modifier.size(18.dp)) {
                    val spacing = 5.dp.toPx()
                    val radius = 1.dp.toPx()
                    for (column in 0..1) for (row in 0..2) {
                        drawCircle(colors.onSurfaceVariant.copy(alpha = .5f), radius,
                            Offset(size.width / 2 + (column - .5f) * spacing, size.height / 2 + (row - 1) * spacing))
                    }
                }
            }
        }
    }
}

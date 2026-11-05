package app.coordinate.lab

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Only actual controls consume touches; the remaining canvas stays available to the map. */
@Composable
internal fun MapFocusOverlay(
    tool: MapWorkspaceTool,
    sketchPoints: Int,
    strokeCount: Int,
    picked: Place?,
    marked: Boolean,
    preview: SavedRoute?,
    onExit: () -> Unit,
    onTool: (MapWorkspaceTool) -> Unit,
    onUndo: () -> Unit,
    onClear: () -> Unit,
    onSaveSketch: () -> Unit,
    onClearPreview: () -> Unit,
    onSavePlace: () -> Unit,
    onMark: () -> Unit,
    onRemoveMark: () -> Unit,
    onDismissPick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier.fillMaxSize()) {
        Column(
            Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                FocusToolButton(AppIcons.Map, "浏览地图", selected = tool == MapWorkspaceTool.BROWSE, onClick = { onTool(MapWorkspaceTool.BROWSE) })
                FocusToolButton(AppIcons.Edit, "描绘路线", selected = tool == MapWorkspaceTool.DRAW, onClick = { onTool(MapWorkspaceTool.DRAW) })
                Spacer(Modifier.weight(1f))
                FocusToolButton(AppIcons.Unlock, "退出全屏地图", onClick = onExit)
            }
            preview?.let { route ->
                Surface(
                    modifier = Modifier.widthIn(max = 360.dp).fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(18.dp),
                    shadowElevation = 1.dp
                ) {
                    Row(Modifier.padding(start = 14.dp, end = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (route.closed) AppIcons.Loop else AppIcons.Route, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Column(Modifier.weight(1f).padding(horizontal = 10.dp, vertical = 8.dp)) {
                            Text(route.name, style = MaterialTheme.typography.labelLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            if (route.road && !route.previewGeometryReady) {
                                Text("沿道路草稿 · 直线示意", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        FocusToolButton(AppIcons.Close, "关闭路线预览 ${route.name}", elevated = false, onClick = onClearPreview)
                    }
                }
            }
            picked?.let { place ->
                FocusPickedPlace(
                    place = place,
                    marked = marked,
                    onSavePlace = onSavePlace,
                    onMark = onMark,
                    onRemoveMark = onRemoveMark,
                    onDismiss = onDismissPick
                )
            }
        }
        if (tool == MapWorkspaceTool.DRAW && strokeCount > 0) {
            Column(
                Modifier.align(Alignment.CenterEnd).padding(end = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                FocusToolButton(AppIcons.Undo, "撤销上一笔描线", onClick = onUndo)
                FocusToolButton(AppIcons.ClearAll, "清空描线", onClick = onClear)
                FocusToolButton(AppIcons.BookmarkBorder, "命名并收藏手绘路线", enabled = sketchPoints >= 2, onClick = onSaveSketch)
            }
        }
    }
}

@Composable
private fun FocusPickedPlace(
    place: Place,
    marked: Boolean,
    onSavePlace: () -> Unit,
    onMark: () -> Unit,
    onRemoveMark: () -> Unit,
    onDismiss: () -> Unit
) {
    Surface(
        modifier = Modifier.widthIn(max = 440.dp).fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(18.dp),
        shadowElevation = 1.dp
    ) {
        BoxWithConstraints {
            val stacked = maxWidth < 340.dp || LocalDensity.current.fontScale > 1.3f
            if (stacked) {
                Column(Modifier.padding(start = 14.dp, end = 2.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(place.name, Modifier.weight(1f).padding(vertical = 8.dp), style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        FocusToolButton(AppIcons.Close, "关闭地点卡片", elevated = false, onClick = onDismiss)
                    }
                    Row(Modifier.align(Alignment.End)) {
                        FocusPickedActions(place, marked, onSavePlace, onMark, onRemoveMark)
                    }
                }
            } else {
                Row(Modifier.padding(start = 14.dp, end = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(place.name, Modifier.weight(1f).padding(end = 4.dp, top = 8.dp, bottom = 8.dp), style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    FocusPickedActions(place, marked, onSavePlace, onMark, onRemoveMark)
                    FocusToolButton(AppIcons.Close, "关闭地点卡片", elevated = false, onClick = onDismiss)
                }
            }
        }
    }
}

@Composable
private fun FocusPickedActions(place: Place, marked: Boolean, onSavePlace: () -> Unit, onMark: () -> Unit, onRemoveMark: () -> Unit) {
    var markMenu by remember(place.id) { mutableStateOf(false) }
    FocusToolButton(AppIcons.BookmarkBorder, "收藏地点 ${place.name}", elevated = false, onClick = onSavePlace)
    FocusToolButton(AppIcons.Flag, if (marked) "重命名标记 ${place.name}" else "标记地点 ${place.name}", selected = marked, elevated = false, onClick = onMark)
    if (marked) {
        Box {
            FocusToolButton(AppIcons.More, "标记更多操作", elevated = false, onClick = { markMenu = true })
            DropdownMenu(expanded = markMenu, onDismissRequest = { markMenu = false }) {
                DropdownMenuItem(
                    text = { Text("移除标记") },
                    leadingIcon = { Icon(AppIcons.ClearAll, null) },
                    onClick = { markMenu = false; onRemoveMark() }
                )
            }
        }
    }
}

/** Forty-dp visible surface with a separate, non-overlapping forty-eight-dp touch target. */
@Composable
private fun FocusToolButton(
    icon: ImageVector,
    label: String,
    selected: Boolean? = null,
    enabled: Boolean = true,
    elevated: Boolean = true,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(48.dp).semantics { if (selected != null) this.selected = selected },
        enabled = enabled
    ) {
        Surface(
            Modifier.size(40.dp),
            color = if (selected == true) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
            contentColor = if (!enabled) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f) else if (selected == true) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            shape = RoundedCornerShape(14.dp),
            shadowElevation = if (elevated) 2.dp else 0.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, label, Modifier.size(21.dp))
            }
        }
    }
}

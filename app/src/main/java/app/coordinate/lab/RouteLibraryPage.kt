@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package app.coordinate.lab

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.coordinate.lab.core.Geo
import java.util.Locale

/** Selecting a saved route loads it into the normal route controls, without starting a simulation. */
@Composable
internal fun RouteLibraryPage(
    state: UiState,
    onPlaceSelect: (Place) -> Unit,
    onPlaceRemove: (Place) -> Unit,
    onRouteSelect: (SavedRoute) -> Unit,
    onRouteRename: (SavedRoute, String) -> Boolean,
    onRouteRemove: (SavedRoute) -> Unit
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var renameRoute by remember { mutableStateOf<SavedRoute?>(null) }
    var deleteRoute by remember { mutableStateOf<SavedRoute?>(null) }
    var deletePlace by remember { mutableStateOf<Place?>(null) }

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        TabRow(
            selectedTabIndex = tab,
            containerColor = MaterialTheme.colorScheme.background,
            divider = { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant) }
        ) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("地点 ${state.favorites.size}") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("路线 ${state.savedRoutes.size}") })
        }
        if (tab == 0) {
            if (state.favorites.isEmpty()) {
                LibraryEmpty(AppIcons.BookmarkBorder, "还没有收藏地点", "选中地点后，点书签保存")
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(state.favorites, key = { it.id }) { place ->
                        SavedPlaceRow(place, onSelect = { onPlaceSelect(place) }, onRemove = { deletePlace = place })
                    }
                }
            }
        } else {
            if (state.savedRoutes.isEmpty()) {
                LibraryEmpty(AppIcons.Route, "还没有收藏路线", "在路线菜单中保存，或在全屏地图描绘")
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(state.savedRoutes, key = { it.id }) { route ->
                        SavedRouteRow(
                            route,
                            onSelect = { onRouteSelect(route) },
                            onRename = { renameRoute = route },
                            onRemove = { deleteRoute = route }
                        )
                    }
                }
            }
        }
    }
    renameRoute?.let { route ->
        RouteNameDialog(
            title = "重命名路线",
            initialName = route.name,
            onDismiss = { renameRoute = null },
            onSave = { onRouteRename(route, it) }
        )
    }
    deleteRoute?.let { route ->
        LibraryDeleteDialog(
            title = "删除路线？",
            name = route.name,
            onDismiss = { deleteRoute = null },
            onDelete = { onRouteRemove(route); deleteRoute = null }
        )
    }
    deletePlace?.let { place ->
        LibraryDeleteDialog(
            title = "取消收藏地点？",
            name = place.name,
            confirmLabel = "取消收藏",
            onDismiss = { deletePlace = null },
            onDelete = { onPlaceRemove(place); deletePlace = null }
        )
    }
}

@Composable
private fun SavedPlaceRow(place: Place, onSelect: () -> Unit, onRemove: () -> Unit) {
    var menuOpen by remember(place.id) { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().combinedClickable(
            onClickLabel = "选择地点 ${place.name}",
            onLongClickLabel = "管理地点 ${place.name}",
            onLongClick = { menuOpen = true },
            onClick = onSelect
        ).padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LibraryRowIcon(AppIcons.LocationOn)
        Column(Modifier.weight(1f).padding(start = 12.dp, end = 4.dp)) {
            Text(place.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val detail = place.detail.ifBlank { place.city }
            if (detail.isNotBlank()) {
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        Box {
            IconButton(onClick = { menuOpen = true }) { Icon(AppIcons.More, "管理地点 ${place.name}") }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("取消收藏") },
                    leadingIcon = { Icon(AppIcons.BookmarkBorder, null) },
                    onClick = { menuOpen = false; onRemove() }
                )
            }
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun SavedRouteRow(route: SavedRoute, onSelect: () -> Unit, onRename: () -> Unit, onRemove: () -> Unit) {
    var menuOpen by remember(route.id) { mutableStateOf(false) }
    val summary = remember(route) { savedRouteSummary(route) }
    Row(
        Modifier.fillMaxWidth().combinedClickable(
            onClickLabel = "载入路线 ${route.name}",
            onLongClickLabel = "管理路线 ${route.name}",
            onLongClick = { menuOpen = true },
            onClick = onSelect
        ).padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LibraryRowIcon(if (route.closed) AppIcons.Loop else AppIcons.Route)
        Column(Modifier.weight(1f).padding(start = 12.dp, end = 4.dp)) {
            Text(route.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Box {
            IconButton(onClick = { menuOpen = true }) { Icon(AppIcons.More, "管理路线 ${route.name}") }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(text = { Text("重命名") }, leadingIcon = { Icon(AppIcons.Edit, null) }, onClick = { menuOpen = false; onRename() })
                DropdownMenuItem(text = { Text("删除") }, leadingIcon = { Icon(AppIcons.ClearAll, null) }, onClick = { menuOpen = false; onRemove() })
            }
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

private fun savedRouteSummary(route: SavedRoute): String {
    val drawn = route.kind == SavedRouteKind.DRAWN
    val roadDraft = route.road && !route.previewGeometryReady && !drawn
    return buildList {
        if (!roadDraft && route.points.size >= 2) {
            val meters = route.points.zipWithNext { a, b -> Geo.distance(a, b) }.sum()
            add(if (meters < 1000.0) "${meters.toInt()} m" else String.format(Locale.CHINA, "%.1f km", meters / 1000.0))
        }
        add(if (drawn) "手绘 · ${route.points.size} 个轨迹点" else "${route.waypoints.size} 个地点")
        if (roadDraft) add("沿道路草稿")
        if (route.closed) add("闭环")
    }.joinToString(" · ")
}

@Composable
private fun LibraryRowIcon(icon: ImageVector) {
    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primaryContainer) {
        Icon(icon, null, Modifier.padding(10.dp).size(20.dp), tint = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun LibraryEmpty(icon: ImageVector, title: String, detail: String) {
    Column(
        Modifier.fillMaxWidth().padding(top = 64.dp, start = 12.dp, end = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(icon, null, Modifier.size(36.dp), tint = MaterialTheme.colorScheme.primary)
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun LibraryDeleteDialog(title: String, name: String, confirmLabel: String = "删除", onDismiss: () -> Unit, onDelete: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(name, maxLines = 3, overflow = TextOverflow.Ellipsis) },
        confirmButton = { TextButton(onClick = onDelete) { Text(confirmLabel, color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("保留") } }
    )
}

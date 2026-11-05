package app.coordinate.lab

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Static, debug-only visual QA. Deliberately has no app state, preferences or location access. */
class MapArtPreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { CoordinateTheme { MapArtPreview() } }
    }
}

private data class PreviewMarker(val title: String, val asset: MapMarkerAsset)

@Composable
private fun MapArtPreview() {
    val density = LocalDensity.current.density
    val markers = remember(density) {
        listOf(
            Triple("真实位置", MapMarkerRole.REAL, ""),
            Triple("选定地点", MapMarkerRole.SELECTED, ""),
            Triple("移动位置", MapMarkerRole.MOVING, ""),
            Triple("地图标记", MapMarkerRole.ANNOTATION, ""),
            Triple("起点", MapMarkerRole.START, "1"),
            Triple("途经点", MapMarkerRole.VIA, "12"),
            Triple("终点", MapMarkerRole.END, "20"),
            Triple("闭环", MapMarkerRole.LOOP, "")
        ).map { (title, role, label) -> PreviewMarker(title, MapMarkerArt.create(role, label, density)) }
    }
    DisposableEffect(markers) {
        onDispose { markers.forEach { if (!it.asset.bitmap.isRecycled) it.asset.bitmap.recycle() } }
    }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text("图标预览 · 不启动模拟", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(Modifier.size(52.dp), shape = RoundedCornerShape(18.dp), color = Color(0xFF176349)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(AppIcons.Pause, "新版运行态图标预览", Modifier.size(24.dp), tint = Color.White)
                    }
                }
                Text("00:00:00", style = MaterialTheme.typography.titleMedium, color = Color(0xFF176349))
            }
            MarkerPreviewCard("浅色地图", markers, density, dark = false)
            MarkerPreviewCard("深色地图", markers, density, dark = true)
        }
    }
}

@Composable
private fun MarkerPreviewCard(title: String, markers: List<PreviewMarker>, density: Float, dark: Boolean) {
    val text = if (dark) Color(0xFFE2EEE8) else Color(0xFF38554A)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = if (dark) Color(0xFF263A35) else Color(0xFFE9F1ED)
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, Modifier.padding(horizontal = 6.dp), color = text, style = MaterialTheme.typography.labelLarge)
            markers.chunked(4).forEach { row ->
                Row(Modifier.fillMaxWidth()) {
                    row.forEach { marker ->
                        Column(
                            Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Box(Modifier.size(46.dp), contentAlignment = Alignment.Center) {
                                Image(
                                    bitmap = marker.asset.bitmap.asImageBitmap(),
                                    contentDescription = marker.title,
                                    modifier = Modifier.size(
                                        (marker.asset.bitmap.width / density).dp,
                                        (marker.asset.bitmap.height / density).dp
                                    )
                                )
                            }
                            Text(marker.title, color = text, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}

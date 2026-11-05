@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package app.coordinate.lab

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable internal fun MapLayerSheet(style: MapStyle,traffic: Boolean,onStyle: (MapStyle)->Unit,onTraffic: (Boolean)->Unit,onDismiss: ()->Unit) {
    ModalBottomSheet(onDismissRequest=onDismiss) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal=20.dp).padding(bottom=24.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text("地图类型",style=MaterialTheme.typography.titleLarge)
            MapStyle.entries.chunked(3).forEach {row->
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    row.forEach {item->
                        val chosen=style==item
                        OutlinedCard(onClick={onStyle(item);onDismiss()},modifier=Modifier.weight(1f).semantics {selected=chosen},shape=RoundedCornerShape(16.dp),border=BorderStroke(1.dp,if(chosen)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),colors=CardDefaults.outlinedCardColors(containerColor=if(chosen)MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow)) {
                            Column(Modifier.fillMaxWidth().padding(horizontal=4.dp,vertical=14.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(8.dp)) {
                                Icon(when(item){MapStyle.NORMAL->AppIcons.Map;MapStyle.SATELLITE->AppIcons.Layers;MapStyle.NIGHT,MapStyle.NAVIGATION_NIGHT->AppIcons.Night;MapStyle.NAVIGATION->AppIcons.LocationOn;MapStyle.TRANSIT->AppIcons.Bus},null,Modifier.size(24.dp))
                                Text(item.label,style=MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
            }
            HorizontalDivider(Modifier.padding(top=4.dp))
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Text("实时路况",Modifier.weight(1f),style=MaterialTheme.typography.bodyLarge)
                Switch(checked=traffic,onCheckedChange=onTraffic,modifier=Modifier.semantics {contentDescription="实时路况"})
            }
        }
    }
}

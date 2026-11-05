package app.coordinate.lab

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.unit.dp
import app.coordinate.lab.core.RoutePointOrder
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.floor

/** Drag is previewed locally, then committed once on drop; cancel never edits the draft. */
@Composable internal fun RoutePointStrip(points: List<Place>,enabled: Boolean,onMove: (Int,Int)->Unit) {
    val scroll=rememberScrollState()
    val density=LocalDensity.current
    val haptic=LocalHapticFeedback.current
    val slot=with(density){46.dp.toPx()}
    val edge=with(density){24.dp.toPx()}
    val step=with(density){6.dp.toPx()}
    var width by remember {mutableIntStateOf(0)}
    var from by remember(points,enabled){mutableIntStateOf(-1)}
    var target by remember(points,enabled){mutableIntStateOf(-1)}
    var dx by remember(points,enabled){mutableFloatStateOf(0f)}
    var startScroll by remember(points,enabled){mutableIntStateOf(0)}
    var pointerX by remember(points,enabled){mutableFloatStateOf(0f)}
    val moveNow by rememberUpdatedState(onMove)
    val edgeDirection=when {from<0->0;pointerX<edge->-1;pointerX>width-edge->1;else->0}
    LaunchedEffect(from,edgeDirection) {
        if(from<0 || edgeDirection==0)return@LaunchedEffect
        while(isActive) {
            scroll.scrollBy(edgeDirection*step)
            val active=from
            if(active !in points.indices)break
            target=RoutePointOrder.target(active,dx+scroll.value-startScroll,slot,points.size)
            delay(16)
        }
    }
    val order=if(from>=0 && target>=0)RoutePointOrder.move(points.indices.toList(),from,target) else points.indices.toList()
    Box(Modifier.fillMaxWidth().height(42.dp).onSizeChanged {width=it.width}.pointerInput(points,enabled,slot) {
        if(!enabled || points.isEmpty())return@pointerInput
        detectDragGesturesAfterLongPress(
            onDragStart={offset->
                val index=floor((offset.x+scroll.value)/slot).toInt()
                if(index in points.indices){from=index;target=index;dx=0f;startScroll=scroll.value;pointerX=offset.x;haptic.performHapticFeedback(HapticFeedbackType.LongPress)}
            },
            onDrag={change,amount->
                if(from>=0){change.consume();dx+=amount.x;pointerX=change.position.x
                    target=RoutePointOrder.target(from,dx+scroll.value-startScroll,slot,points.size)}
            },
            onDragEnd={val old=from;val next=target;from=-1;target=-1;if(old>=0 && next>=0 && old!=next)moveNow(old,next)},
            onDragCancel={from=-1;target=-1}
        )
    }) {
        Row(Modifier.horizontalScroll(scroll,enabled=from<0),horizontalArrangement=Arrangement.spacedBy(6.dp),verticalAlignment=Alignment.CenterVertically) {
            if(points.isEmpty())Text("—",color=MaterialTheme.colorScheme.onSurfaceVariant)
            order.forEach {index->key(index){
                val dragging=index==from
                val position=order.indexOf(index)
                val color=if(dragging)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer
                Box(Modifier.size(40.dp).clip(RoundedCornerShape(11.dp)).background(color).semantics {
                    contentDescription="第 ${position+1} 个路线点，${points[index].name}，${points[index].id}"
                    if(enabled)customActions=listOf(
                        CustomAccessibilityAction("前移"){if(position>0){moveNow(position,position-1);true}else false},
                        CustomAccessibilityAction("后移"){if(position<points.lastIndex){moveNow(position,position+1);true}else false}
                    )
                },contentAlignment=Alignment.Center) {
                    Text("${index+1}",fontWeight=FontWeight.SemiBold,color=if(dragging)MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }}
        }
    }
}

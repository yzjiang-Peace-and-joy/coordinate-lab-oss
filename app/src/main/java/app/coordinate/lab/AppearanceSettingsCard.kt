package app.coordinate.lab

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import kotlin.math.roundToInt
import kotlin.math.cos
import kotlin.math.sin

/** Changes are immediate except for the custom picker, whose draft is isolated until Save. */
@Composable internal fun AppearanceSettingsCard(
    settings: AppearanceSettings,
    onTheme: (ThemeMode) -> Unit,
    onAccent: (String) -> Boolean,
    onReset: () -> Unit
) {
    var customPicker by rememberSaveable { mutableStateOf(false) }
    val selectedHex = AppearanceColors.normalizeHex(settings.accentHex) ?: "176349"
    Surface(shape=MaterialTheme.shapes.large,color=MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().padding(18.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Icon(AppIcons.Tune,null,tint=MaterialTheme.colorScheme.primary)
                Text("外观",Modifier.weight(1f).padding(start=10.dp),style=MaterialTheme.typography.titleMedium)
                TextButton(onClick=onReset,contentPadding=PaddingValues(horizontal=8.dp)) { Text("重置") }
            }
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).selectableGroup(),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                listOf(ThemeMode.SYSTEM to "跟随系统",ThemeMode.LIGHT to "浅色",ThemeMode.DARK to "深色").forEach { (mode,label) ->
                    val chosen = settings.theme == mode
                    Column(
                        Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(16.dp))
                            .background(if(chosen)MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow)
                            .selectable(selected=chosen,onClick={onTheme(mode)},role=Role.RadioButton)
                            .heightIn(min=76.dp).padding(horizontal=5.dp,vertical=12.dp),
                        horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(7.dp,Alignment.CenterVertically)
                    ) {
                        val tint=if(chosen)MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                        if(mode==ThemeMode.LIGHT)SunSymbol(tint) else Icon(if(mode==ThemeMode.SYSTEM)AppIcons.Tune else AppIcons.Night,null,Modifier.size(20.dp),tint=tint)
                        Text(label,style=MaterialTheme.typography.labelLarge,textAlign=TextAlign.Center,
                            color=if(chosen)MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Text("强调色",style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.onSurfaceVariant)
            Column(Modifier.selectableGroup(),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                AppearanceColors.presets.chunked(4).forEach { presets ->
                    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min),horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                        presets.forEach { preset ->
                            val chosen = selectedHex == preset.hex
                            val color = appearanceSwatch(preset.hex)
                            Column(
                                Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(14.dp))
                                    .selectable(selected=chosen,onClick={onAccent(preset.hex)},role=Role.RadioButton)
                                    .semantics { contentDescription="强调色，${preset.label}，#${preset.hex}" }
                                    .heightIn(min=72.dp).padding(vertical=8.dp,horizontal=2.dp),
                                horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(7.dp)
                            ) {
                                Box(Modifier.size(34.dp).border(if(chosen)2.dp else 1.dp,
                                    if(chosen)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,CircleShape)
                                    .padding(if(chosen)4.dp else 1.dp).background(color,CircleShape),contentAlignment=Alignment.Center) {
                                    if(chosen)Icon(AppIcons.Check,null,Modifier.size(17.dp),tint=swatchForeground(color))
                                }
                                Text(preset.label,style=MaterialTheme.typography.labelMedium,textAlign=TextAlign.Center,
                                    color=if(chosen)MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        repeat(4-presets.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
            OutlinedCard(onClick={customPicker=true},shape=RoundedCornerShape(16.dp),
                modifier=Modifier.fillMaxWidth().semantics { contentDescription="自定义强调色，当前 #$selectedHex" },
                colors=CardDefaults.outlinedCardColors(containerColor=MaterialTheme.colorScheme.surface),
                border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)) {
                Row(Modifier.fillMaxWidth().padding(12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(30.dp).border(1.dp,MaterialTheme.colorScheme.outlineVariant,CircleShape).padding(1.dp).background(appearanceSwatch(selectedHex),CircleShape))
                    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(2.dp)) {
                        Text("自定义",style=MaterialTheme.typography.bodyMedium)
                        Text("#$selectedHex",style=MaterialTheme.typography.labelSmall,fontFamily=FontFamily.Monospace,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(AppIcons.Edit,null,Modifier.size(20.dp),tint=MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
    if(customPicker)AccentPickerDialog(selectedHex,onDismiss={customPicker=false},onSave={if(onAccent(it)){customPicker=false;true}else false})
}

@Composable private fun AccentPickerDialog(initialHex:String,onDismiss:()->Unit,onSave:(String)->Boolean) {
    val initialHsv = remember(initialHex) { FloatArray(3).also { android.graphics.Color.colorToHSV(android.graphics.Color.parseColor("#$initialHex"),it) } }
    var hue by rememberSaveable { mutableFloatStateOf(initialHsv[0]) }
    var saturation by rememberSaveable { mutableFloatStateOf(initialHsv[1]) }
    var value by rememberSaveable { mutableFloatStateOf(initialHsv[2]) }
    var hex by rememberSaveable { mutableStateOf(initialHex) }
    var attempted by rememberSaveable { mutableStateOf(false) }
    var saveError by rememberSaveable { mutableStateOf(false) }
    var hexFocused by remember { mutableStateOf(false) }
    val hexRow = remember { BringIntoViewRequester() }
    val imeBottom = WindowInsets.ime.getBottom(LocalDensity.current)
    LaunchedEffect(hexFocused,imeBottom,attempted,saveError) {
        if(hexFocused)hexRow.bringIntoView()
    }
    fun setHsv(h:Float,s:Float,v:Float) {
        hue=h.coerceIn(0f,360f);saturation=s.coerceIn(0f,1f);value=v.coerceIn(0f,1f)
        hex=String.format(java.util.Locale.ROOT,"%06X",android.graphics.Color.HSVToColor(floatArrayOf(hue,saturation,value)) and 0xFFFFFF)
        attempted=false;saveError=false
    }
    fun save() {
        val valid=AppearanceColors.normalizeHex(hex)
        saveError=false
        if(valid!=null) { if(!onSave(valid))saveError=true } else attempted=true
    }
    val preview=Color.hsv(hue,saturation,value)
    // Target 36 dialogs are edge-to-edge. Reserve the keyboard/system insets explicitly:
    // Material's weighted text area can then scroll without moving its fixed action row under IME.
    AlertDialog(onDismissRequest=onDismiss,title={Text("自定义强调色")},
        modifier=Modifier.safeDrawingPadding().imePadding(),
        properties=DialogProperties(decorFitsSystemWindows=false),
        text={
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(14.dp)) {
                SaturationValuePanel(hue,saturation,value,{s,v->setHsv(hue,s,v)})
                Column {
                    Text("色相",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    // The spectrum stays visible even while its handle sits over a pale accent.
                    Box(Modifier.fillMaxWidth(),contentAlignment=Alignment.Center) {
                        Box(Modifier.fillMaxWidth().height(10.dp).padding(horizontal=10.dp).clip(CircleShape).background(Brush.horizontalGradient(
                            listOf(Color.Red,Color.Yellow,Color.Green,Color.Cyan,Color.Blue,Color.Magenta,Color.Red))))
                        Slider(value=hue,onValueChange={setHsv(it,saturation,value)},valueRange=0f..360f,
                            modifier=Modifier.fillMaxWidth().semantics { contentDescription="色相" },
                            colors=SliderDefaults.colors(activeTrackColor=Color.Transparent,inactiveTrackColor=Color.Transparent,
                                thumbColor=Color.hsv(hue,1f,1f)))
                    }
                }
                Row(Modifier.fillMaxWidth().bringIntoViewRequester(hexRow),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(preview)
                        .border(1.dp,MaterialTheme.colorScheme.outlineVariant,RoundedCornerShape(12.dp)))
                    OutlinedTextField(value=hex,onValueChange={input->
                        val candidate=input.trim().removePrefix("#")
                        if(candidate.length<=6) {
                            hex=candidate.uppercase(java.util.Locale.ROOT);attempted=false;saveError=false
                            AppearanceColors.normalizeHex(hex)?.let { normalized->
                                val hsv=FloatArray(3)
                                android.graphics.Color.colorToHSV(android.graphics.Color.parseColor("#$normalized"),hsv)
                                hue=hsv[0];saturation=hsv[1];value=hsv[2]
                            }
                        }
                    },modifier=Modifier.weight(1f).onFocusChanged {hexFocused=it.isFocused},label={Text("HEX")},prefix={Text("#")},singleLine=true,
                        isError=saveError || (attempted && AppearanceColors.normalizeHex(hex)==null),
                        supportingText={Text(when {
                            saveError->"保存失败，请重试"
                            attempted && AppearanceColors.normalizeHex(hex)==null->"请输入 6 位十六进制颜色"
                            else->"自动适配明暗对比"
                        })},
                        keyboardOptions=KeyboardOptions(capitalization=KeyboardCapitalization.Characters,autoCorrectEnabled=false,keyboardType=KeyboardType.Ascii,imeAction=ImeAction.Done),
                        keyboardActions=KeyboardActions(onDone={save()}))
                }
            }
        },confirmButton={TextButton(onClick={save()}){Text("保存")}},dismissButton={TextButton(onClick=onDismiss){Text("取消")}})
}

@Composable private fun SaturationValuePanel(hue:Float,saturation:Float,value:Float,onChange:(Float,Float)->Unit) {
    val latestChange by rememberUpdatedState(onChange)
    val latestSaturation by rememberUpdatedState(saturation)
    val latestValue by rememberUpdatedState(value)
    Canvas(Modifier.fillMaxWidth().height(164.dp).clip(RoundedCornerShape(16.dp))
        .semantics {
            contentDescription="饱和度 ${(saturation*100).roundToInt()}%，明度 ${(value*100).roundToInt()}%"
            customActions=listOf(
                CustomAccessibilityAction("增加饱和度"){latestChange((latestSaturation+.05f).coerceAtMost(1f),latestValue);true},
                CustomAccessibilityAction("减少饱和度"){latestChange((latestSaturation-.05f).coerceAtLeast(0f),latestValue);true},
                CustomAccessibilityAction("增加明度"){latestChange(latestSaturation,(latestValue+.05f).coerceAtMost(1f));true},
                CustomAccessibilityAction("减少明度"){latestChange(latestSaturation,(latestValue-.05f).coerceAtLeast(0f));true})
        }
        .pointerInput(Unit) {
            awaitEachGesture {
                val down=awaitFirstDown(requireUnconsumed=false)
                fun choose(position:Offset) {
                    if(size.width>0 && size.height>0)latestChange((position.x/size.width).coerceIn(0f,1f),1f-(position.y/size.height).coerceIn(0f,1f))
                }
                down.consume();choose(down.position)
                do {
                    val event=awaitPointerEvent()
                    val change=event.changes.firstOrNull {it.id==down.id}
                    if(event.changes.count {it.pressed}>1)break
                    if(change!=null) {change.consume();choose(change.position)}
                } while(change?.pressed==true)
            }
        }) {
        drawRect(Brush.horizontalGradient(listOf(Color.White,Color.hsv(hue,1f,1f))))
        drawRect(Brush.verticalGradient(listOf(Color.Transparent,Color.Black)))
        val radius=7.dp.toPx()
        val center=Offset((saturation*size.width).coerceIn(radius,size.width-radius),((1f-value)*size.height).coerceIn(radius,size.height-radius))
        drawCircle(Color.Black.copy(alpha=.4f),radius+1.dp.toPx(),center,style=Stroke(3.dp.toPx()))
        drawCircle(Color.White,radius,center,style=Stroke(2.dp.toPx()))
    }
}

private fun appearanceSwatch(hex:String)=Color((0xFF000000L or hex.toLong(16)).toInt())
private fun swatchForeground(color:Color):Color = if(color.luminance()>.179f)Color.Black else Color.White

@Composable private fun SunSymbol(tint:Color) {
    Canvas(Modifier.size(20.dp)) {
        val stroke=1.7.dp.toPx()
        drawCircle(tint,3.2.dp.toPx(),style=Stroke(stroke))
        repeat(8) {index->
            val angle=index*Math.PI/4
            val direction=Offset(cos(angle).toFloat(),sin(angle).toFloat())
            drawLine(tint,center+direction*6.dp.toPx(),center+direction*8.5.dp.toPx(),strokeWidth=stroke,cap=androidx.compose.ui.graphics.StrokeCap.Round)
        }
    }
}

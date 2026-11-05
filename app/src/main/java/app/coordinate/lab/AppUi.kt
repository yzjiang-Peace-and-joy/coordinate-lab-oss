@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package app.coordinate.lab

import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.coordinate.lab.core.GeoPoint
import kotlinx.coroutines.delay

@Composable fun CoordinateTheme(settings: AppearanceSettings=AppearanceSettings(),content: @Composable ()->Unit) =
    CoordinateAppearanceTheme(settings,content)

@Composable fun CoordinateApp(vm: AppViewModel,onStart: ()->Unit,onPermission: ()->Unit,onDeveloper: ()->Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val runtime by SimulationBus.state.collectAsStateWithLifecycle()
    val now by produceState(SystemClock.elapsedRealtime(),runtime.active) {
        do { value=SystemClock.elapsedRealtime();if(runtime.active)delay(1000) } while(runtime.active)
    }
    var page by rememberSaveable { mutableIntStateOf(0) }
    var focusedMap by rememberSaveable { mutableStateOf(false) }
    var search by rememberSaveable { mutableStateOf(false) }
    var setup by rememberSaveable { mutableStateOf(false) }
    var manual by rememberSaveable { mutableStateOf(false) }
    val snack=remember { SnackbarHostState() }
    LaunchedEffect(state.message){state.message?.let { snack.showSnackbar(it);vm.notice(null) }}
    LaunchedEffect(runtime.message,runtime.active){
        if(!runtime.active && runtime.message.isNotBlank() && !runtime.message.startsWith("已停止模拟"))vm.notice(runtime.message)
    }
    Scaffold(
        contentWindowInsets=WindowInsets(0,0,0,0),
        containerColor=MaterialTheme.colorScheme.background,
        snackbarHost={SnackbarHost(snack)},
        bottomBar={
            if(!focusedMap || page!=0) {
            Surface(shadowElevation=10.dp) {
                NavigationBar(containerColor=MaterialTheme.colorScheme.surface,tonalElevation=0.dp) {
                    listOf("地图" to AppIcons.Map,"收藏" to AppIcons.BookmarkBorder,"设置" to AppIcons.Tune).forEachIndexed { index,pair ->
                        NavigationBarItem(selected=page==index,onClick={page=index},icon={Icon(pair.second,pair.first)},label={Text(pair.first)})
                    }
                }
            }
            }
        }
    ){padding->
        if(page==0) {
            MapHome(vm,state,runtime,now,{search=true},{setup=true},{manual=true},onStart,
                Modifier.fillMaxSize().padding(bottom=padding.calculateBottomPadding()),
                focused=focusedMap,onFocusChange={focusedMap=it})
        } else {
            Column(Modifier.fillMaxSize().padding(bottom=padding.calculateBottomPadding()).statusBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal=24.dp,vertical=18.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
                    Column {Text(if(page==2)"设置" else "坐标",fontSize=27.sp,fontWeight=FontWeight.SemiBold);if(page!=2)Text("一个地点，一段路线",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)}
                    SimulationStatusChip(runtime,now)
                }
                when(page) {
                    1->RouteLibraryPage(state,
                        onPlaceSelect={if(vm.selectFavoritePlace(it)){page=0;focusedMap=false}},onPlaceRemove=vm::favorite,
                        onRouteSelect={if(vm.selectFavoriteRoute(it.id)){page=0;focusedMap=false}},
                        onRouteRename={route,name->vm.renameSavedRoute(route.id,name)},
                        onRouteRemove={vm.deleteSavedRoute(it.id)})
                    2->SettingsPage(vm,state,onPermission,onDeveloper,{setup=true})
                }
            }
        }
    }
    if(search)PlaceSearchSheet(vm,state,onClose={search=false},onSelect={vm.selectSearchResult(it);search=false})
    if(setup)MapPrivacyDialog(state.keyConfigured,state.mapEnabled,onConsent=vm::setMapConsent,onClose={setup=false})
    if(manual)CoordinateInput(onClose={manual=false},onSelect={vm.select(it);manual=false})
}

@Composable private fun SettingsPage(vm: AppViewModel,state: UiState,onPermission: ()->Unit,onDeveloper: ()->Unit,onSetup: ()->Unit) {
    val context=LocalContext.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        AppearanceSettingsCard(state.appearance,onTheme=vm::setTheme,onAccent=vm::setAccent,onReset=vm::resetAppearance)
        SettingCard("运行准备",AppIcons.VerifiedUser) {
            Readiness("模拟定位应用",state.mockAllowed,"在开发者选项中选择「坐标」",onDeveloper)
            Readiness("精确位置权限",state.preciseAllowed,"首页定位时申请，模拟需精确权限",onPermission)
            Text("服务从前台启动并显示常驻通知；首版不申请始终定位、电话、存储或悬浮窗权限。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        SettingCard("后台持续运行",AppIcons.Tune) {
            Text("部分一加 / OPPO 系统即使显示常驻通知，仍会暂停应用。请在本应用的耗电管理中允许后台活动或选择不限制；具体名称以系统为准。",style=MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick={runCatching {context.startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:${context.packageName}")))}.onFailure {vm.notice("请手动打开系统设置 → 应用 → 坐标 → 耗电管理")}}){Text("打开本应用系统设置")}
            Text("允许后台运行会增加耗电；无需开放始终定位、悬浮窗或无障碍权限。运行是否连续，请看地图页的最近写入时间与最长中断提示。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        SettingCard("隐私与边界",AppIcons.Shield) {
            Text("地点与收藏只存本机，无账号、无云同步。启用高德后，地图、搜索和路线请求由高德处理；开始模拟前由你手动授予系统权限。",style=MaterialTheme.typography.bodyMedium)
            Text("不修改 IP、计步或第三方应用，不隐藏系统模拟定位标记。正常停止会释放模拟定位源；其他应用的缓存可能需要刷新。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick=onSetup){Text("地图隐私与授权")}
        }
        Text("坐标 ${BuildConfig.VERSION_NAME} · 独立开发的本地测试版",Modifier.padding(vertical=12.dp),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable private fun SettingCard(title: String,icon: ImageVector,content: @Composable ColumnScope.()->Unit) {
    Surface(shape=MaterialTheme.shapes.large,color=MaterialTheme.colorScheme.surface){Column(Modifier.fillMaxWidth().padding(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){Row(verticalAlignment=Alignment.CenterVertically){Icon(icon,null,tint=MaterialTheme.colorScheme.primary);Text(title,Modifier.padding(start=10.dp),style=MaterialTheme.typography.titleMedium)};content()}}
}
@Composable private fun Readiness(title: String,ready: Boolean,hint: String,onClick: ()->Unit){
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(title);Text(if(ready)"已就绪" else hint,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)};TextButton(onClick=onClick){Text(if(ready)"查看" else "设置")}}
}

@Composable private fun CoordinateInput(onClose: ()->Unit,onSelect: (Place)->Unit) {
    var latitude by remember { mutableStateOf("") };var longitude by remember { mutableStateOf("") };var name by remember { mutableStateOf("") };var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest=onClose,title={Text("输入 WGS84 坐标")},text={Column(verticalArrangement=Arrangement.spacedBy(10.dp)){
        Text("Android 系统使用 WGS84；请不要直接粘贴高德 GCJ-02 或百度 BD-09 坐标。",style=MaterialTheme.typography.bodySmall)
        OutlinedTextField(name,{name=it},label={Text("地点名称（可选）")},singleLine=true)
        OutlinedTextField(latitude,{latitude=it},label={Text("纬度 -90 至 90")},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal))
        OutlinedTextField(longitude,{longitude=it},label={Text("经度 -180 至 180")},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal))
        error?.let {Text(it,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)}
    }},confirmButton={TextButton(onClick={runCatching {GeoPoint(latitude.toDouble(),longitude.toDouble())}.onSuccess {onSelect(Place(name.ifBlank {"自定义地点"},"",it,"手动输入 WGS84"))}.onFailure {error="请输入有效数字，并检查经纬度范围"}}){Text("使用此地点")}},dismissButton={TextButton(onClick=onClose){Text("取消")}})
}

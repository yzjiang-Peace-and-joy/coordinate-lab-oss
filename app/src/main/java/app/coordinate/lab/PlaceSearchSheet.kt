@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package app.coordinate.lab

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.coordinate.lab.core.Geo
import java.util.Locale

@Composable internal fun PlaceSearchSheet(vm: AppViewModel,state: UiState,onClose: ()->Unit,onSelect: (Place)->Unit) {
    var cityDialog by rememberSaveable {mutableStateOf(false)}
    val sheet=rememberModalBottomSheetState(skipPartiallyExpanded=true)
    val focus=remember {FocusRequester()}
    val keyboard=LocalSoftwareKeyboardController.current
    DisposableEffect(vm){vm.openSearch();onDispose {vm.closeSearch()}}
    LaunchedEffect(Unit){focus.requestFocus()}
    val submit: ()->Unit={keyboard?.hide();vm.search()}
    val choose: (Place)->Unit={keyboard?.hide();onSelect(it)}
    ModalBottomSheet(onDismissRequest=onClose,sheetState=sheet) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(.91f).imePadding().padding(horizontal=20.dp)) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Text(if(state.routeMode)"添加路线点" else "搜索地点",Modifier.weight(1f),style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.SemiBold)
                TextButton(onClick=onClose){Text("关闭")}
            }
            OutlinedTextField(
                value=state.searchText,onValueChange=vm::searchText,
                modifier=Modifier.fillMaxWidth().focusRequester(focus),singleLine=true,
                placeholder={Text("城市、地标、道路或完整地址")},
                leadingIcon={Icon(AppIcons.Search,null)},
                trailingIcon={Row {
                    if(state.searchText.isNotEmpty())IconButton(onClick={vm.searchText("")}){Icon(AppIcons.Close,"清除搜索内容")}
                    IconButton(onClick=submit,enabled=state.searchText.isNotBlank()){Icon(AppIcons.ArrowForward,"提交地点搜索")}
                }},
                keyboardOptions=KeyboardOptions(imeAction=ImeAction.Search),keyboardActions=KeyboardActions(onSearch={submit()})
            )
            Row(Modifier.fillMaxWidth().padding(top=8.dp),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically) {
                Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    listOf(SearchScope.SMART to "智能",SearchScope.CITY to "本城",SearchScope.NATIONWIDE to "全国").forEach {(scope,label)->
                        FilterChip(selected=state.searchScope==scope,onClick={
                            vm.searchScope(scope)
                            if(scope==SearchScope.CITY && state.city.isBlank())cityDialog=true
                            else if(state.searchText.isNotBlank()){keyboard?.hide();vm.search()}
                        },label={Text(label,maxLines=1)})
                    }
                }
                TextButton(onClick={cityDialog=true},contentPadding=PaddingValues(horizontal=8.dp)){
                    Text(state.city.ifBlank {"选择城市"},maxLines=1,overflow=TextOverflow.Ellipsis,modifier=Modifier.widthIn(max=96.dp))
                }
            }
            Text(when {
                !state.mapEnabled->"尚未启用高德，仅检索本机收藏与离线示例。"
                state.searchScope==SearchScope.SMART->if(state.routeMode)"以末个路线点为中心；选择搜索结果将追加到路线，不会开始模拟。" else "以所选点为中心，附近结果不足会扩大范围；全国回退会单独标注。"
                state.searchScope==SearchScope.CITY->"只检索 ${state.city.ifBlank {"指定城市"}}；跨城地点请选择全国。"
                else->"不限制当前城市，支持跨城地点和完整地址。"
            },style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(bottom=10.dp))
            if(state.searching || state.suggesting)LinearProgressIndicator(Modifier.fillMaxWidth())

            LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(bottom=24.dp)) {
                if(state.searching) {
                    item {SearchNote("正在搜索地点…","你可以继续修改关键词或关闭搜索。")}
                } else if(state.searchCompleted) {
                    if(state.searchError!=null) {
                        item {SearchNote("搜索未完成",state.searchError,error=true)}
                        item {TextButton(onClick=submit){Text("重试搜索")}}
                    } else {
                        item {SearchNote(if(state.searchResults.isEmpty())"没有匹配结果" else "已加载 ${state.searchResults.size} 个地点",state.searchDescription)}
                        if(state.suggestedCities.isNotEmpty())item {
                            Text("也可尝试以下城市",style=MaterialTheme.typography.labelMedium)
                            Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                state.suggestedCities.take(8).forEach {city->AssistChip(onClick={vm.city(city);vm.searchScope(SearchScope.CITY);vm.search()},label={Text(city)})}
                            }
                        }
                        items(state.searchResults,key={it.id+it.name}) {place->
                            SearchPlaceRow(place,state,choose)
                        }
                        if(state.searchResults.isEmpty()) {
                            item {Text("试试更具体的名称或完整地址；无结果不会显示成示例地点。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(vertical=12.dp))}
                            if(state.mapEnabled && state.searchScope!=SearchScope.NATIONWIDE)item {FilledTonalButton(onClick={keyboard?.hide();vm.searchNationwide()}){Text("改为全国搜索")}}
                        }
                        if(state.searchMoreError!=null)item {Text(state.searchMoreError,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall,modifier=Modifier.padding(top=10.dp))}
                        if(state.hasMore || state.loadingMore)item {
                            OutlinedButton(onClick=vm::loadMore,enabled=!state.loadingMore,modifier=Modifier.fillMaxWidth().padding(top=12.dp)) {
                                if(state.loadingMore){CircularProgressIndicator(Modifier.size(16.dp),strokeWidth=2.dp);Spacer(Modifier.width(8.dp))}
                                Text(when {state.loadingMore->"正在加载…";state.searchMoreError!=null->"重试加载更多";else->"加载更多地点"})
                            }
                        } else if(state.searchResults.isNotEmpty())item {Text("当前范围的结果已加载完",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(vertical=12.dp))}
                    }
                } else if(state.searchText.isNotBlank()) {
                    if(state.searchSuggestions.isNotEmpty()) {
                        item {Text("输入联想",style=MaterialTheme.typography.labelMedium,modifier=Modifier.padding(top=14.dp,bottom=4.dp))}
                        items(state.searchSuggestions,key={listOf(it.name,it.city,it.detail,it.point).joinToString("|")}) {suggestion->
                            SuggestionRow(suggestion){
                                if(suggestion.point!=null){
                                    vm.searchText(suggestion.name)
                                    choose(Place(suggestion.name,suggestion.city,suggestion.point,suggestion.detail))
                                } else {keyboard?.hide();vm.search(suggestion.name)}
                            }
                        }
                    }
                    item {SearchNote(if(state.suggesting)"正在获取联想…" else "按搜索查看完整结果","联想不是全部结果；没有联想时仍可提交关键词或完整地址。")}
                    if(!state.mapEnabled)item {FilledTonalButton(onClick=submit){Text("检索本机内容")}}
                } else {
                    if(state.recentSearches.isNotEmpty()) {
                        item {
                            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                                Text("最近搜索",Modifier.weight(1f),style=MaterialTheme.typography.titleSmall)
                                TextButton(onClick=vm::clearSearchHistory){Text("清空")}
                            }
                        }
                        items(state.recentSearches,key={"recent:$it"}) {term->
                            Row(Modifier.fillMaxWidth().clickable{keyboard?.hide();vm.search(term)}.padding(vertical=13.dp),verticalAlignment=Alignment.CenterVertically) {
                                Icon(AppIcons.Search,null,Modifier.size(18.dp),tint=MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(term,Modifier.weight(1f).padding(horizontal=12.dp),maxLines=1,overflow=TextOverflow.Ellipsis)
                                Icon(AppIcons.ArrowForward,null,Modifier.size(16.dp),tint=MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    } else item {SearchNote("想去哪里？","输入城市、景点、商场、道路或完整地址。结果点选只更新所选地点，不会自动开始模拟。")}
                    if(state.favorites.isNotEmpty()) {
                        item {Text("本机收藏",style=MaterialTheme.typography.titleSmall,modifier=Modifier.padding(top=16.dp))}
                        items(state.favorites.take(5),key={"favorite:${it.id}${it.name}"}) {place->SearchPlaceRow(place,state,choose)}
                    }
                    if(!state.mapEnabled) {
                        item {Text("离线示例城市（非搜索结果）",style=MaterialTheme.typography.titleSmall,modifier=Modifier.padding(top=16.dp))}
                        items(Examples.places,key={"offline:${it.id}"}) {place->SearchPlaceRow(place,state,choose)}
                    }
                }
            }
        }
    }
    if(cityDialog) {
        var value by remember {mutableStateOf(state.city)}
        AlertDialog(onDismissRequest={cityDialog=false},title={Text("选择搜索城市")},text={
            Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value,{value=it.take(50)},label={Text("如：成都、南京、苏州")},singleLine=true)
                Text("这里只设置检索范围，不修改地图选点或正在运行的模拟位置。",style=MaterialTheme.typography.bodySmall)
                Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {listOf("北京","上海","杭州","深圳").forEach {city->AssistChip(onClick={value=city},label={Text(city)})}}
            }
        },confirmButton={TextButton(onClick={
            if(value.isNotBlank()) {
                vm.city(value)
                if(value.trim() !in listOf("全国","全部","中国","中国大陆"))vm.searchScope(SearchScope.CITY)
                cityDialog=false
                if(state.searchText.isNotBlank()){keyboard?.hide();vm.search()}
            }
        },enabled=value.isNotBlank()){Text("确定")}},dismissButton={TextButton(onClick={cityDialog=false}){Text("取消")}})
    }
}

@Composable private fun SearchNote(title: String,description: String,error: Boolean=false) {
    Column(Modifier.fillMaxWidth().padding(vertical=16.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
        Text(title,style=MaterialTheme.typography.titleSmall,color=if(error)MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
        if(description.isNotBlank())Text(description,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable private fun SearchPlaceRow(place: Place,state: UiState,onSelect: (Place)->Unit) {
    Column(Modifier.fillMaxWidth().clickable{onSelect(place)}.padding(vertical=14.dp),verticalArrangement=Arrangement.spacedBy(5.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically) {
            Icon(AppIcons.LocationOn,null,Modifier.size(20.dp),tint=MaterialTheme.colorScheme.primary)
            Text(place.name,Modifier.weight(1f).padding(horizontal=10.dp),style=MaterialTheme.typography.titleMedium)
            Icon(AppIcons.ArrowForward,null,Modifier.size(17.dp),tint=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(listOf(place.city,place.detail).filter{it.isNotBlank()}.distinct().joinToString(" · "),Modifier.padding(start=30.dp),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        val center=if(state.routeMode)state.routeWaypoints.lastOrNull()?.point ?: state.selected.point else state.selected.point
        if(state.searchScope==SearchScope.SMART)Text("距${if(state.routeMode)"末个路线点" else "所选点"} ${searchDistance(Geo.distance(center,place.point))}",Modifier.padding(start=30.dp),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.primary)
    }
    HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
}

@Composable private fun SuggestionRow(suggestion: PlaceSuggestion,onClick: ()->Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick=onClick).padding(vertical=14.dp),verticalArrangement=Arrangement.spacedBy(5.dp)) {
        Text(suggestion.name,style=MaterialTheme.typography.titleMedium)
        Text(listOf(suggestion.city,suggestion.detail).filter{it.isNotBlank()}.distinct().joinToString(" · "),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        Text(if(suggestion.point==null)"输入建议 · 点击继续搜索具体地点" else "点击选择此地点",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.primary)
    }
    HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
}

private fun searchDistance(meters: Double)=if(meters<1000)"${meters.toInt()} m" else String.format(Locale.CHINA,"%.1f km",meters/1000)

package app.coordinate.lab

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/** User-facing privacy consent only: no developer credentials or application configuration. */
@Composable internal fun MapPrivacyDialog(
    available: Boolean, enabled: Boolean, onConsent: (Boolean)->Boolean, onClose: ()->Unit
) {
    val context=LocalContext.current
    AlertDialog(onDismissRequest=onClose,title={Text("地图隐私与授权")},text={
        Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text("地图、地点搜索和沿道路规划由高德提供。启用后，高德 SDK 可能处理设备与网络信息、搜索内容、所选地点和路线坐标，详情请阅读其隐私政策。",style=MaterialTheme.typography.bodyMedium)
            Text("不同意也可使用离线坐标、收藏及直线移动；不会自动开始模拟。你可以随时在设置的隐私入口停用地图联网服务。",style=MaterialTheme.typography.bodySmall)
            if(!available)Text("此安装包的地图服务暂不可用，请联系提供安装包的朋友；离线功能仍可使用。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.error)
            TextButton(onClick={context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("https://lbs.amap.com/pages/privacy/")))}){Text("阅读高德隐私政策")}
        }
    },confirmButton={
        TextButton(onClick={if(enabled || onConsent(true))onClose()},enabled=available){Text(if(enabled)"完成" else "同意并启用地图")}
    },dismissButton={
        TextButton(onClick={if(onConsent(false))onClose()}){Text(if(enabled)"停用地图服务" else "暂不启用")}
    })
}

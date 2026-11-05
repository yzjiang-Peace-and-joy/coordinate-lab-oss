# 坐标 / Coordinate Lab

一个以地图为中心的 Android 位置模拟工具。当前源码版本 **0.2.18**，采用 Kotlin、Jetpack Compose 与 Android 公开模拟位置接口，不需要 Root。

本仓库是独立整理的开源快照，不包含原私有仓库的提交历史、个人验收记录、用户地点、密钥或签名文件。

## 功能

- 地图选点、城市/地点搜索、坐标输入与单点位置模拟。
- 多点选线、自定义移动速度、路线预览、直线/沿道路步行规划，以及连接终点与起点的闭环选项。
- 收藏地点和命名路线；选择地点自动切换单点模式，选择路线切换多点并预览，启动由用户主动确认。
- 全屏地图工作区、手绘路线收藏与完整轨迹载入；单条轨迹最多 10,000 点。
- 地图图层、路况、浅色/深色/跟随系统主题、强调色自定义；深色主题默认联动夜景图层。
- 前台服务、运行计时与定位写入健康状态。应用不自动开机运行，也不自动恢复被系统终止的模拟会话。

## 使用边界

供位置功能开发、地图交互验证等经授权的测试使用。不修改 IP、计步或其他应用，也不隐藏 Android 的模拟位置标记。系统成功写入模拟位置不保证任何第三方应用都会采用；请勿用于伪造运动成绩、考勤、权益或其他欺骗用途。

直线模式不避障；沿道路规划依赖高德服务，失败不会被静默替换为直线。闭环只表示最后连接回起点，不是无限循环；当前路线到站后保持在终点。

## 环境

- Android 10 / API 29 及以上；目标与编译 API 36。
- JDK 17 或 21、Android SDK Platform 36、Gradle 8.13。
- Android Gradle Plugin 8.13.2、Kotlin 2.2.21。
- 包名：`app.coordinate.lab`。

## 从源码构建

```powershell
git clone https://github.com/yzjiang-Peace-and-joy/coordinate-lab-oss.git
cd coordinate-lab-oss
```

设置 `JAVA_HOME` 指向 JDK，设置 `ANDROID_HOME` 指向 Android SDK；也可通过 Android Studio 生成本机的 `local.properties`。这些路径不应提交到仓库。

Windows PowerShell：

```powershell
.\build.ps1
```

也可直接使用 Gradle Wrapper（macOS/Linux 使用 `./gradlew`）：

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug lintDebug
```

产物：`app/build/outputs/apk/debug/app-debug.apk`。构建脚本会在本机 `.local/` 创建独立的调试签名，缓存与临时文件保留在项目目录。直接使用 Wrapper/Android Studio 时，若没有本机 `.local/debug.keystore`，则使用 Android 默认调试签名。

**新生成的签名不能覆盖使用不同签名的旧安装。** 本仓库不提供维护者的私钥或正式发行签名；请勿为了安装测试版而卸载含重要数据的旧版。

## 地图服务配置

开源源码不内置维护者的高德 Key。没有自己的配置时仍可构建并使用离线坐标与直线路线功能，真实地图、在线搜索与道路规划不可用。

1. 在[高德开放平台](https://lbs.amap.com/)创建自己的 **Android 平台** Key，绑定包名和实际签名证书的 SHA1。
2. 复制 `amap.properties.example` 为 `amap.local.properties`，填写 `AMAP_ANDROID_KEY` 后重新构建。该文件已被 Git 忽略。
3. 在应用首页启用地图时阅读隐私说明并明确同意。预置客户端配置不代表隐私授权；设置 → 隐私与边界 → 地图隐私与授权中可撤回同意。

0.2.18 界面不提供 Key 编辑、包名或 SHA1 的开发者配置卡片。Key 会进入构建后的 APK，不能依赖隐藏界面或 `.gitignore` 对其保密；应在高德控制台限制平台、包名与签名，遵守其配额和服务条款。

## 安装后准备

1. 在自己的 Android 测试设备上安装自行构建的 APK；按系统提示允许本次安装来源，无需关闭系统安全扫描。
2. 在系统开发者选项中，将“选择模拟位置信息应用”设为“坐标”。
3. 授予所需精确位置权限；通知、后台活动设置按系统提示处理。部分厂商系统需要在本应用耗电管理中允许后台运行。
4. 选点或载入路线，确认预览后主动开始。运行按钮虽然使用双竖线图形，当前点击含义是**结束本次模拟并恢复真实定位**，不是暂停后继续。

正常停止会释放本应用模拟位置源。其他应用可能缓存旧位置；异常退出、系统强杀或设备重启后的表现仍需按机型验证。

## 数据与隐私

收藏、路线草稿和外观设置保存在本机，没有账号或云同步。启用高德后，地图、搜索和路线服务会按其政策处理相关设备/网络信息及查询坐标，参见[高德隐私政策](https://lbs.amap.com/pages/privacy/)。

不要提交真实 Key、签名、用户数据或含私人地点的截图。提交问题时请使用虚构测试地点，隐藏个人信息。第三方 SDK 及地图服务不因本项目开源而改变其条款。

## 测试与维护

测试命令、已覆盖范围和待验证事项见 [TESTING.md](TESTING.md)。当前代码包含 208 项 JVM 测试；构建与单元测试不等于所有设备和第三方应用均兼容。

欢迎通过 Issues 提供机型、Android 版本、源码版本、复现步骤、预期与实际结果。不要公开安全凭据或个人轨迹。

## 许可证

原创源码与本仓库原创文档按 [MIT](LICENSE) 许可证开放。第三方组件及 Gradle Wrapper 保留各自许可证；高德 SDK/服务不在本项目 MIT 再授权范围内，参见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。

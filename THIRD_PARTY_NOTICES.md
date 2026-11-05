# 第三方组件说明

本项目的 MIT 许可证只覆盖本项目有权授权的原创内容，不替代依赖或地图服务本身的许可证、隐私政策、商标和数据使用条款。依赖通过构建工具解析；本仓库不附带第三方 Android SDK 二进制或地图瓦片。

| 组件 | 使用范围 | 许可证/条款来源 |
| --- | --- | --- |
| AndroidX / Jetpack Compose | Android 界面、生命周期与 Activity | 上游组件许可证，主要为 Apache-2.0；参见 [AndroidX](https://android.googlesource.com/platform/frameworks/support/) 与各构件 POM |
| Kotlin / kotlinx.coroutines | 语言插件及协程运行时 | [Kotlin](https://github.com/JetBrains/kotlin/blob/master/license/LICENSE.txt)、[kotlinx.coroutines](https://github.com/Kotlin/kotlinx.coroutines/blob/master/LICENSE.txt)，Apache-2.0 |
| JUnit 4.13.2 | JVM 测试，不作为应用业务代码发布 | [JUnit 4](https://github.com/junit-team/junit4/blob/main/LICENSE-junit.txt)，EPL-1.0 |
| Gradle Wrapper 8.13 | 构建引导脚本与 Wrapper JAR | [Gradle](https://github.com/gradle/gradle/blob/v8.13.0/LICENSE)，Apache-2.0；生成文件保留上游版权头 |
| 高德 Android 地图/定位/搜索合包 | 地图、地点搜索与道路规划 | 版本 `11.3.100_loc11.3.101_sea9.8.1`；受[高德开放平台](https://lbs.amap.com/)独立 SDK/服务协议与[隐私政策](https://lbs.amap.com/pages/privacy/)约束，不由本项目按 MIT 重新授权 |

准确依赖版本见 `app/build.gradle.kts`；传递依赖可能具有各自声明。制作或分发 APK 时，应审查所有实际打包组件及其通知要求。开放应用源码不等于取得地图数据、商标或其他第三方内容的再分发权。

Gradle Wrapper 的许可证与上游通知保存在 `licenses/gradle/LICENSE` 和 `licenses/gradle/NOTICE`，按上游 Gradle 发行包原样保留，含 Apache-2.0 与其中的第三方声明。

# 骰子盘

一个使用 Jetpack Compose、WebView 和 Three.js 物理渲染实现的 Android 3D 掷骰 App。骰子会在桌面上产生重力、边界以及骰子之间的真实碰撞效果，并为手机和平板提供自适应布局。

[繁體中文](README.md) · [日本語](README.ja.md) · [English](README.en.md)

## 功能

- 支持 D4、D6、D8、D10、D12、D20。
- D6 使用点数显示，其余骰型使用面值显示。
- 一次可掷 1～10 颗骰子。
- 点击骰盘或“掷骰”按钮即可开始，并支持清空场景。
- 使用 Cannon 物理碰撞：骰子之间、骰子与桌面及四周边界都会碰撞。
- 使用设备重力传感器自动校正桌面水平投影，背景与骰子使用同一套姿态基准。
- 可控制手机倾斜／摇动物理效果、震动、动态轨迹、结果卡与骰子颜色。
- 灵敏度范围 0.5x～3.0x，默认 2.2x。
- 支持繁体中文、简体中文、繁体中文（香港）、English、日本語；可以跟随系统语言，也可以在设置中手动选择。
- 手机、平板和宽屏使用自适应布局，减少平板左右留白。

## 真机画面

<p align="center">
  <img src="screenshots/home.png" alt="平板自适应主画面" width="49%" />
  <img src="screenshots/physical-roll.png" alt="3D 物理掷骰结果" width="49%" />
</p>
<p align="center">
  <img src="screenshots/settings.png" alt="掷骰设置页" width="49%" />
  <img src="screenshots/languages.png" alt="多语言菜单" width="49%" />
</p>

以上截图取自实体 Android 平板，展示自适应布局、Cannon 物理碰撞、设置面板，以及系统／手动语言切换入口。

## 技术架构

```text
Compose UI
  └─ DiceRollerViewModel + StateFlow
       ├─ DiceSettingsRepository + Preferences DataStore
       └─ DiceWebScene（WebView）
            ├─ WebViewAssetLoader（离线加载本地资源）
            ├─ JavaScript bridge（掷骰、清除、姿态与事件）
            └─ Three.js + dice-box-threejs + Cannon.js
```

- Kotlin 负责设置、随机结果、状态、传感器生命周期和 Compose UI。
- WebView 只加载 APK 内 `app/src/main/assets/dice/` 的本地资源，不依赖网络。
- 物理骰子由 `dice-box-threejs` 与 Cannon.js 管理；Android 传感器数据会经过显示方向校正和低通滤波后送入场景。
- Release 构建启用 R8 和资源压缩，同时保留 JavaScript bridge 的 `@JavascriptInterface` 方法。

## 构建要求

- JDK 17。
- Android SDK 35。
- Android API 26（Android 8.0）或更高版本的设备或模拟器。
- 项目提供 Gradle Wrapper，不需要预先安装 Gradle。

## 构建与测试

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
./gradlew :app:assembleRelease
```

Debug APK 位于 `app/build/outputs/apk/debug/app-debug.apk`。安装到已连接的设备：

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## 传感器与 AR 限制

自动桌面校正使用重力传感器和加速度计，因此可以让虚拟桌面相对于现实重力保持水平。这不是 ARCore 平面追踪：目前不会追踪相机位置、真实桌面深度、遮挡或完整的世界坐标视差。若未来需要把骰子固定在真实桌面上，需要加入 ARCore 或其他 AR 平面追踪方案。

## 隐私与权限

目前只使用震动权限；传感器数据只在 App 运行期间用于本地物理和视角计算，不上传、不创建账号，也不需要相机或网络权限。设置保存在 Android Preferences DataStore 与本地 SharedPreferences 中。

## 许可证

本项目原创代码使用 [MIT License](LICENSE)。Three.js、Cannon.js、dice-box-threejs 以及其他 bundled 资源仍受各自原始许可证和版权声明约束；重新发布时请保留资源中的 license header 并遵守上游项目条款。


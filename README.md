# Dice Tray

一個以 Jetpack Compose、WebView 與 Three.js 物理渲染打造的 Android 3D 擲骰子 App。骰子會在檯面上產生真實的重力、牆面與骰子之間的碰撞效果，並針對手機與平板提供適應性版面。

[簡體中文](README.zh-CN.md) · [日本語](README.ja.md) · [English](README.en.md)

## 功能

- D4、D6、D8、D10、D12、D20。
- D6 使用點數呈現，其餘骰型使用面值呈現。
- 一次擲 1～10 顆骰子。
- 點擊骰盤或「擲骰」按鈕即可擲骰；支援清除場景。
- Cannon 物理碰撞：骰子彼此、檯面與四周邊界都會產生碰撞。
- 裝置重力感測器自動校正檯面水平投影，底圖與骰子使用同一套姿態基準。
- 可選擇手機傾斜／搖動物理效果、震動、動態軌跡、結果卡與骰子顏色。
- 靈敏度範圍 0.5x～3.0x，預設 2.2x。
- 繁體中文、簡體中文、繁體中文（香港）、English、日本語；可跟隨系統語言，也可在設定中手動指定。
- 手機、平板與寬螢幕使用不同版面比例，減少平板左右留白。

## 真機畫面

<p align="center">
  <img src="screenshots/home.png" alt="平板自適應主畫面" width="49%" />
  <img src="screenshots/physical-roll.png" alt="3D 物理擲骰結果" width="49%" />
</p>
<p align="center">
  <img src="screenshots/settings.png" alt="擲骰設定頁" width="49%" />
  <img src="screenshots/languages.png" alt="多國語言選單" width="49%" />
</p>

以上截圖取自實體 Android 平板，展示自適應版面、Cannon 物理碰撞、設定面板，以及系統／手動語言切換入口。

## 技術架構

```text
Compose UI
  └─ DiceRollerViewModel + StateFlow
       ├─ DiceSettingsRepository + Preferences DataStore
       └─ DiceWebScene (WebView)
            ├─ WebViewAssetLoader（離線載入本地資源）
            ├─ JavaScript bridge（擲骰、清除、姿態與事件）
            └─ Three.js + dice-box-threejs + Cannon.js
```

- Kotlin 負責設定、隨機結果、狀態、感測器生命週期與 Compose UI。
- WebView 只載入 APK 內的 `app/src/main/assets/dice/` 資源，不依賴網路。
- 物理骰子由 `dice-box-threejs` 與 Cannon.js 管理；Android 感測器資料經過顯示方向校正與低通濾波後才送進場景。
- Release build 啟用 R8/資源壓縮，JavaScript bridge 的 `@JavascriptInterface` 方法會保留。

## 建置需求

- JDK 17。
- Android SDK 35。
- Android API 26（Android 8.0）以上的裝置或模擬器。
- 專案提供 Gradle Wrapper，不需要預先安裝 Gradle。

## 建置與測試

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
./gradlew :app:assembleRelease
```

Debug APK 會輸出至 `app/build/outputs/apk/debug/app-debug.apk`。安裝到已連線裝置：

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## 感測器與 AR 限制

自動檯面校正使用重力感測器與加速度計，因此能讓虛擬檯面相對於現實重力維持水平。這不是 ARCore 平面追蹤：目前不追蹤相機位置、真實桌面深度、遮擋或完整的世界座標視差。若未來要把骰子固定在真實桌面上，需要另行加入 ARCore 或其他 AR 平面追蹤方案。

## 隱私與權限

目前只使用震動權限；感測器資料只在 App 執行期間用於本地物理與視角計算，不上傳、不建立帳號，也不要求相機或網路權限。設定儲存在 Android Preferences DataStore 與本地 SharedPreferences。

## 授權

本專案原始程式碼採用 [MIT License](LICENSE)。Three.js、Cannon.js、dice-box-threejs 及其他 bundled 資產仍受其各自的原始授權與著作權聲明約束；請保留資產內的 license header，並在重新發布時遵守上游專案條款。


# Dice Tray

Jetpack Compose、WebView、Three.js の物理レンダリングで作った Android 3D ダイスアプリです。ダイスは台の上で重力、壁、ほかのダイスとの衝突を伴って転がり、スマートフォンとタブレットの画面サイズに合わせてレイアウトが変わります。

[繁體中文](README.md) · [简体中文](README.zh-CN.md) · [English](README.en.md)

## 機能

- D4、D6、D8、D10、D12、D20 に対応。
- D6 は目の点、その他のダイスは数字で表示。
- 1～10 個のダイスを同時に振ることが可能。
- ダイス台または「振る」ボタンをタップして実行。シーンのクリアにも対応。
- Cannon 物理演算によるダイス同士、台、周囲の壁との衝突。
- 重力センサーで端末の姿勢を検出し、背景とダイスの投影を同じ水平基準に自動調整。
- スマートフォンの傾き／振動、バイブレーション、モーション軌跡、結果カード、ダイス色を設定可能。
- 感度は 0.5x～3.0x、初期値は 2.2x。
- 繁体字中国語、簡体字中国語、香港繁体字中国語、English、日本語。システム言語に従うことも手動選択も可能。
- スマートフォン、タブレット、ワイド画面向けのレスポンシブレイアウト。

## 実機スクリーンショット

<p align="center">
  <img src="screenshots/home.png" alt="タブレット向けレスポンシブなホーム画面" width="49%" />
  <img src="screenshots/physical-roll.png" alt="3D 物理ダイスの結果" width="49%" />
</p>
<p align="center">
  <img src="screenshots/settings.png" alt="ダイス設定画面" width="49%" />
  <img src="screenshots/languages.png" alt="多言語メニュー" width="49%" />
</p>

実際の Android タブレットで撮影した画面です。レスポンシブレイアウト、Cannon の物理衝突、設定画面、システム言語／手動言語切り替えを確認できます。

## 技術構成

```text
Compose UI
  └─ DiceRollerViewModel + StateFlow
       ├─ DiceSettingsRepository + Preferences DataStore
       └─ DiceWebScene（WebView）
            ├─ WebViewAssetLoader（ローカル資産をオフラインで読み込み）
            ├─ JavaScript bridge（振る、クリア、姿勢、イベント）
            └─ Three.js + dice-box-threejs + Cannon.js
```

- Kotlin は設定、乱数結果、状態、センサーのライフサイクル、Compose UI を担当します。
- WebView は APK 内の `app/src/main/assets/dice/` だけを読み込み、ネットワークに依存しません。
- 物理演算は `dice-box-threejs` と Cannon.js が担当し、センサー値は表示方向の補正とローパスフィルターを通してシーンへ渡します。
- Release ビルドでは R8 とリソース圧縮を有効にし、JavaScript bridge の `@JavascriptInterface` メソッドを保持します。

## ビルド要件

- JDK 17。
- Android SDK 35。
- Android API 26（Android 8.0）以上の端末またはエミュレーター。
- Gradle Wrapper を同梱しているため、Gradle の別途インストールは不要です。

## ビルドとテスト

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
./gradlew :app:assembleRelease
```

Debug APK は `app/build/outputs/apk/debug/app-debug.apk` に生成されます。

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## センサーと AR の制限

自動水平補正は重力センサーと加速度計を利用するため、仮想の台を現実の重力に対して水平に保てます。ただし ARCore の平面トラッキングではありません。現在はカメラ位置、実際の机の深度、遮蔽、完全なワールド座標の視差を追跡しません。実際の机にダイスを固定する AR 表現には ARCore などの導入が必要です。

## プライバシーと権限

使用する権限はバイブレーションのみです。センサー値はアプリ実行中のローカルな物理演算と視点補正にだけ使い、アップロード、アカウント作成、カメラ、ネットワーク通信は行いません。設定は Android Preferences DataStore とローカル SharedPreferences に保存されます。

## ライセンス

本プロジェクトのオリジナルコードは [MIT License](LICENSE) です。Three.js、Cannon.js、dice-box-threejs などの bundled 資産は各 upstream のライセンスと著作権表示に従います。再配布時は資産内の license header を保持してください。


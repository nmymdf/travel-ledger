# 卡溜趴(travel-ledger)— 給 Claude 的專案說明

每次開工先讀這份。細節在 `docs/` 裡,這裡只講「誰做什麼、規則、去哪裡找」。

## 專案與分工

卡溜趴是一個離線優先的旅遊記帳+行程 App,一家人自用。三個版本用**同一種 .zip 檔**互傳(分享旅程、同伴補充、備份、電腦排行程)。

| 版本 | 負責人 | 位置 |
|---|---|---|
| Android(Kotlin、Compose、Room) | **爸爸**(repo 擁有者 nmymdf)與他的 Claude | 本 repo `app/`,分支 `claude/confident-volta-6rsegv` |
| 電腦行程桌(單一 HTML,只有行程) | 爸爸與他的 Claude | 本 repo `desktop/` |
| iPhone / iPad(SwiftUI、GRDB、ZIPFoundation) | **Jay(兒子)**與他的 Claude | Jay 的 fork `jaykuo0619/travel-ledger` 的 `ios` 分支,`ios/` 資料夾 |

- **Android 這邊的 Claude 不改 `ios/`**。iPhone 要跟著改的事,寫進 `docs/IOS_PORT.md`(規格)和 `docs/IOS_TODO.md`(待辦清單)並 push,Jay 有空時照著做。
- **Jay 那邊的 Claude 不改 `app/`、`desktop/`、`compat/fixtures/`**。iPhone 匯出的相容檔放 `compat/from-ios/`。
- **檔案格式(`docs/IOS_PORT.md` 第 4 節)三邊共用**。任何一邊要改格式、改 `compat/fixtures/`,先停下來問使用者,並通知另一邊;能不改就不改。

## 跟使用者溝通

- 一律用**繁體中文**,用一般人聽得懂的話,不要堆術語。
- 使用者常說「先討論」「先回答」「不動手」:這時**只說明和提方案,不改程式**;等他說「好」才動手。
- 提方案時給建議,不要列一大堆選項;牽涉畫面的改動,可以先做預覽截圖給他看(例如計算機)。
- 回報時說清楚:做了什麼、怎麼驗證的、哪些在電腦上測不到要他在手機上試。

## 一定要遵守

- **repo 是公開的**:金鑰、`.p12`、keystore、密碼、token 都不能 commit,也不要請使用者把它們貼進對話。Android 正式簽章用環境變數 `TRAVEL_LEDGER_KEYSTORE_B64` 或 `local.properties` 的 `travelLedger.keystore`;簽章指紋必須是 `661000B5…30F4`(build 會檢查)。
- 沒有被要求就**不要開 PR**。commit 訊息不放模型名稱。
- 使用者明確要求過的介面規則:**任何畫面都不能出現直排字**(窄欄把中文擠成一字一行)、大字體下不能被切字;字不縮小,靠減少留白;按鈕隨字體長高。`NoVerticalTextTest` 會檢查。
- 改了使用者看得到的行為,要同步更新:`docs/SPEC.md`(版本紀錄)、`docs/manual/manual.html`(說明書,含版本表)並重產 PDF、`docs/TESTING.md`;**會影響 iPhone 的,也要更新 `docs/IOS_PORT.md` 和 `docs/IOS_TODO.md`**。

## 文件地圖

| 檔案 | 內容 |
|---|---|
| `docs/SPEC.md` | 規格與每一版做了什麼(第 11 節以後是版本紀錄) |
| `docs/TESTING.md` | 測試怎麼跑、每個測試在驗什麼 |
| `docs/IOS_PORT.md` | iPhone 版交接規格:資料模型、檔案格式、逐畫面細節(第 11 節)、Android 已知小問題(第 12 節) |
| `docs/IOS_TODO.md` | iPhone 版還要跟著 Android 改的項目(勾選清單) |
| `docs/iPhone版_給家人的步驟.md` | Jay 第一次上手的操作步驟 |
| `compat/README.md` | 三邊互傳的相容測試檔說明 |
| `desktop/README.md` | 電腦行程桌的結構 |
| `docs/manual/` | 使用說明書(HTML → PDF) |
| `docs/打包APK指南.md` | 通用的 Android 打包步驟(簽章金鑰、環境、安裝),給使用者的其他 App 專案參考 |

## 常用指令(Android / 電腦行程桌)

```
./gradlew :app:testDebugUnitTest --offline        # 全部測試(Robolectric,不用模擬器)
./gradlew :app:assembleRelease --offline          # 正式版 APK(需要簽章金鑰)
./gradlew :app:recordPaparazziDebug --tests '*ScreenSnapshots*'   # 重產畫面截圖
node desktop/build.mjs                            # 產生 desktop/dist/卡溜趴行程桌.html
PLAYWRIGHT_DIR=/opt/node-tools/node_modules node desktop/test/e2e.mjs   # 電腦行程桌實際操作測試
PLAYWRIGHT_DIR=/opt/node-tools/node_modules node docs/manual/build.mjs  # 說明書 PDF
```

出新版:`app/build.gradle.kts` 的 `versionCode`+1、`versionName`;說明書 `build.mjs` 與 `manual.html` 的版本號;版本表加一行。

## 目前狀態(更新時改這裡)

- Android **v0.10.6**、電腦行程桌 **v1.4**、iPhone **1.0.0**(送審中)。
- iPhone 待辦見 `docs/IOS_TODO.md`(其中「非西曆日期錯誤」建議送審前修)。
- 還沒決定:帳本卡與統計頁「日均」的算法要不要統一。
- 之前提過、使用者還沒說要做的 Android 功能:自動帶入參考匯率、行程列左右滑、收據自動偵測多種文字、畫布(圈收據金額、手寫便條)、長按圖示「記一筆」、旅行手帳頁、手機版刪除的復原。

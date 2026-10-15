# 打包 Android APK 指南(給其他專案的 Claude)

> 這是卡溜趴實際在用的做法,整理成任何 Android App 都能照做的步驟。使用者是一般人,請用繁體中文、白話說明,每一步做了什麼、要他做什麼(例如在設定頁貼金鑰)講清楚。
> 參考實作:本 repo 的 `app/build.gradle.kts`(簽章與指紋檢查)、`docs/android-env-setup.sh`(雲端環境安裝 Android SDK)。

## 0. 先弄清楚三件事

1. App 的**套件名稱**(`applicationId`,例如 `com.archiekuo.travelledger`)。一旦給人安裝,就**不要再改**,改了手機會當成另一個 App。
2. 這是**第一次給人安裝**,還是已經有人裝了舊版?
   - 已經有人裝了:新版必須用**同一把簽章金鑰**,否則手機會拒絕更新(只能移除重裝、資料會不見)。先找出舊版用的金鑰。
   - 第一次:現在建立一把正式金鑰(第 3 節),之後永遠用它。
3. 專案能不能編譯:先跑 `./gradlew :app:assembleDebug`。

## 1. 雲端環境(Claude Code 網頁版的容器)

- 環境的**網路**要允許:`dl.google.com`、`maven.google.com`、`repo.maven.apache.org`(以及 `plugins.gradle.org`)。
- 把 `docs/android-env-setup.sh` 的內容放進環境的 **setup script**(每次開容器自動裝 Android SDK 35 與 build-tools)。專案的 `local.properties` 寫 `sdk.dir=/opt/android-sdk`(這個檔不要 commit)。
- 需要時用 `read_documentation` 查「environment.setup_script / environment.network / environment.secrets」的最新設定方式,並請使用者到設定頁操作。

## 2. Debug 版與正式版的差別

| | Debug(`assembleDebug`) | 正式版(`assembleRelease`) |
|---|---|---|
| 簽章 | 每台電腦/容器自動產生的臨時金鑰 | 自己保管的正式金鑰 |
| 能不能更新 | 換了容器就換金鑰,**之後無法覆蓋安裝** | 永遠同一把,可以一直覆蓋更新 |
| 用途 | 自己試 | **要給人用的一律用這個** |

## 3. 建立正式簽章金鑰(第一次才做)

```
keytool -genkeypair -v -keystore release.keystore -alias release \
  -keyalg RSA -keysize 4096 -validity 36500 \
  -storepass <密碼> -keypass <密碼> -dname "CN=<作者名>"
base64 -w0 release.keystore > release.keystore.b64
```

- **金鑰檔、密碼絕對不能 commit**(repo 可能是公開的),也不要叫使用者把它們貼到對話裡。
- 請使用者把 `release.keystore.b64` 的內容存成環境的**機密環境變數**(例如 `<APP>_KEYSTORE_B64`;密碼另存 `<APP>_KEYSTORE_PASSWORD`),並**另外備份金鑰檔**(雲端硬碟等)。金鑰遺失 = 已安裝的人永遠無法更新。
- 記下指紋,寫進 build 檔做檢查:`keytool -list -v -keystore release.keystore` 的 SHA-256。

## 4. build.gradle.kts 的簽章設定(照卡溜趴的寫法)

重點:金鑰從環境變數(base64)或 `local.properties` 讀;打包前**檢查指紋**,不對就停止,避免做出別人手機無法更新的 APK。完整範例看本 repo `app/build.gradle.kts` 開頭與結尾(`releaseKeystore`、`keystoreFingerprint`、`assembleRelease` 的 `doFirst` 檢查),把 `TRAVEL_LEDGER_*` 換成新 App 的名字、指紋換成新金鑰的。

`.gitignore` 至少要有:`/local.properties`、`*.keystore`、`*.jks`、`*.b64`、`build/`。

## 5. 打包與驗證

```
./gradlew :app:testDebugUnitTest      # 有測試就先跑
./gradlew :app:assembleRelease
$ANDROID_HOME/build-tools/35.0.0/apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk
```

- 確認印出的 SHA-256 等於記下的指紋。
- 每出一版:`versionCode` +1(必須比上一版大,否則手機不讓更新)、`versionName` 改成新版號。
- 把 APK 複製成好認的檔名(例如 `<App名稱>-v1.0.1.apk`),用 SendUserFile 傳給使用者。

## 6. 告訴使用者怎麼安裝

1. 把 APK 傳到手機(LINE 傳給自己、Google 雲端硬碟都可以),點開。
2. 第一次會要求「允許安裝不明來源的應用程式」,允許那個 App(例如 LINE 或檔案管理)。
3. 之後出新版,直接點新 APK 覆蓋安裝,**資料會保留**;前提是簽章相同、`versionCode` 比較大。
4. 不要先移除舊版再裝(移除會刪掉資料)。

## 7. 常見問題

- **「應用程式未安裝」/「與現有套件衝突」**:簽章不同(例如之前裝的是 debug 版)。只能移除重裝,資料會不見;所以一開始就用正式金鑰。
- **Gradle 下載失敗(429、逾時)**:重試;確認網路允許清單。
- **容器重開後金鑰不見**:正常,金鑰應該在環境變數裡,build 時自動還原。

# 卡溜趴 iPhone 版:移植交接文件

> 給在 Mac 上做 iPhone 版的 Claude Code 讀。人看的操作步驟在 `docs/iPhone版_給家人的步驟.md`。
> 與使用者溝通一律使用**繁體中文**;程式碼與註解可用英文。

## 0. 先讀這裡

- **目標**:做出和 Android 版功能一致的 iPhone app,並且**能和 Android 版互傳檔案**(分享旅程、同伴補充、備份還原、電腦行程桌的檔案)。檔案相容是第一優先,畫面可以照 iOS 的習慣做。
- **Android 版是規格的來源**。行為有疑問時,以 `app/src/main/java/com/archiekuo/travelledger/` 的程式為準,其次是 `docs/SPEC.md`、使用說明書 `docs/manual/manual.html`(也有 PDF)。
- **程式放在 `ios/`**(Xcode 專案)。不要改 `app/`、`desktop/`、`compat/fixtures/`;檔案格式需要改時先停下來問,因為 Android 和桌面網頁也要一起改。
- **這個 repo 是公開的**:不可 commit 任何憑證、`.p12`、描述檔、App Store Connect 金鑰、密碼。簽章用 Xcode 自動簽章(憑證留在 Mac 的鑰匙圈)。不要叫使用者把密碼或金鑰貼到對話裡。
- 每完成一個階段(第 8 節)就要能編譯、在模擬器跑、測試通過,再 commit。commit 訊息不要放模型名稱。
- 使用者是第一次做 iPhone app:每一步做之前用一兩句話說要做什麼,需要他按 Xcode 或手機時,寫清楚點哪裡。

## 1. App 是什麼

個人自用的旅遊記帳+行程 App,離線優先、不需要網路、不做雲端同步。一個人(「記帳人」)記帳與排行程,用 LINE 傳 .zip 檔給同伴看;同伴可以補充項目再傳回記帳人審核。

主要功能(細節見 `docs/SPEC.md` 第 11~13.7 節與使用說明書):

| 區塊 | 功能 |
|---|---|
| 旅程 | 新增/編輯(名稱、日期、成員、外幣匯率、預算、分帳開關、封面、導航軟體)、封存、刪除;首頁有進行中旅程(含 30 天內出發)直接開該旅程 |
| 記一筆 | 名稱(提示用過的名稱並帶分類)→ 分類(一行橫滑)→ 付款方式 → 大計算機輸入金額;幣別在計算機上切換;匯率小字可改;日期時間預設現在;最多 3 張照片 |
| 照片與收據 | 拍收據(文字辨識、自動填店家/金額/幣別/日期時間,不存相簿)、拍照(存相簿)、相簿(自動判斷收據或回憶);辨識文字存起來供搜尋 |
| 帳本與統計 | 依日期分組明細、分類篩選、總花費/日均/人均、預算進度、分類/日期/付款方式統計、搜尋 |
| 行程 | 每天的行程與「待排」、依天/依類型檢視、狀態(未去/已去/略過)、訂位(不需/要訂/已訂)與訂位資訊、地點、筆記、截圖(最多 9 張);點行程先看內容,右上角編輯/刪除;一鍵「為這個行程記一筆」;導航 |
| 文字處理 | 貼上多行自動拆成多個行程(時間、分類)、把一則筆記拆成多個行程(原筆記移到當日筆記,可復原)、網址與電話可點 |
| 當日筆記 | 每天一則,待排也有一則 |
| 分享與交換 | 分享旅程給同伴(唯讀副本)、同伴補充 → 記帳人審核、全部備份/還原、和電腦行程桌互傳 |

## 2. 技術建議(iOS)

| 用途 | Android 用的 | iOS 建議 |
|---|---|---|
| 介面 | Jetpack Compose | SwiftUI,最低 iOS 17 |
| 資料庫 | Room(SQLite) | SwiftData,或 GRDB(SQLite,較好掌控);兩者擇一,第 3 節的欄位照抄 |
| zip | java.util.zip | [ZIPFoundation](https://github.com/weichsel/ZIPFoundation)(Swift Package) |
| 收據文字辨識 | ML Kit(中日韓英) | Vision `VNRecognizeTextRequest`,語言 `zh-Hant`、`zh-Hans`、`ja`、`ko`、`en` |
| 拍照/相簿 | 系統相機、相簿選取 | `UIImagePickerController`(相機)、`PhotosPicker`;存相簿用 `PHPhotoLibrary`(只要「加入」權限) |
| 傳檔給 LINE | 分享 Intent + FileProvider | `ShareLink` 或 `UIActivityViewController`,分享 .zip 檔 |
| 從 LINE 開檔 | VIEW/SEND intent-filter(application/zip) | `CFBundleDocumentTypes` 宣告 `com.pkware.zip-archive` + `.onOpenURL`;另提供「從檔案匯入」(`fileImporter`) |
| 從地圖 App 分享地點 | SEND text/plain | Share Extension(之後再做,非必要) |
| 導航 | Intent | 見第 6 節 |

bundle id 建議 `com.archiekuo.travelledger`。App 顯示名稱「卡溜趴」。

## 3. 資料模型

來源:`data/Entities.kt`。**日期一律是 epoch day**(1970-01-01 起算的天數,當地日期,沒有時區);時間是 `minuteOfDay`(午夜起算分鐘數);`createdAt`、`updatedAt`、`sharedAt`、`exportedAt` 是 epoch 毫秒。本國幣固定 TWD。

| 表 | 欄位(★ = 跨裝置比對用) |
|---|---|
| Trip | id、name、startDate、endDate、homeCurrency("TWD")、budget?、splitEnabled、createdAt、archived、coverPath?、coverTheme?、★uuid、sharedBy?(非空=別人分享來的唯讀副本)、sharedAt?、navApp?("google"/"naver"/"kakao",空=每次詢問) |
| TripCurrencyRate | tripId+currency、rate(1 外幣 = ? TWD)、updatedAt、source |
| Member | id、tripId、name |
| Category | id、name、sortOrder、icon(空=依名稱猜)、color(-1=依名稱猜);預設:吃、交通、購物、住宿、景點、其他 |
| PaymentMethod | id、name、sortOrder;預設:現金、信用卡、行動支付 |
| Expense | id、tripId、date、minuteOfDay?、title、amount(原幣)、currency、rate、homeAmount(=amount×rate 四捨五入到 2 位)、categoryId?、paymentMethodId?、payerId?、note、ocrText、planItemId?、createdAt、★uuid、addedBy?、pending |
| Photo | id、expenseId、type("RECEIPT"/"MEMORY")、path、width、height、createdAt、savedToGallery |
| PlanItem | id、tripId、title、categoryId?(與支出共用分類)、date?(空=待排)、minuteOfDay?、status("TODO"/"DONE"/"SKIPPED")、reservation("NONE"/"NEEDED"/"BOOKED")、reservationNote、location(地址或地圖連結)、estCost?(已不顯示,保留)、note、createdAt、★uuid、addedBy?、pending |
| DayNote | tripId+day、text、updatedAt;**待排的 day = Int64.min(-9223372036854775808)** |
| PlanPhoto | id、planItemId、path、width、height、createdAt |

- `ExpenseShare`(分攤對象)在 Android 有表但目前沒用,不需要做。
- `readOnly = sharedBy != nil`。唯讀旅程隱藏記一筆/新增/編輯/打勾,只能看;但可以新增「自己的補充」(pending = true),只有 pending 的項目自己可以改。
- 刪除旅程/支出/行程時,一併刪掉它們的照片檔。
- 照片存 App 私有資料夾,資料庫只存路徑(iOS 建議存相對於 Application Support 的相對路徑,避免重裝後絕對路徑變掉)。壓縮成 JPEG:收據長邊 1600、回憶 1200、行程截圖 1600。

## 4. 檔案交換格式(最重要)

來源:`backup/TripArchive.kt`(讀這個檔最準)。一個 `.zip`:

```
manifest.json   這是什麼檔
data.json       內容
files/...       照片(covers/、receipts/、memories/、plans/)
```

### 4.1 manifest.json

```json
{ "format": "travelledger", "version": 1, "kind": "trip|backup|additions|plans",
  "exportedAt": 1790000100000, "sharedBy": "小明", "appVersion": "0.10.0", "photoCount": 2,
  "trips": [ { "uuid": "...", "name": "首爾賞楓", "startDate": 20748, "endDate": 20753, "expenseCount": 2, "totalHome": 1488 } ] }
```

- `format` 不是 `travelledger` → 「這不是卡溜趴的檔案」。`version` 大於自己支援的 → 「這個檔案來自較新版的卡溜趴,請先更新 App」。**iPhone 寫出的檔一律 version 1**。
- 匯入前先只讀 manifest 給使用者確認(旅程名稱、日期、筆數、總額、照片數)。
- `sharedBy`:kind=trip 是分享人;kind=additions 是補充的同伴;其他不放。

### 4.2 data.json(kind = trip / backup)

```
{ "categories": [ {name, sortOrder, icon, color} ],
  "payments":   [ {name, sortOrder} ],
  "trips": [ {
      uuid, name, startDate, endDate, homeCurrency, budget?, splitEnabled, createdAt, coverTheme?, navApp?, cover?(檔名),
      sharedBy?, sharedAt?,
      members: ["我","小美"],                      ← 依順序,支出的 payer 是這裡的索引
      rates:   [ {currency, rate, updatedAt, source} ],
      plans:   [ {uuid, title, category?(名稱), date?, minuteOfDay?, status, reservation, reservationNote, location,
                  estCost?, note, createdAt, addedBy?, pending?(只有 true 才寫), photos:[{file, width, height, createdAt}]} ],
      dayNotes:[ {day, text, updatedAt} ],
      expenses:[ {uuid, date, minuteOfDay?, title, amount, currency, rate, homeAmount, category?, payment?, note, ocrText,
                  createdAt, planUuid?, plan?(plans 的索引,舊檔用), payer?(members 的索引), addedBy?, pending?,
                  photos:[{file, type, width, height, createdAt}]} ]
  } ] }
```

- **分類、付款方式用名稱對應**,不用 id;匯入時手機上沒有的名稱就新增(排在最後)。
- 支出連到行程:優先 `planUuid`,沒有才用 `plan` 索引。
- 「?」的欄位可能不存在**或是 null**,兩種都要接受。數字可能寫成整數(`1200`)或小數(`1200.5`),Double 欄位都要能讀整數。
- **`day` 可能是 -9223372036854775808**:不能經過 Double(會失真)。用 `JSONDecoder` 解成 `Int64` 沒問題;若用 `JSONSerialization` 要確認拿到的是精確的 Int64。寫出時也必須是這個精確的整數。
- 照片檔名格式 `files/<資料夾>/<序號>-<原檔名>`,資料夾:`covers`、`receipts`、`memories`、`plans`。行程截圖**永遠**帶上;支出照片和封面只有使用者勾「包含照片」才帶。
- zip 裡的檔名含 `..` 或是資料夾就略過(安全)。

### 4.3 各種 kind 的匯入規則

| kind | 誰產生 | 匯入規則 |
|---|---|---|
| `trip` | 記帳人「分享給同伴」 | 變成唯讀副本(`sharedBy` 取檔案裡的,沒有就用 manifest 的 sharedBy,再沒有用「同伴」)。同 uuid 已存在:是**自己的**旅程 → 拒絕「「X」是你自己記的旅程,不需要匯入」;是唯讀副本 → 清掉官方內容後重寫(成員、匯率、行程、支出、當日筆記、照片),**自己 pending 的補充保留**:記帳人已收下的(官方內容出現同 uuid)刪掉自己那份,其餘保留並用 uuid 重新連回行程。分享檔**不含** pending 項目 |
| `backup` | 「備份全部」 | 每趟:同 uuid 就整趟取代(含 pending),沒有就新增。還原前若手機上同 uuid 的旅程在 `exportedAt` 之後還有新紀錄或支出筆數比備份多,紅字警告「仍要還原」 |
| `additions` | 同伴「傳給記帳人」 | data.json:`{tripUuid, categories, payments, plans:[…], expenses:[…]}`,格式同上,每項有 `addedBy`。記帳人審核勾選後加入(手機上已有同 uuid 的跳過,所以重複打開不會重複)。找不到旅程 → 「手機上沒有「X」這趟旅程」;自己手上那趟是唯讀 → 「這是給記帳人(X)的補充,請轉傳給他」 |
| `plans` | 電腦行程桌「傳到手機」 | data.json:`{trip:{uuid,name,startDate,endDate,navApp?}, plans:[…], deleted:[uuid…], dayNotes:[…], categories}`。旅程用 uuid 找,沒有就新建;是別人的唯讀旅程 → 拒絕。每個 plan 同 uuid 就更新欄位(title、category、date、minuteOfDay、status、reservation、reservationNote、location、note)並換掉截圖,沒有就新增;`deleted` 裡的刪掉;dayNotes 覆寫(text 空字串 = 刪除該日筆記);**支出完全不動** |

iPhone 版也要能**產生** trip、backup、additions 三種檔(plans 只有電腦行程桌產生,iPhone 只需要讀)。檔名:`卡溜趴-<旅程名>.zip`、`卡溜趴備份-<yyyy-MM-dd>.zip`、`卡溜趴補充-<旅程名>-<同伴名>.zip`。

## 5. 要照搬的邏輯

| Android 檔案 | 內容 | 驗證 |
|---|---|---|
| `logic/PlanParser.kt` | 貼上多行/拆筆記成多個行程:每行(與 →)一項、去編號、時間(09:00、3點半、早上/中午/下午/晚上…)、分類(明寫標籤【交通】>移動動詞>關鍵字)、標題清理、單獨一行網址併入上一項地點、「第三天」等標題略過 | `desktop/test/parser-cases.json`(Android 與桌面網頁都跑這份,iPhone 也要全部通過;桌面版 JS 寫法在 `desktop/src/parser.js`,比 Kotlin 更接近 Swift 可直接參考) |
| `logic/Links.kt` | 網址(遇到中日韓文字或括號停止)與電話(+ 或 0 開頭、8~15 位數)偵測 | `app/src/test/.../logic/LinksTest.kt` 的案例 |
| `logic/ReceiptParser.kt` | 從辨識文字猜店家、金額、幣別、日期時間、是否為收據 | `ReceiptParserTest` 的案例 |
| `ui/Format.kt` | 日期(10/22 週四)、金額格式、`toHomeAmount`(四捨五入 2 位) | — |
| `ui/CategoryStyle.kt`、`logic/CoverArt.kt`、`ui/CoverArtwork.kt` | 分類圖示/顏色、封面插圖 | 對照截圖 `app/src/test/snapshots/images` |

建議把 Android 的單元測試案例轉成 XCTest,至少 parser-cases.json 直接讀檔測。

## 6. 導航

每趟旅程設定導航軟體;點「導航」:
- Google:`comgooglemaps://?daddr=<名稱或地址>`,沒裝就開 `https://www.google.com/maps/dir/?api=1&destination=<...>`;location 本身是地圖連結時直接開連結。
- Naver:`nmap://search?query=<...>&appname=<bundle id>`
- Kakao:`kakaomap://search?q=<...>`
- 沒裝該 App → 退回 Apple 地圖或 Google 網頁版。`Info.plist` 要加 `LSApplicationQueriesSchemes`:`comgooglemaps`、`nmap`、`kakaomap`。
- 「每次詢問」:列出已安裝的選項讓使用者選。

## 7. 相容測試(一定要做)

`compat/` 資料夾(說明見 `compat/README.md`):

1. **讀 Android 的檔**:`compat/fixtures/` 有 Android 真的寫出來的檔:
   - `trip.zip`:分享的旅程「首爾賞楓」(2 成員、KRW 匯率、3 個行程含待排與截圖、2 則當日筆記含待排、2 筆支出含收據照片與付款人、連到行程)。
   - `backup.zip`:備份,含自己的「首爾賞楓」與別人分享的唯讀旅程「沖繩」(有一筆 pending 補充)。
   - `additions.zip`:同伴「小美」對首爾賞楓的補充(1 個行程、1 筆支出連到 plan-market)。
   - `plans.zip`:電腦行程桌寫的檔(廣藏市場移到 20751、刪除 Olive Young、新增兩項、景福宮多一張截圖、20750 當日筆記「先換錢再出門」)。

   每個檔都寫 XCTest:匯入後逐欄位比對(預期值見 `compat/README.md`)。
2. **寫給 Android 讀**:iPhone 匯出 trip、backup、additions 各一個檔(用和 fixtures 相同的資料),放到 `compat/from-ios/`,commit。Android 端的 `CompatFixturesTest.filesFromTheIphone` 會逐一匯入,通過才算相容。請使用者通知 Android 那邊的 Claude 執行這個測試。
3. **來回一次**:匯入 `trip.zip` → 匯出 → 再匯入,內容不變。

## 8. 建議開發順序(每階段都要能跑、有測試、commit)

1. Xcode 專案骨架、資料模型、旅程列表/新增/編輯。
2. **檔案匯入**(4 種 kind 都能讀)+ 唯讀旅程顯示 + 相容測試第 1 項。← 做完這步,iPhone 同伴就能看分享的旅程,先給家人裝來用。
3. 行程:每天/待排、行程內容頁、編輯、當日筆記、截圖、網址電話可點、導航。
4. 記一筆:計算機、分類/付款方式、幣別匯率、帳本明細、統計。
5. 匯出:分享旅程、同伴補充(唯讀旅程上新增 pending → 傳給記帳人)、審核補充、備份/還原 + 相容測試第 2、3 項。
6. 照片:相機、相簿、收據辨識(Vision)、存相簿。
7. 貼上拆分、拆筆記(含復原)、Share Extension、細節打磨。

## 9. 介面規則(使用者在 Android 版明確要求過的)

- **任何畫面都不可以出現直排字**(窄欄位把中文擠成一字一行)或數字/短字被斷行、文字被切到。用動態字體最大的幾級、iPhone SE 寬度檢查每個畫面。
- 字不要縮小:省空間靠減少留白與重複標籤;按鈕高度隨字體長高,放不下就改上下排。
- 記一筆要快:三步內完成。
- 編輯行程:名稱 1 行、字多長到 2 行;筆記在最下面;日期是一排方形格子可左右滑(不要兩排)。
- 點行程先看內容,編輯/刪除是右上角圖示;支出點了直接編輯。
- 淺色、深色都要好看;風格參考 `docs/screens/` 與 `app/src/test/snapshots/images/`。

## 10. 不能在模擬器自動測的

相機、真實收據辨識準確度、LINE 傳檔與「用卡溜趴開啟」、存相簿、導航 App 跳轉:做完請使用者在真的 iPhone 上照清單點一次,並和 Android 手機互傳一次。

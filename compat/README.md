# 檔案相容測試

卡溜趴的 Android 版、電腦行程桌、iPhone 版用同一種 .zip 檔互傳(格式見 `docs/IOS_PORT.md` 第 4 節)。這裡放各版本真的寫出來的檔,用來確認彼此讀得懂。

```
compat/
  fixtures/   Android 與電腦行程桌寫出的檔(iPhone 版要能讀)
  from-ios/   iPhone 版寫出的檔(Android 版要能讀)
```

## fixtures/

由 `app/src/test/java/com/archiekuo/travelledger/backup/CompatFixturesTest.kt` 產生(`WRITE_COMPAT_FIXTURES=1` 時重新產生,平常只驗證 Android 仍讀得進來)。`plans.zip` 是 `desktop/test/e2e.mjs` 操作電腦行程桌寫出的檔(同 `app/src/test/resources/desktop/roundtrip.zip`)。

日期是 epoch day:20748 = 2026/10/22,20749 = 10/23,20750 = 10/24,20751 = 10/25,20753 = 10/27。

### trip.zip(kind=trip,小明分享)

- 旅程:uuid `trip-seoul`「首爾賞楓」,20748–20753,預算 40000,分帳開啟,導航 `naver`,sharedBy「小明」,sharedAt 1790000100000。
- 成員:我、小美。匯率:KRW 0.024。
- 行程:
  - `plan-palace`「景福宮 韓服體驗」景點,20749 09:00(540),已訂位 BOOKED「10:00 兩位 #A123」,地點 `https://maps.app.goo.gl/palace`,筆記含網址與電話 02-1234-5678,1 張截圖 `files/plans/0-menu.jpg`(64×48 JPEG)。
  - `plan-market`「廣藏市場」吃,20750,無時間。
  - `plan-oy`「Olive Young」購物,待排(沒有 date)。
- 當日筆記:20749「早點出門」;待排(day = -9223372036854775808)「有空再去」。
- 支出:
  - `exp-metro`「機場捷運」20748 15:30(930),1200 TWD,rate 1,homeAmount 1200,交通,信用卡,付款人 我(payer 0)。
  - `exp-pancake`「綠豆煎餅」20750,12000 KRW,rate 0.024,homeAmount 288,吃,現金,付款人 小美(payer 1),備註「很好吃」,連到 `plan-market`,1 張收據照片(type RECEIPT)。
- 匯入後:唯讀旅程,顯示「小明 分享」。

### backup.zip(kind=backup)

- `trip-seoul`:內容同上,但 sharedBy 為空(自己的旅程,可編輯)。
- `trip-okinawa`「沖繩」20800–20803,sharedBy「阿姨」(唯讀):`plan-aquarium`「美麗海水族館」20801;`plan-kouri`「古宇利島」待排,**pending = true**(自己尚未傳出的補充,備份要保留)。

### additions.zip(kind=additions,小美的補充)

- tripUuid `trip-seoul`,manifest sharedBy「小美」。
- 行程 `plan-add-dumpling`「明洞餃子」吃,20751 12:00(720),addedBy 小美。
- 支出 `exp-add-gimbap`「麻藥飯捲」20750,5000 KRW,homeAmount 120,吃,planUuid `plan-market`,addedBy 小美。
- 測法:先還原 backup.zip(有自己的 trip-seoul),審核兩項都勾 → 加入 2 項;再匯入一次 → 0 項(不重複)。若只匯入 trip.zip(唯讀),要被拒絕並提示轉給記帳人。

### plans.zip(kind=plans,電腦行程桌)

- 併入自己的 `trip-seoul` 後:
  - `plan-market` 改到 20751。
  - `plan-oy` 被刪除(在 `deleted`)。
  - 新增「搭乘 01A 循環公車到南大門市場站」交通 11:40(700)、「明洞餃子」吃,都在 20751(uuid 是隨機的)。
  - `plan-palace` 變成 2 張截圖、訂位 NONE、地點空白、筆記「韓服要先預約 https://hanbok.example.com」(電腦版的內容覆蓋手機版)。**第一張截圖只有 7 個位元組,不是真的圖**:匯入不能因此失敗,顯示時當作壞圖處理即可。
  - 當日筆記:20750「先換錢再出門」;待排仍是「有空再去」。
  - 支出 2 筆不變。
- 併入唯讀的 trip-seoul(只匯入 trip.zip 的情況)要被拒絕。

## from-ios/

iPhone 版用 fixtures 的同一份資料匯出 trip、backup、additions 檔放這裡(檔名隨意,`.zip`),commit 之後在 Android 專案執行:

```
./gradlew :app:testDebugUnitTest --tests '*CompatFixturesTest'
```

`filesFromTheIphone` 會逐一匯入,全部成功才算相容。additions 檔會先還原 `fixtures/backup.zip` 再匯入,所以要針對 `trip-seoul` 做補充。

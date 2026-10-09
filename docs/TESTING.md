# 測試

在專案根目錄執行(需要 Android SDK,見 `docs/android-env-setup.sh`):

```
./gradlew :app:testDebugUnitTest          # 全部測試
./gradlew :app:recordPaparazziDebug       # 重新產生畫面截圖(app/src/test/snapshots/images)
```

| 測試 | 內容 |
|---|---|
| `data/LedgerFlowTest` | 真實 Room 資料庫 + 真實 ViewModel:一趟東京旅程的完整記帳、匯率、預設值、名稱提示、照片、刪除分類/付款方式/旅程、行程規劃與從行程記帳 |
| `data/MigrationTest` | 舊版資料庫(v1)放入資料後升級到最新版,逐版驗證結構並確認資料保留;v6 每個舊旅程都拿到不同的 uuid |
| `ui/AppFlowTest` | 啟動整個 App 用點擊操作:建旅程、記帳(計算機)、帳本與統計、新增行程並從行程記帳、修改與刪除支出、同伴打開分享檔→確認→唯讀旅程與支出明細 |
| `backup/TripArchiveTest` | 兩支手機:分享旅程→同伴匯入唯讀副本(帳目、行程連結、付款人、自訂分類都正確)、再分享會更新同一趟、自己的旅程被拒、照片可選並在更新時清掉舊檔、備份還原到新手機且重複還原不重複、非旅帳檔案被拒 |
| `ui/PasteDialogTest` | 貼上多筆對話框 |
| `logic/*Test`、`ui/LogicTest` | 收據解析、貼上/地圖分享解析、計算機、換算 |
| `snap/ScreenSnapshots` | 各畫面淺色/深色/特大字截圖 |

測試以 Robolectric 在電腦上模擬 Android,不需要模擬器。Robolectric 需要的 Android 執行檔由 Gradle 下載後以離線模式使用(見 `app/build.gradle.kts`)。

**無法在電腦上測的**:相機拍照、ML Kit 對真實收據的辨識、Google 地圖分享、存到相簿、LINE 傳檔與「用其他應用程式開啟」、系統存檔畫面,需在手機上確認。

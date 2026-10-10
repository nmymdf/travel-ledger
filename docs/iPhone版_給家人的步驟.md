# 卡溜趴 iPhone 版:第一次做的操作步驟

寫給在 Mac 上用 Claude 做 iPhone 版的家人。程式交給 Claude 寫,你負責:準備環境、回答 Claude 的問題、在 Xcode 和 iPhone 上按它說的按鈕、試用。

技術細節都寫在 `docs/IOS_PORT.md`,那是給 Claude 讀的,你不用看懂。

---

## 一、準備(約 1~2 小時,大部分在等下載)

| 需要 | 說明 |
|---|---|
| Mac | 建議 M1 以後的機型、系統更新到最新;硬碟至少留 40 GB |
| Xcode | Mac 的 App Store 搜尋「Xcode」安裝(很大,要等一陣子)。裝好打開一次,讓它裝完附加元件 |
| Apple 開發者帳號 | 已經有了。打開 Xcode → 左上角 Xcode 選單 → Settings → Accounts → 左下「+」→ 用那個 Apple ID 登入 |
| GitHub 帳號 | 到 github.com 免費註冊。把帳號名稱告訴卡溜趴作者,請他把你加進專案(見第六節) |
| Claude 帳號 | Claude Pro 或 Max 方案(Claude Code 要用) |
| iPhone + 傳輸線 | 試裝用 |

## 二、安裝 Claude Code

1. 打開 Mac 的「終端機」(按 ⌘+空白鍵,輸入「終端機」或 Terminal)。
2. 貼上這行,按 Enter:
   ```
   curl -fsSL https://claude.ai/install.sh | bash
   ```
3. 裝好後輸入 `claude` 按 Enter,第一次會打開瀏覽器請你登入 Claude 帳號。登入後回到終端機就能用了。
4. 輸入 `/exit` 可以離開。

> 官方說明:https://code.claude.com/docs (安裝方式有變時以官網為準)

## 三、下載卡溜趴的程式

在終端機一行一行貼上:

```
cd ~/Documents
git clone https://github.com/nmymdf/travel-ledger.git
cd travel-ledger
claude
```

如果問你要不要安裝「命令列開發者工具」,按安裝,裝完再貼一次。

之後每次要繼續做,都是打開終端機輸入:

```
cd ~/Documents/travel-ledger
claude
```

## 四、第一次跟 Claude 說的話

Claude 打開後,直接複製這段貼給它:

```
請先讀 docs/IOS_PORT.md 和 compat/README.md,再看 docs/SPEC.md。
我們要做卡溜趴的 iPhone 版。我是第一次做 iPhone app,請用繁體中文一步一步帶我,
需要我在 Xcode 或 iPhone 上操作時,告訴我點哪裡。
請在 ios 分支工作,先做 IOS_PORT.md 第 8 節的第 1、2 階段,做之前先跟我說計畫。
```

## 五、做的時候會遇到的事

- **Claude 會先問再做**:要執行指令或改檔案時會問「可以嗎?」。看一下它要做什麼,沒問題就允許。看不懂就直接問它「這是做什麼的?」。
- **安裝工具**:它可能請你安裝 Homebrew、GitHub CLI(`gh`)等工具。
- **登入 GitHub**:要上傳程式時,在終端機輸入 `gh auth login`,選 GitHub.com → HTTPS → 用瀏覽器登入。**密碼一律在瀏覽器或系統視窗輸入,不要打在跟 Claude 的對話裡。**
- **在模擬器上跑**:Claude 會用 Xcode 開模擬的 iPhone 給你看畫面。
- **裝到自己的 iPhone**:
  1. iPhone 用線接 Mac,手機上按「信任這部電腦」。
  2. iPhone:設定 → 隱私權與安全性 → 開發者模式 → 打開(會重開機)。
  3. Xcode 上方選你的 iPhone,按 ▶︎。第一次在手機上:設定 → 一般 → VPN 與裝置管理 → 信任你的開發者帳號。
- **每做完一個階段**:跟 Claude 說「幫我 commit 並 push 到 ios 分支」。
- **對話太長或隔天再做**:重新打開 `claude`,說「我們在做卡溜趴 iPhone 版,請看 docs/IOS_PORT.md 和 git log,告訴我做到哪了,接著做下一步」。

## 六、卡溜趴作者(Android 那邊)要做的

1. **把家人加進專案**:GitHub 打開 nmymdf/travel-ledger → Settings → Collaborators → Add people → 輸入家人的 GitHub 帳號。家人會收到邀請信,按接受。
2. **相容測試**:iPhone 版做到匯出檔案時,Claude 會把 iPhone 寫的檔放進 `compat/from-ios/` 並 push。這時請你跟 Android 這邊的 Claude 說:「iPhone 的相容檔在 ios 分支的 compat/from-ios,幫我跑相容測試」。

## 七、給家人和同伴安裝(TestFlight)

做到第 2 階段(能打開 LINE 傳來的旅程)就可以先給大家用。

1. 跟 Claude 說「我要用 TestFlight 發給家人測試,請帶我做」。它會帶你:
   - 到 App Store Connect(appstoreconnect.apple.com)建立 App。
   - Xcode → Product → Archive → Distribute App → 上傳。
2. 加測試者,兩種方式:
   - **內部測試**:不用審核,但每個人都要先被加進 App Store Connect 的團隊(使用者與存取權限)。
   - **外部測試**:輸入對方的 email 或給公開連結。第一版要等 Apple 簡單審核,通常一天內。
3. 家人在 iPhone 安裝「TestFlight」App,收到邀請後按安裝。
4. TestFlight 的版本 90 天後過期,之後要再上傳一版。

## 八、互傳試一次

iPhone 版做到能匯入匯出後,和 Android 手機用 LINE 互傳一次:

- Android 分享旅程 → iPhone 打開。
- iPhone 補充 → 傳回 Android 審核。
- iPhone 備份 → 還原。

有問題就把畫面截圖給 Claude 看。

## 九、注意

- 這個專案在 GitHub 上是**公開**的:憑證、密碼、`.p12`、App Store Connect 金鑰都不能放進專案。Claude 知道這條規則,但看到它要上傳奇怪的檔案時可以問一下。
- 不確定的設計(例如畫面要長怎樣),Claude 會問你;你也可以問卡溜趴作者,或請他在 Android 版截圖給你參考。

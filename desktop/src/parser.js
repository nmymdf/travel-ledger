// Plan parsing for the desktop planner — a line-for-line port of the phone's logic/PlanParser.kt,
// so pasted notes split and classify the same way on both. desktop/test/parser.test.mjs keeps them in step.
const PlanParser = (() => {
  const urlRegex = /https?:\/\/\S+/;
  const urlRegexG = /https?:\/\/\S+/g;

  // Keyword → category name. First match wins, so lodging ("飯店") goes before food ("飯").
  const hints = [
    ["住宿", ["飯店", "酒店", "旅館", "民宿", "ホテル", "Hotel", "hotel", "Inn", "check-in", "入住", "退房", "住宿"]],
    ["交通", ["機場", "空港", "車站", "駅", "新幹線", "JR", "巴士", "バス", "租車", "Airport", "Station", "地鐵", "捷運", "KTX", "高鐵", "計程車", "轉乘", "接駁", "AREX"]],
    ["吃", [
      "早餐", "午餐", "晚餐", "宵夜", "下午茶", "拉麵", "ラーメン", "壽司", "寿司", "燒肉", "焼肉", "烤肉", "炸雞", "蔘雞湯", "部隊鍋", "冷麵",
      "火鍋", "牛舌", "餃子", "小吃", "餐", "食堂", "居酒屋", "咖哩", "カレー", "麵", "うどん", "そば", "丼", "飯", "料理", "甜點", "スイーツ",
      "パン", "麵包", "Cafe", "cafe", "咖啡", "茶", "Restaurant", "restaurant",
    ]],
    ["購物", ["百貨", "商場", "Outlet", "outlet", "唐吉訶德", "ドン・キホーテ", "藥妝", "超市", "市場", "商店街", "Mall", "購物", "免稅", "伴手禮", "Olive Young"]],
    ["景點", [
      "寺", "神社", "宮", "城", "塔", "公園", "博物館", "美術館", "展望", "水族館", "動物園", "樂園", "ランド", "山", "海", "湖", "島", "橋",
      "老街", "夜市", "壁畫村", "韓屋", "韓服", "Temple", "Park", "Museum", "Tower", "Shrine", "Palace",
    ]],
  ];

  const tagNames = {
    "交通": "交通", "移動": "交通", "吃": "吃", "餐": "吃", "美食": "吃", "餐廳": "吃", "早餐": "吃", "午餐": "吃", "晚餐": "吃",
    "住宿": "住宿", "住": "住宿", "飯店": "住宿", "購物": "購物", "買": "購物", "逛街": "購物", "景點": "景點", "玩": "景點", "參觀": "景點",
  };
  const bracketTag = /[【\[(（〔]\s*([^】\])）〕]{1,4})\s*[】\])）〕]\s*[:：]?/;
  const prefixTag = /^\s*(交通|移動|住宿|購物|景點|美食|餐廳|早餐|午餐|晚餐)\s*[:：]/;
  const emojiTags = [
    ["交通", ["🚇", "🚌", "🚕", "🚆", "🚄", "✈", "🚶", "🚗", "🚉"]],
    ["吃", ["🍜", "🍣", "🍖", "🍗", "🍲", "🍚", "☕", "🍰", "🍺", "🍴", "🍽"]],
    ["住宿", ["🏨", "🛏"]],
    ["購物", ["🛍", "🛒"]],
    ["景點", ["📷", "📸", "🏯", "⛩", "🏞"]],
  ];
  const travelVerbs = /^(?:搭乘|搭|坐|轉乘|轉搭|步行|走路|前往|移動|開車|騎)|(?:搭乘|轉乘|步行約|走路約|車程|下車|上車|直達)/;

  const removeRange = (text, m) => (text.slice(0, m.index) + text.slice(m.index + m[0].length)).trim();

  /** The category the text names itself (tag, prefix or emoji), and the text without that marker. */
  function explicitCategory(text) {
    let m = bracketTag.exec(text);
    if (m && tagNames[m[1].trim()]) return [tagNames[m[1].trim()], removeRange(text, m)];
    m = prefixTag.exec(text);
    if (m && tagNames[m[1]]) return [tagNames[m[1]], removeRange(text, m)];
    for (const [cat, marks] of emojiTags) if (marks.some((x) => text.includes(x))) return [cat, text];
    return null;
  }

  function guessCategory(text) {
    const tagged = explicitCategory(text);
    if (tagged) return tagged[0];
    if (travelVerbs.test(text.trim())) return "交通";
    for (const [cat, words] of hints) if (words.some((w) => text.includes(w))) return cat;
    return null;
  }

  const headerLine = /^\s*(?:day\s*\d+|d\d+|第\s*[一二三四五六七八九十\d]+\s*天)\s*[:：]?\s*$/i;
  const clock = /(?<!\d)([01]?\d|2[0-3])\s*[:：]\s*([0-5]\d)(?!\d)/;
  const clockG = /(?<!\d)([01]?\d|2[0-3])\s*[:：]\s*([0-5]\d)(?!\d)/g;
  const hourWord = /(?<!\d)([01]?\d|2[0-3])\s*點\s*(半|\d{1,2}\s*分)?/;
  const hourWordG = /(?<!\d)([01]?\d|2[0-3])\s*點\s*(半|\d{1,2}\s*分)?/g;
  const periods = [
    ["早上", 9 * 60], ["上午", 9 * 60], ["早餐", 8 * 60], ["中午", 12 * 60], ["午餐", 12 * 60], ["下午茶", 15 * 60], ["下午", 14 * 60],
    ["傍晚", 17 * 60], ["晚餐", 18 * 60], ["晚上", 18 * 60], ["宵夜", 21 * 60],
  ];
  const mealWords = new Set(["早餐", "午餐", "晚餐", "下午茶", "宵夜"]);
  const separators = /\s*(?:→|➡|->|⇒|>)\s*/;
  const bullet = /^\s*(?:[-*•・●○◎▶►✓✔☐□]|\d{1,3}[.、)):]|[(\(]\d{1,3}[)\)])\s*/;
  const lines = (t) => t.split(/\r\n|\n|\r/);
  const trimChars = (s, chars) => {
    let a = 0, b = s.length;
    while (a < b && chars.includes(s[a])) a++;
    while (b > a && chars.includes(s[b - 1])) b--;
    return s.slice(a, b);
  };
  const edge = ["-", "—", ":", "：", ",", "，", "、", "～", "~", "。", " "];

  /** The time written in text (09:30, 3點半, 下午3點, 晚上…), in minutes after midnight, or null. */
  function findTime(text) {
    const afternoon = text.includes("下午") || text.includes("晚上") || text.includes("傍晚");
    let m = clock.exec(text);
    if (m) {
      const h = +m[1];
      return (afternoon && h < 12 ? h + 12 : h) * 60 + +m[2];
    }
    m = hourWord.exec(text);
    if (m) {
      const h = +m[1];
      const min = m[2] === "半" ? 30 : m[2] ? +m[2].replace(/\D/g, "") : 0;
      return (afternoon && h < 12 ? h + 12 : h) * 60 + min;
    }
    for (const [w, t] of periods) if (text.includes(w)) return t;
    return null;
  }

  /** A day's notes split into plan items, exactly as on the phone (see PlanParser.splitNote). */
  function splitNote(text) {
    const out = [];
    for (const raw of lines(text)) {
      if (!raw.trim() || headerLine.test(raw)) continue;
      const urlMatch = urlRegex.exec(raw);
      const lineUrl = urlMatch ? urlMatch[0] : null;
      const rest = raw.replace(urlRegexG, " ").trim();
      if (!rest && lineUrl) {
        if (out.length && !out[out.length - 1].location) out[out.length - 1].location = lineUrl;
        else out.push({ title: "地圖地點", location: lineUrl, category: null, minuteOfDay: null });
        continue;
      }
      const lineTime = findTime(rest);
      const steps = rest.split(separators).filter((s) => s.trim());
      steps.forEach((step, i) => {
        const time = findTime(step) ?? (i === 0 ? lineTime : null);
        let title = step.replace(clockG, " ").replace(hourWordG, " ").replace(bullet, "");
        for (const [w] of periods) if (!mealWords.has(w)) title = title.split(w).join(" ");
        title = trimChars(title.replace(/\s+/g, " ").trim(), edge);
        const tagged = explicitCategory(title);
        const clean = trimChars((tagged ? tagged[1] : title).trim(), edge);
        if (!clean) return;
        out.push({
          title: clean, location: i === 0 ? lineUrl || "" : "",
          category: tagged ? tagged[0] : guessCategory(clean) ?? guessCategory(step), minuteOfDay: time,
        });
      });
    }
    return out;
  }

  /** The place name inside a Google Maps link (…/maps/place/明洞餃子+本店/@…), or null. */
  function nameFromMapsUrl(url) {
    const m = /\/maps\/place\/([^/@?]+)/.exec(url);
    if (!m) return null;
    try { return decodeURIComponent(m[1].replace(/\+/g, " ")).trim() || null; } catch { return null; }
  }

  /** Text dropped or pasted from a map app: "店名\nhttps://maps.app.goo.gl/…" or just a link. */
  function parseShare(text) {
    const urlMatch = urlRegex.exec(text);
    const url = urlMatch ? urlMatch[0] : "";
    const first = lines(text.replace(urlRegexG, "\n")).map((l) => l.trim()).find((l) => l) || (url && nameFromMapsUrl(url)) || "";
    let name = first;
    const cut = [...name].findIndex((c) => c === "," || c === "，");
    if (cut > 0 && /\d/.test(name.slice(cut))) name = name.slice(0, cut).trim();
    return { title: name || "地圖地點", location: url || (name ? "" : text.trim()), category: guessCategory(name), minuteOfDay: null };
  }

  return { explicitCategory, guessCategory, findTime, splitNote, parseShare, nameFromMapsUrl };
})();
if (typeof module !== "undefined") module.exports = PlanParser;

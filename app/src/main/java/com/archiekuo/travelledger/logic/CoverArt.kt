package com.archiekuo.travelledger.logic

import java.time.LocalDate

/** Illustrated cover scenes. */
enum class CoverTheme(val label: String) {
    AUTUMN("秋楓"), SAKURA("櫻花"), SNOW("雪景"), BEACH("海島"), FOOD("美食"), CITY("城市"), GENERIC("旅行");

    companion object {
        fun of(name: String?): CoverTheme? = entries.firstOrNull { it.name == name }
    }
}

/** Landmark silhouettes drawn into the scene. */
enum class Landmark { SEOUL_TOWER, TOKYO_TOWER, FUJI, OSAKA_CASTLE, PAGODA, TORII, TAIPEI_101, BANGKOK_TEMPLE, MARINA_BAY, EIFFEL, BIG_BEN, CITY_SKYLINE }

data class CoverSpec(val theme: CoverTheme, val landmarks: List<Landmark>)

/** Picks a cover illustration from the trip name (theme + place) and, failing that, the travel month. */
object CoverArt {
    private val themeWords = listOf(
        CoverTheme.AUTUMN to listOf("楓", "紅葉", "秋"),
        CoverTheme.SAKURA to listOf("櫻", "桜", "花見", "賞花", "春"),
        CoverTheme.SNOW to listOf("雪", "滑雪", "冬", "雪祭", "極光"),
        CoverTheme.BEACH to listOf("海島", "跳島", "海灘", "沙灘", "潛水", "浮潛", "夏日", "度假村", "島"),
        CoverTheme.FOOD to listOf("美食", "吃", "拉麵", "壽司", "燒肉", "夜市", "小吃", "購物", "逛街"),
    )

    // First match wins; more specific places go first.
    private val places = listOf(
        listOf("北海道", "札幌", "小樽", "函館", "富良野") to listOf(Landmark.TORII),
        listOf("沖繩", "峇里", "巴里", "普吉", "關島", "夏威夷", "長灘", "墾丁", "澎湖", "馬爾地夫", "宿霧", "濟州", "石垣", "宮古") to emptyList(),
        listOf("東京", "淺草", "新宿", "澀谷", "銀座", "橫濱") to listOf(Landmark.FUJI, Landmark.TOKYO_TOWER),
        listOf("富士", "河口湖", "箱根") to listOf(Landmark.FUJI),
        listOf("大阪", "關西", "環球影城", "神戶") to listOf(Landmark.OSAKA_CASTLE),
        listOf("京都", "奈良", "嵐山") to listOf(Landmark.PAGODA),
        listOf("首爾", "韓國", "南韓", "釜山", "仁川") to listOf(Landmark.SEOUL_TOWER),
        listOf("台北", "台灣", "臺北", "臺灣") to listOf(Landmark.TAIPEI_101),
        listOf("曼谷", "泰國", "清邁") to listOf(Landmark.BANGKOK_TEMPLE),
        listOf("新加坡") to listOf(Landmark.MARINA_BAY),
        listOf("巴黎", "法國") to listOf(Landmark.EIFFEL),
        listOf("倫敦", "英國") to listOf(Landmark.BIG_BEN),
        listOf("香港", "上海", "紐約", "杜拜", "雪梨") to listOf(Landmark.CITY_SKYLINE),
        listOf("日本", "九州", "福岡", "名古屋", "金澤", "廣島", "東北", "仙台") to listOf(Landmark.TORII),
    )
    private val coldPlaces = listOf("北海道", "札幌", "小樽", "函館", "富良野", "東北", "仙台", "首爾", "韓國", "南韓", "巴黎", "法國", "倫敦", "英國", "歐洲", "瑞士", "冰島", "芬蘭")
    private val beachPlaces = places[1].first

    /** [chosen] is a theme the user picked by hand; it wins over everything the name suggests. */
    fun pick(name: String, start: LocalDate?, chosen: CoverTheme? = null): CoverSpec {
        val landmarks = places.firstOrNull { (words, _) -> words.any { it in name } }?.second ?: emptyList()
        val keyword = themeWords.firstOrNull { (_, words) -> words.any { it in name } }?.first
        val theme = chosen ?: keyword ?: when {
            beachPlaces.any { it in name } -> CoverTheme.BEACH
            start != null && start.monthValue in 3..4 && landmarks.isNotEmpty() -> CoverTheme.SAKURA
            start != null && start.monthValue in 10..11 && landmarks.isNotEmpty() -> CoverTheme.AUTUMN
            start != null && start.monthValue in listOf(12, 1, 2) && coldPlaces.any { it in name } -> CoverTheme.SNOW
            landmarks.isNotEmpty() -> CoverTheme.CITY
            else -> CoverTheme.GENERIC
        }
        return CoverSpec(theme, if (theme == CoverTheme.BEACH) emptyList() else landmarks)
    }
}

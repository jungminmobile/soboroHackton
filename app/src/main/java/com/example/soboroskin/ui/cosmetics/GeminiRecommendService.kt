package com.example.soboroskin.ui.cosmetics

import android.util.Log
import com.example.soboroskin.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

object GeminiRecommendService {

    private const val TAG = "GeminiService"
    // gemini-2.5-flash-lite : 가장 가볍고 저렴한 모델
    private const val API_URL =
        "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash-lite:generateContent"

    // ── 피부 프로필 (새 지표) ────────────────────────────────────
    var skinType:       String = ""
    var moistureScore:  Int = 50   // 수분 0~100
    var poreScore:      Int = 50   // 모공 0~100 (구 oilScore)
    var acneScore:      Int = 30   // 여드름 0~100 (구 troubleScore)
    var elasticityScore:Int = 60   // 탄력 0~100

    // 건조함은 수분의 역수로 유도
    private val drynessScore get() = (100 - moistureScore).coerceIn(0, 100)

    // 전체 캐시 (1번 호출로 모든 탭 채움)
    private var allProducts: List<CosmeticProduct>? = null
    private var isLoading = false

    fun clearCache() { allProducts = null }

    /** 사용 가능한 모델 목록 조회 — Logcat GeminiService 태그로 확인 */
    suspend fun listModels() = withContext(Dispatchers.IO) {
        try {
            val url  = URL("https://generativelanguage.googleapis.com/v1beta/models?key=${BuildConfig.GEMINI_API_KEY}")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15_000
                readTimeout    = 15_000
            }
            val body = conn.inputStream.bufferedReader().readText()
            val arr  = JSONObject(body).getJSONArray("models")
            for (i in 0 until arr.length()) {
                val m = arr.getJSONObject(i)
                val methods = m.optJSONArray("supportedGenerationMethods")?.let {
                    (0 until it.length()).map { j -> it.getString(j) }
                } ?: emptyList()
                if (methods.contains("generateContent")) {
                    Log.d(TAG, "MODEL: ${m.getString("name")}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "listModels failed", e)
        }
    }

    /** 탭 인덱스에 맞게 필터링해서 반환 */
    suspend fun recommend(tabIndex: Int, forceRefresh: Boolean = false): List<CosmeticProduct> {
        if (forceRefresh) clearCache()

        allProducts?.let { return filterByTab(it, tabIndex) }

        if (isLoading) {
            repeat(20) {
                kotlinx.coroutines.delay(300)
                allProducts?.let { return filterByTab(it, tabIndex) }
            }
            return emptyList()
        }

        isLoading = true
        return try {
            val products = fetchAllProducts()
            allProducts = products
            filterByTab(products, tabIndex)
        } finally {
            isLoading = false
        }
    }

    private fun filterByTab(products: List<CosmeticProduct>, tabIndex: Int): List<CosmeticProduct> {
        val categories = listOf("전체", "토너", "세럼", "크림", "선크림")
        if (tabIndex == 0) return products
        val cat = categories.getOrElse(tabIndex) { "" }
        return products.filter { it.category == cat }
    }

    private suspend fun fetchAllProducts(): List<CosmeticProduct> = withContext(Dispatchers.IO) {

        // 피부 상태 요약 — 짧고 압축된 형식
        val skinInfo = if (skinType.isNotEmpty()) {
            "피부타입:$skinType 수분:$moistureScore 건조함:$drynessScore 탄력:$elasticityScore 모공:$poreScore 여드름:$acneScore (0~100점)"
        } else {
            "피부타입:복합성 수분:50 건조함:50 탄력:60 모공:40 여드름:30"
        }

        // ✅ 토큰 절약 포인트:
        //   - 제품 수 12→8 (카테고리별 2개)
        //   - imageUrl 필드 제거 (어차피 부정확)
        //   - 프롬프트 간결화
        //   - maxOutputTokens 2048→1200
        val prompt = """
한국 스킨케어 전문가. 피부: [$skinInfo]
올리브영 실제 제품으로 토너2·세럼2·크림2·선크림2 총8개 추천.
JSON 배열만 출력, 다른 텍스트 없이.
필드: brand,name,category(토너|세럼|크림|선크림),price(예:25000원),description(20자 이내),whyRecommended(20자 이내),officialUrl
""".trimIndent()

        Log.d(TAG, "Skin: $skinInfo")

        val requestBody = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply { put("text", prompt) })
                    })
                })
            })
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.7)
                put("maxOutputTokens", 2000)      // JSON 잘림 방지
            })
        }.toString()

        repeat(3) { attempt ->
            try {
                val conn = (URL("$API_URL?key=${BuildConfig.GEMINI_API_KEY}").openConnection()
                        as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json")
                    doOutput = true
                    connectTimeout = 15_000
                    readTimeout    = 60_000
                }

                OutputStreamWriter(conn.outputStream).use { it.write(requestBody) }

                val code     = conn.responseCode
                val response = if (code == 200) conn.inputStream.bufferedReader().readText()
                               else conn.errorStream?.bufferedReader()?.readText() ?: ""

                Log.d(TAG, "HTTP $code (attempt ${attempt + 1})")

                when (code) {
                    200 -> {
                        val text = JSONObject(response)
                            .getJSONArray("candidates").getJSONObject(0)
                            .getJSONObject("content")
                            .getJSONArray("parts").getJSONObject(0)
                            .getString("text")
                        Log.d(TAG, "Raw response: ${text.take(500)}")
                        val products = parseProducts(extractJson(text))
                        Log.d(TAG, "Parsed ${products.size} products")
                        return@withContext products
                    }
                    503 -> {
                        Log.w(TAG, "503 over capacity, retry ${attempt + 1}/3")
                        if (attempt < 2) kotlinx.coroutines.delay(3_000)
                    }
                    else -> {
                        Log.e(TAG, "HTTP $code: ${response.take(200)}")
                        return@withContext emptyList()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Request failed (attempt ${attempt + 1})", e)
                if (attempt < 2) kotlinx.coroutines.delay(2_000)
            }
        }
        emptyList()
    }

    private fun extractJson(text: String): String {
        // 마크다운 코드블록 제거
        val stripped = text
            .replace(Regex("`{3}json", RegexOption.IGNORE_CASE), "")
            .replace(Regex("`{3}"), "")
            .trim()
        val s = stripped.indexOf('[')
        val e = stripped.lastIndexOf(']')
        val result = if (s != -1 && e > s) stripped.substring(s, e + 1) else "[]"
        Log.d(TAG, "extractJson result (${result.length} chars): ${result.take(100)}")
        return result
    }

    private fun parseProducts(json: String): List<CosmeticProduct> = try {
        Log.d(TAG, "Parsing JSON (${json.length} chars): ${json.take(300)}")
        val arr = JSONArray(json)
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            CosmeticProduct(
                brand          = o.optString("brand"),
                name           = o.optString("name"),
                category       = o.optString("category"),
                price          = o.optString("price"),
                description    = o.optString("description"),
                whyRecommended = o.optString("whyRecommended"),
                officialUrl    = o.optString("officialUrl"),
                imageUrl       = ""
            )
        }
    } catch (e: Exception) {
        Log.e(TAG, "JSON parse failed: ${e.message}\nJSON: ${json.take(300)}", e)
        emptyList()
    }
}

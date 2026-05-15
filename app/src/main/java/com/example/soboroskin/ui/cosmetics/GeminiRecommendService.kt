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
    private const val API_URL =
        "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent"

    // ── 피부 프로필 ─────────────────────────────────────────────
    var skinType: String = ""
    var moistureScore: Int = 50
    var oilScore: Int = 50
    var troubleScore: Int = 50
    var elasticityScore: Int = 50

    // 전체 캐시 (1번 호출로 모든 탭 채움)
    private var allProducts: List<CosmeticProduct>? = null
    private var isLoading = false

    fun clearCache() { allProducts = null }

    /** 사용 가능한 모델 목록 조회 — Logcat에서 확인용 */
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
            Log.e(TAG, "ListModels failed", e)
        }
    }

    /** 탭 인덱스에 맞게 필터링해서 반환 */
    suspend fun recommend(tabIndex: Int, forceRefresh: Boolean = false): List<CosmeticProduct> {
        if (forceRefresh) clearCache()

        // 캐시 있으면 바로 필터링 반환
        allProducts?.let { return filterByTab(it, tabIndex) }

        // 아직 로딩 중이면 잠깐 대기 (중복 호출 방지)
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
        val skinInfo = if (skinType.isNotEmpty())
            "피부 타입: $skinType / 수분도: $moistureScore/100 / 유분도: $oilScore/100 / 트러블: $troubleScore/100 / 탄력도: $elasticityScore/100"
        else
            "피부 타입: 복합성 / 수분도: 50 / 유분도: 50 / 트러블: 30 / 탄력도: 60"

        val prompt = """
당신은 한국 스킨케어 전문가입니다. 아래 피부 분석을 바탕으로 실제 한국 화장품을 추천하세요.
[$skinInfo]

토너 3개, 세럼 3개, 크림 3개, 선크림 3개 — 총 12개를 추천하세요.
올리브영에서 구매 가능한 실제 제품 우선. 각 카테고리별로 피부에 맞는 제품을 골라주세요.

반드시 아래 JSON 배열 형식으로만 응답하고 다른 텍스트는 절대 포함하지 마세요:
[{"brand":"브랜드명","name":"제품명","category":"토너 또는 세럼 또는 크림 또는 선크림","price":"가격(예:35,000원)","description":"제품 특징 1~2문장","whyRecommended":"이 피부에 맞는 이유 1문장","officialUrl":"올리브영 또는 공식 사이트 URL","imageUrl":"제품 이미지 URL"}]
""".trimIndent()

        Log.d(TAG, "Fetching all products for skin='$skinType'")

        val requestBody = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply { put("text", prompt) })
                    })
                })
            })
            put("generationConfig", JSONObject().apply {
                put("thinkingConfig", JSONObject().apply {
                    put("thinkingBudget", 0)
                })
            })
        }.toString()

        // 503 과부하 시 최대 3회 재시도 (2초 간격)
        repeat(3) { attempt ->
            try {
                val apiKey = BuildConfig.GEMINI_API_KEY
                val url    = URL("$API_URL?key=$apiKey")
                val conn   = (url.openConnection() as HttpURLConnection).apply {
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

                Log.d(TAG, "HTTP $code (attempt ${attempt + 1}) — ${response.take(200)}")

                when (code) {
                    200 -> {
                        val text = JSONObject(response)
                            .getJSONArray("candidates")
                            .getJSONObject(0)
                            .getJSONObject("content")
                            .getJSONArray("parts")
                            .getJSONObject(0)
                            .getString("text")
                        val products = parseProducts(extractJson(text))
                        Log.d(TAG, "Parsed ${products.size} products total")
                        return@withContext products
                    }
                    503 -> {
                        Log.w(TAG, "503 over capacity, retrying in 3s… (attempt ${attempt + 1}/3)")
                        if (attempt < 2) kotlinx.coroutines.delay(3_000)
                    }
                    else -> {
                        Log.e(TAG, "Non-retryable HTTP $code")
                        return@withContext emptyList()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Gemini REST call failed (attempt ${attempt + 1})", e)
                if (attempt < 2) kotlinx.coroutines.delay(2_000)
            }
        }
        emptyList()
    }

    private fun extractJson(text: String): String {
        val s = text.indexOf('[')
        val e = text.lastIndexOf(']')
        return if (s != -1 && e > s) text.substring(s, e + 1) else "[]"
    }

    private fun parseProducts(json: String): List<CosmeticProduct> = try {
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
                imageUrl       = o.optString("imageUrl")
            )
        }
    } catch (e: Exception) {
        Log.e(TAG, "JSON parse failed", e)
        emptyList()
    }
}

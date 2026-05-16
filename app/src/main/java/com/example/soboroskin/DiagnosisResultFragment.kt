package com.example.soboroskin.ui.scan

import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.soboroskin.AcneDetection
import com.example.soboroskin.AcneTracker
import com.example.soboroskin.MainActivity
import com.example.soboroskin.R
import com.example.soboroskin.SkinMetricAnalyzer
import com.example.soboroskin.data.db.AppDatabase
import com.example.soboroskin.data.model.DiagnosisEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class DiagnosisResultFragment : Fragment(R.layout.fragment_diagnosis_result) {

    // 분석 완료된 임시 데이터 보관 변수
    private var analyzedResults: Map<String, Float>? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 1. 이전 ScanResultFragment에서 넘어온 번들 데이터 꺼내기
        val photoPath = arguments?.getString("photo_path") ?: ""
        val skinType  = arguments?.getString("skin_type")  ?: "건성"
        val oil       = arguments?.getInt("oil_score")       ?: 0
        val trouble   = arguments?.getInt("trouble_score")   ?: 0
        val comment   = arguments?.getString("ai_comment")   ?: ""

        @Suppress("UNCHECKED_CAST")
        val detections = (arguments?.getSerializable("detections") as? ArrayList<AcneDetection>) ?: arrayListOf()
        val imageW = arguments?.getInt("image_w") ?: 0
        val imageH = arguments?.getInt("image_h") ?: 0

        val btnSave = view.findViewById<Button>(R.id.btnSaveToDiary)
        btnSave.isEnabled = false // 분석이 완전히 완료되기 전까지 버튼 클릭 방지

        // PyTorch 네이티브 라이브러리가 없을 수 있어 try-catch 처리
        val analyzer: SkinMetricAnalyzer? = try {
            SkinMetricAnalyzer(requireContext())
        } catch (e: UnsatisfiedLinkError) {
            android.util.Log.w("DiagnosisResult", "PyTorch 라이브러리 없음, 더미 데이터 표시", e)
            null
        } catch (e: Throwable) {
            android.util.Log.w("DiagnosisResult", "SkinMetricAnalyzer 초기화 실패", e)
            null
        }

        if (photoPath.isNotEmpty() && File(photoPath).exists()) {
            val bitmap = BitmapFactory.decodeFile(photoPath)
            view.findViewById<ImageView>(R.id.ivResultPhoto).setImageBitmap(bitmap)

            if (analyzer != null && bitmap != null) {
                lifecycleScope.launch {
                    val results = withContext(Dispatchers.Default) {
                        try { analyzer.analyze(bitmap) }
                        catch (t: Throwable) { null }
                    }
                    val finalResults = results ?: getDummyResults()
                    analyzedResults = finalResults
                    updateUI(view, finalResults)
                    btnSave.isEnabled = true // 데이터 로딩이 완료되면 버튼 활성화
                }
            } else {
                val finalResults = getDummyResults()
                analyzedResults = finalResults
                updateUI(view, finalResults)
                btnSave.isEnabled = true
            }
        } else {
            val finalResults = getDummyResults()
            analyzedResults = finalResults
            updateUI(view, finalResults)
            btnSave.isEnabled = true
        }

        // 💾 기록 저장 버튼 클릭 처리 리스너 구현
        btnSave.setOnClickListener {
            val results = analyzedResults ?: return@setOnClickListener
            btnSave.isEnabled = false // 연타 및 중복 저장 방지

            // 상세 페이지에서 새롭게 계산된 4가지 지표값 가공
            val moistureScore = (results["moisture"] ?: 0f).toInt()
            val elasticityScore = ((results["elasticity"] ?: 0f) * 10).toInt() // 0.0~10.0 스케일을 0~100 점수로 변환

            val now = System.currentTimeMillis()

            // 엔티티 매핑 객체 생성 (새로 업데이트된 수분, 탄력값을 대입)
            val entity = DiagnosisEntity(
                skinType        = skinType,
                moistureScore   = moistureScore,
                oilScore        = oil,
                troubleScore    = trouble,
                elasticityScore = elasticityScore,
                aiComment       = comment,
                photoPath       = photoPath,
                isManual        = false,
                date            = now
            )

            lifecycleScope.launch {
                val db = AppDatabase.getInstance(requireContext())
                // Step 1: 기본 진단 마스터 테이블에 삽입 후 자동 생성된 ID 획득
                val diagnosisId = db.diagnosisDao().insert(entity)

                // Step 2: 트러블 좌표 위치가 담긴 디텍션 리스트가 있다면 AcneTracker로 저장 연동
                if (detections.isNotEmpty() && imageW > 0 && imageH > 0) {
                    try {
                        AcneTracker(db).track(
                            detections  = detections,
                            imageWidth  = imageW,
                            imageHeight = imageH,
                            diagnosisId = diagnosisId,
                            date        = now
                        )
                    } catch (t: Throwable) {
                        android.util.Log.e("DiagnosisResult", "AcneTracker failed", t)
                    }
                }

                // Step 3: 메인 스레드로 돌아와 토스트 띄우고 화면 오버레이 닫기
                withContext(Dispatchers.Main) {
                    val msg = if (detections.isEmpty())
                        "트러블 없이 저장되었습니다"
                    else
                        getString(R.string.result_saved)
                    Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()

                    // 메인 액티비티 콜백을 호출하여 메인 화면으로 복귀
                    (activity as? MainActivity)?.onDiagnosisSaved()
                }
            }
        }
    }

    /** PyTorch 라이브러리가 없을 때 보여줄 임시 더미 결과 */
    private fun getDummyResults(): Map<String, Float> = mapOf(
        "moisture"   to kotlin.random.Random.nextInt(45, 85).toFloat(),
        "dryness"    to kotlin.random.Random.nextInt(15, 55).toFloat(),
        "elasticity" to (kotlin.random.Random.nextInt(55, 90) / 10f),
        "pore"       to (kotlin.random.Random.nextInt(20, 70) / 10f)
    )

    private fun updateUI(view: View, results: Map<String, Float>) {
        // 1. 수분
        val moisture = results["moisture"] ?: 0f
        view.findViewById<TextView>(R.id.txtMoistureValue).text = "${moisture.toInt()}%"
        view.findViewById<ProgressBar>(R.id.pbMoisture).progress = moisture.toInt()

        // 2. 건조함
        val dryness = results["dryness"] ?: 0f
        view.findViewById<TextView>(R.id.txtDrynessValue).text = "${dryness.toInt()}%"
        view.findViewById<ProgressBar>(R.id.pbDryness).progress = dryness.toInt()

        // 3. 탄력
        val elasticity = results["elasticity"] ?: 0f
        view.findViewById<TextView>(R.id.txtElasticityValue).text = String.format("%.1f", elasticity)
        view.findViewById<ProgressBar>(R.id.pbElasticity).progress = (elasticity * 10).toInt()

        // 4. 모공
        val pore = results["pore"] ?: 0f
        view.findViewById<TextView>(R.id.txtPoreValue).text = String.format("%.1f", pore)
        view.findViewById<ProgressBar>(R.id.pbPore).progress = (pore * 10).toInt()
    }
}
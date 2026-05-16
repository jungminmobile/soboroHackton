package com.example.soboroskin

import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import com.example.soboroskin.databinding.ActivityMainBinding
import com.example.soboroskin.ui.scan.ScanFragment
import com.example.soboroskin.ui.scan.ScanResultFragment

class MainActivity : AppCompatActivity() {

    companion object {
        init {
            try {
                // 🚀 PTL 모델을 돌리기 위한 라이브러리 강제 로드
                System.loadLibrary("pytorch_jni_lite")
                System.loadLibrary("torchvision_ops_lite")
                Log.d("SkinAI", "✅ AI 엔진 부품 로드 성공")
            } catch (e: UnsatisfiedLinkError) {
                Log.e("SkinAI", "⚠️ 라이브러리 자동 로드 대기 중: ${e.message}")
            }
        }
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController
    private var isScanOpen = false
    private var activeTabIndex = 0   // 0 = 홈, 1 = 화장품

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupNavigation()
    }

    private fun setupNavigation() {
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController

        binding.tabHome.setOnClickListener {
            if (activeTabIndex != 0) {
                navController.navigate(R.id.homeFragment)
                setActiveTab(0)
            }
        }
        binding.tabCosmetics.setOnClickListener {
            if (activeTabIndex != 1) {
                navController.navigate(R.id.cosmeticsFragment)
                setActiveTab(1)
            }
        }
        binding.tabScanFab.setOnClickListener { openScanOverlay() }

        setActiveTab(0)
    }

    private fun setActiveTab(index: Int) {
        activeTabIndex = index
        val mint = ContextCompat.getColor(this, R.color.primary)
        val gray = ContextCompat.getColor(this, R.color.nav_unselected)
        binding.tabHomeIcon.setColorFilter(if (index == 0) mint else gray)
        binding.tabHomeLabel.setTextColor(if (index == 0) mint else gray)
        binding.tabCosmeticsIcon.setColorFilter(if (index == 1) mint else gray)
        binding.tabCosmeticsLabel.setTextColor(if (index == 1) mint else gray)
    }

    fun openScanOverlay() {
        if (isScanOpen) return
        isScanOpen = true
        binding.bottomTabBar.visibility = View.GONE
        supportFragmentManager.beginTransaction()
            .replace(R.id.scan_fragment_container, ScanFragment())
            .commit()

        binding.scanOverlayContainer.visibility = View.VISIBLE
        binding.scanOverlayContainer.post {
            binding.scanOverlayContainer.translationY = binding.scanOverlayContainer.height.toFloat()
            binding.scanOverlayContainer.animate().translationY(0f).setDuration(350).start()
        }
    }

    // 🚀 [추가됨] ScanFragment에서 분석이 끝나면 이 함수를 호출해서 결과창을 띄웁니다.
    fun showScanResult(
        skinType: String,
        moistureScore: Int,
        oilScore: Int,
        troubleScore: Int,
        elasticityScore: Int,
        aiComment: String,
        photoPath: String,
        detections: List<AcneDetection> = emptyList(),
        imageWidth: Int = 0,
        imageHeight: Int = 0
    ) {
        // 결과 화면 프래그먼트 생성 및 데이터 전달
        val resultFragment = ScanResultFragment.newInstance(
            skinType        = skinType,
            moistureScore   = moistureScore,
            oilScore        = oilScore,
            troubleScore    = troubleScore,
            elasticityScore = elasticityScore,
            aiComment       = aiComment,
            photoPath       = photoPath,
            detections      = detections,
            imageWidth      = imageWidth,
            imageHeight     = imageHeight
        )

        // 화면 교체
        // addToBackStack → UI 뒤로가기 버튼 / 시스템 뒤로가기로 ScanFragment 복귀 가능
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(
                R.anim.screen_enter,
                R.anim.screen_exit,
                R.anim.screen_pop_enter,
                R.anim.screen_pop_exit
            )
            .replace(R.id.scan_fragment_container, resultFragment)
            .addToBackStack("scan_result")
            .commit()
    }

    fun closeScanOverlay() {
        isScanOpen = false
        binding.scanOverlayContainer.animate()
            .translationY(binding.scanOverlayContainer.height.toFloat())
            .setDuration(300)
            .withEndAction {
                binding.scanOverlayContainer.visibility = View.GONE
                // 오버레이 닫힐 때 백스택 + 프래그먼트 전체 정리
                repeat(supportFragmentManager.backStackEntryCount) {
                    supportFragmentManager.popBackStackImmediate()
                }
                supportFragmentManager.findFragmentById(R.id.scan_fragment_container)?.let { frag ->
                    supportFragmentManager.beginTransaction().remove(frag).commit()
                }
                binding.bottomTabBar.visibility = View.VISIBLE
            }.start()
    }

    fun onDiagnosisSaved() {
        closeScanOverlay()
        navController.navigate(R.id.homeFragment)
        setActiveTab(0)
    }

    // 뒤로가기 처리
    override fun onBackPressed() {
        if (isScanOpen) {
            // 결과 화면 등 백스택이 있으면 먼저 팝 (→ 카메라로 복귀)
            if (supportFragmentManager.backStackEntryCount > 0) {
                supportFragmentManager.popBackStack()
            } else {
                closeScanOverlay()
            }
        } else {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }
}
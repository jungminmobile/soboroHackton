package com.example.soboroskin.ui.profile

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import com.example.soboroskin.auth.AuthActivity
import com.example.soboroskin.data.db.AppDatabase
import com.example.soboroskin.databinding.FragmentProfileSheetBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class ProfileBottomSheet : BottomSheetDialogFragment() {

    private var _binding: FragmentProfileSheetBinding? = null
    private val binding get() = _binding!!

    companion object {
        const val TAG = "ProfileBottomSheet"
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileSheetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val user = FirebaseAuth.getInstance().currentUser

        if (user != null) {
            val name = user.displayName?.takeIf { it.isNotEmpty() } ?: "사용자"
            val initial = name.firstOrNull()?.toString() ?: "S"

            binding.tvAvatar.text = initial
            binding.tvProfileName.text = name
            binding.tvProfileEmail.text = user.email ?: ""
            binding.tvGuestBadge.visibility = View.GONE

            // 가입 후 일수
            val createdMs = user.metadata?.creationTimestamp ?: System.currentTimeMillis()
            val days = TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - createdMs)
            binding.tvStatDays.text = "${days}일"
        } else {
            // 게스트
            binding.tvAvatar.text = "G"
            binding.tvProfileName.text = "게스트"
            binding.tvProfileEmail.text = "로그인하지 않음"
            binding.tvGuestBadge.visibility = View.VISIBLE
            binding.tvStatDays.text = "-"
        }

        // DB 통계
        val db = AppDatabase.getInstance(requireContext())
        lifecycleScope.launch {
            val count  = db.diagnosisDao().getCount()
            val latest = db.diagnosisDao().getLatestDiagnosis()

            binding.tvStatDiagnoses.text = "${count}회"
            binding.tvStatSkinType.text  = latest?.skinType?.takeIf { it.isNotEmpty() } ?: "-"
        }

        // 로그아웃
        binding.btnLogout.setOnClickListener {
            FirebaseAuth.getInstance().signOut()
            dismiss()
            startActivity(
                Intent(requireContext(), AuthActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

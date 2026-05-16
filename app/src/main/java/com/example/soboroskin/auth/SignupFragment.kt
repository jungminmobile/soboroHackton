package com.example.soboroskin.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.soboroskin.databinding.FragmentSignupBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest

class SignupFragment : Fragment() {

    private var _binding: FragmentSignupBinding? = null
    private val binding get() = _binding!!

    private val auth = FirebaseAuth.getInstance()
    private val authActivity get() = activity as? AuthActivity

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSignupBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Google에서 받아온 이름 미리 채우기
        val googleDisplayName = arguments?.getString("display_name") ?: ""
        binding.etName.setText(googleDisplayName)

        // 뒤로가기 → Firebase 계정 삭제 후 로그인 화면으로
        binding.btnBack.setOnClickListener {
            auth.currentUser?.delete()
            auth.signOut()
            parentFragmentManager.popBackStack()
        }

        // 시작하기 버튼
        binding.btnSignup.setOnClickListener {
            val nickname = binding.etName.text?.toString()?.trim() ?: ""
            if (nickname.isEmpty()) {
                binding.tvError.text = "닉네임을 입력해주세요"
                binding.tvError.visibility = View.VISIBLE
                return@setOnClickListener
            }
            binding.tvError.visibility = View.GONE
            setLoading(true)

            // Firebase 프로필에 닉네임 저장
            val profileUpdate = UserProfileChangeRequest.Builder()
                .setDisplayName(nickname)
                .build()
            auth.currentUser?.updateProfile(profileUpdate)
                ?.addOnCompleteListener {
                    // 성공 여부와 관계없이 메인으로 진행
                    authActivity?.goToMain()
                } ?: authActivity?.goToMain()
        }
    }

    private fun setLoading(isLoading: Boolean) {
        binding.btnSignup.isEnabled = !isLoading
        binding.btnSignup.alpha = if (isLoading) 0.6f else 1f
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

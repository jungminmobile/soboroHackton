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

        binding.btnBack.setOnClickListener { authActivity?.showLogin() }
        binding.btnSignup.setOnClickListener { attemptSignup() }
    }

    private fun attemptSignup() {
        val name     = binding.etName.text?.toString()?.trim() ?: ""
        val email    = binding.etEmail.text?.toString()?.trim() ?: ""
        val password = binding.etPassword.text?.toString() ?: ""
        val confirm  = binding.etPasswordConfirm.text?.toString() ?: ""

        when {
            name.isEmpty()            -> { showError("닉네임을 입력해주세요."); return }
            email.isEmpty()           -> { showError("이메일을 입력해주세요."); return }
            password.length < 6       -> { showError("비밀번호는 6자 이상이어야 해요."); return }
            password != confirm       -> { showError("비밀번호가 일치하지 않아요."); return }
        }

        binding.btnSignup.isEnabled = false
        hideError()

        auth.createUserWithEmailAndPassword(email, password)
            .addOnSuccessListener { result ->
                // displayName 설정
                val profileUpdate = UserProfileChangeRequest.Builder()
                    .setDisplayName(name)
                    .build()
                result.user?.updateProfile(profileUpdate)
                    ?.addOnCompleteListener { authActivity?.goToMain() }
                    ?: authActivity?.goToMain()
            }
            .addOnFailureListener { e ->
                binding.btnSignup.isEnabled = true
                showError(e.localizedMessage ?: "가입에 실패했어요.")
            }
    }

    private fun showError(msg: String) {
        binding.tvError.text = msg
        binding.tvError.visibility = View.VISIBLE
    }

    private fun hideError() {
        binding.tvError.visibility = View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

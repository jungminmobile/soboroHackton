package com.example.soboroskin.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.soboroskin.databinding.FragmentLoginBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException

class LoginFragment : Fragment() {

    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!

    private val auth = FirebaseAuth.getInstance()
    private val authActivity get() = activity as? AuthActivity

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnLogin.setOnClickListener { attemptLogin() }
        binding.tvGoSignup.setOnClickListener { authActivity?.showSignup() }
        binding.btnDevSkip.setOnClickListener { authActivity?.goToMain() }
    }

    private fun attemptLogin() {
        val email    = binding.etEmail.text?.toString()?.trim() ?: ""
        val password = binding.etPassword.text?.toString() ?: ""

        if (email.isEmpty() || password.isEmpty()) {
            showError("이메일과 비밀번호를 입력해주세요.")
            return
        }

        binding.btnLogin.isEnabled = false
        hideError()

        auth.signInWithEmailAndPassword(email, password)
            .addOnSuccessListener { authActivity?.goToMain() }
            .addOnFailureListener { e ->
                binding.btnLogin.isEnabled = true
                showError(mapFirebaseError(e))
            }
    }

    private fun mapFirebaseError(e: Exception): String = when (e) {
        is FirebaseAuthInvalidUserException        -> "존재하지 않는 계정이에요."
        is FirebaseAuthInvalidCredentialsException -> "이메일 또는 비밀번호가 틀렸어요."
        else                                       -> "로그인에 실패했어요. 다시 시도해주세요."
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

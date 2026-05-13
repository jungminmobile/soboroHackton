package com.example.soboroskin.ui.community

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.soboroskin.databinding.FragmentCommunityListBinding

class CommunityListFragment : Fragment() {

    private var _binding: FragmentCommunityListBinding? = null
    private val binding get() = _binding!!

    companion object {
        private const val ARG_TAB = "tab"
        fun newInstance(tabIndex: Int) = CommunityListFragment().apply {
            arguments = Bundle().apply { putInt(ARG_TAB, tabIndex) }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCommunityListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val tabIndex = arguments?.getInt(ARG_TAB) ?: 0
        val posts = getMockPosts(tabIndex)

        val adapter = CommunityAdapter(posts)
        binding.rvPosts.layoutManager = LinearLayoutManager(requireContext())
        binding.rvPosts.adapter = adapter
    }

    private fun getMockPosts(tab: Int): List<CommunityPost> {
        val allPosts = listOf(
            CommunityPost("건성 피부에 좋은 루틴 공유해요", "안녕하세요! 저처럼 건성 피부인 분들을 위해 꾸준히 효과봤던 루틴을 공유합니다...", "피부러버", "건성", "1시간 전", 42, 8),
            CommunityPost("기미 관리 방법 추천해주세요", "30대 초반인데 최근에 기미가 생기기 시작했어요. 효과적인 방법 있을까요?", "고민맘", "민감성", "3시간 전", 18, 14),
            CommunityPost("비타민C 세럼 추천 🍊", "비타민C 세럼 2년째 쓰는 사람으로서 실제 후기 남겨요. 정말 효과 있어요!", "스킨케어고수", "중성", "6시간 전", 67, 23),
            CommunityPost("여름철 지성 피부 관리 꿀팁", "지성 피부인데 여름에 번들거림이 너무 심해서 찾아낸 방법들 공유해요", "지성맘", "지성", "1일 전", 35, 11),
            CommunityPost("자외선 차단제 추천 부탁드려요", "미백 효과 있으면서 자극 없는 선크림 추천 받고 싶어요!", "새내기피부", "민감성", "2일 전", 29, 19),
            CommunityPost("밤 루틴 공유 - 6개월 전후 비교", "6개월 꾸준히 같은 루틴 유지한 결과 공유드려요. 트러블이 많이 줄었어요!", "루틴덕후", "복합성", "3일 전", 121, 45),
        )
        return when (tab) {
            0 -> allPosts.sortedByDescending { it.likes }
            1 -> allPosts
            2 -> allPosts.filter { it.title.contains("추천") || it.title.contains("방법") }
            else -> allPosts
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

data class CommunityPost(
    val title: String,
    val preview: String,
    val author: String,
    val skinType: String,
    val timeAgo: String,
    val likes: Int,
    val comments: Int
)

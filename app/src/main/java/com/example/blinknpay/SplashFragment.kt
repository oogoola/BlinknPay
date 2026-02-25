package com.example.blinknpay



import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AlphaAnimation
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.blinknpay.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import android.widget.ImageView
import android.widget.TextView

class SplashFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_splash, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val imgLogo = view.findViewById<ImageView>(R.id.imgLogo)
        val tvTagline = view.findViewById<TextView>(R.id.tvTagline)

        // 🌟 Fade-in animation for both image and text
        val fadeIn = AlphaAnimation(0f, 1f).apply {
            duration = 1500 // 1.5 seconds
            fillAfter = true
        }
        imgLogo.startAnimation(fadeIn)
        tvTagline.startAnimation(fadeIn)

        // ⏳ Wait 3 seconds, then navigate
        lifecycleScope.launch {
            delay(3000) // 3 seconds
            navigateNext()
        }
    }

    private fun navigateNext() {
        // 🔐 Replace with your logic:
        // e.g. check login or show loginFragment
        findNavController().navigate(R.id.action_splashFragment_to_loginFragment)
    }
}

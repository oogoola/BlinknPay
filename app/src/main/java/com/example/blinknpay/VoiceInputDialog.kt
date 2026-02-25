package com.example.blinknpay

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.LayoutInflater
import android.view.View
import android.view.animation.Animation
import android.view.animation.ScaleAnimation
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import java.util.*
import com.example.blinknpay.R

class VoiceInputDialog : DialogFragment() {

    interface VoiceResultListener {
        fun onVoiceResult(text: String)
    }

    private var listener: VoiceResultListener? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private lateinit var micButton: ImageView
    private lateinit var statusText: TextView
    private var pulseAnimation: Animation? = null

    // ✅ explicit setter so caller can assign listener
    fun setVoiceResultListener(l: VoiceResultListener) {
        listener = l
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (listener == null) { // prefer explicit setVoiceResultListener, but fallback
            listener = when {
                context is VoiceResultListener -> context
                parentFragment is VoiceResultListener -> parentFragment as VoiceResultListener
                else -> null
            }
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val inflater = LayoutInflater.from(context)
        val view: View = inflater.inflate(R.layout.voice_input_dialog, null)

        micButton = view.findViewById(R.id.micButton)
        statusText = view.findViewById(R.id.statusText)

        if (SpeechRecognizer.isRecognitionAvailable(requireContext())) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(requireContext())
        } else {
            statusText.text = "Speech recognition not available"
        }

        micButton.setOnClickListener { startListening() }

        return AlertDialog.Builder(requireContext(), R.style.CustomVoiceDialog)
            .setView(view)
            .setCancelable(true)
            .create()
    }

    private fun startListening() {
        val recognizer = speechRecognizer ?: return

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true) // ✅ allow live results
        }

        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                statusText.text = "Listening..."
                startPulse()
            }

            override fun onBeginningOfSpeech() {
                statusText.text = "Speak Or Blink to Pay"
            }

            override fun onRmsChanged(rmsdB: Float) {
                // could animate mic based on loudness
            }

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                statusText.text = "Processing..."
                stopPulse()
            }

            override fun onError(error: Int) {
                val message = when (error) {
                    SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                    SpeechRecognizer.ERROR_CLIENT -> "Client side error"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Mic permission missing"
                    SpeechRecognizer.ERROR_NETWORK -> "Network error"
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                    SpeechRecognizer.ERROR_NO_MATCH -> "No match found"
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy"
                    SpeechRecognizer.ERROR_SERVER -> "Server error"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input"
                    else -> "Unknown error ($error)"
                }
                statusText.text = message
                stopPulse()
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    val spokenText = matches[0]
                    listener?.onVoiceResult(spokenText)
                    dismiss()
                } else {
                    statusText.text = "Didn’t catch that"
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!partial.isNullOrEmpty()) {
                    statusText.text = partial[0] // ✅ show live transcription
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        recognizer.startListening(intent)
    }

    private fun startPulse() {
        pulseAnimation = ScaleAnimation(
            1f, 1.3f, 1f, 1.3f,
            Animation.RELATIVE_TO_SELF, 0.5f,
            Animation.RELATIVE_TO_SELF, 0.5f
        ).apply {
            duration = 600
            repeatMode = Animation.REVERSE
            repeatCount = Animation.INFINITE
        }
        micButton.startAnimation(pulseAnimation)
    }

    private fun stopPulse() {
        micButton.clearAnimation()
        pulseAnimation = null
    }

    override fun onDestroyView() {
        super.onDestroyView()
        speechRecognizer?.cancel()
        speechRecognizer?.destroy()
        stopPulse()
    }
}

package com.example.ui

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.api.ApiClient
import com.example.api.AssessmentResponse
import com.example.api.Content
import com.example.api.FileData
import com.example.api.FirestoreDocumentBody
import com.example.api.FirestoreValue
import com.example.api.FirestoreMapValue
import com.example.api.FirestoreArrayValue
import com.example.api.GenerateContentRequest
import com.example.api.GenerationConfig
import com.example.api.Part
import com.example.api.ResponseFormat
import com.example.api.ResponseFormatText
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class AppState {
    object Idle : AppState()
    object RecordingBaseline : AppState()
    object BaselineDone : AppState()
    object RecordingTarget : AppState()
    object TargetDone : AppState()
    object Processing : AppState()
    data class Success(val result: AssessmentResponse, val sessionId: String) : AppState()
    data class Error(val message: String) : AppState()
}

class VeracityViewModel : ViewModel() {
    private val _uiState = MutableStateFlow<AppState>(AppState.Idle)
    val uiState: StateFlow<AppState> = _uiState.asStateFlow()

    private var baselineVideoUri: Uri? = null
    private var targetVideoUri: Uri? = null

    fun startBaselineRecording() {
        _uiState.value = AppState.RecordingBaseline
    }

    fun onBaselineRecorded(uri: Uri?) {
        if (uri != null) {
            baselineVideoUri = uri
            _uiState.value = AppState.BaselineDone
        } else {
            _uiState.value = AppState.Idle
        }
    }

    fun startTargetRecording() {
        _uiState.value = AppState.RecordingTarget
    }

    fun onTargetRecorded(uri: Uri?) {
        if (uri != null) {
            targetVideoUri = uri
            _uiState.value = AppState.TargetDone
        } else {
            _uiState.value = AppState.BaselineDone
        }
    }

    fun processVideos(context: Context) {
        val bUri = baselineVideoUri
        val tUri = targetVideoUri
        if (bUri == null || tUri == null) return
        
        _uiState.value = AppState.Processing

        viewModelScope.launch {
            try {
                val apiKey = BuildConfig.GEMINI_API_KEY
                
                // 1. Upload both videos
                val baselineFileUri = ApiClient.uploadVideo(context, bUri)
                val targetFileUri = ApiClient.uploadVideo(context, tUri)

                // 2. Build Gemini Request
                val systemInstructionText = "You are a comparative multi-modal text and video analysis asset. Your function is to identify structural variations between a Calibration Log and a Target Log. Compare speech speed in words per minute, physical pause positioning, and basic behavioral shifts between the two files. Analyze the spoken text of the Target Log against the Calibration Log for timeline consistency and causal clarity. Output your results exactly in the following JSON format:\n" +
                        "{\n" +
                        "  \"consistencyIndex\": float (0.00 to 1.00),\n" +
                        "  \"evaluationSummary\": \"string detailing narrative stability classification\",\n" +
                        "  \"metricDeltas\": {\n" +
                        "    \"latencyVariationMs\": int,\n" +
                        "    \"rhythmShiftPercentage\": float\n" +
                        "  },\n" +
                        "  \"anomalyLog\": [\"list of identified structural or timeline variations\"]\n" +
                        "}"
                
                val req = GenerateContentRequest(
                    systemInstruction = Content(parts = listOf(Part(text = systemInstructionText))),
                    contents = listOf(
                        Content(parts = listOf(
                            Part(text = "Here is the Calibration Log (Baseline):"),
                            Part(fileData = FileData(mimeType = "video/mp4", fileUri = baselineFileUri)),
                            Part(text = "Here is the Target Log:"),
                            Part(fileData = FileData(mimeType = "video/mp4", fileUri = targetFileUri))
                        ))
                    ),
                    generationConfig = GenerationConfig(
                        temperature = 0.1f,
                        topP = 0.1f,
                        responseFormat = ResponseFormat(
                            text = ResponseFormatText(mimeType = "application/json")
                        )
                    )
                )

                val response = ApiClient.geminiService.generateContent(apiKey, req)
                val responseText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                    ?: throw Exception("Empty response from Gemini")

                val assessment = ApiClient.parseAssessmentOutput(responseText)
                    ?: throw Exception("Failed to parse JSON")

                if (assessment.evaluationSummary.contains("Inconclusive", ignoreCase = true)) {
                    _uiState.value = AppState.Error(
                        "Inconclusive Broken State: Technical limitation (e.g., poor lighting, video noise). Please ensure good lighting and clear audio."
                    )
                } else {
                    val sessionId = generateSessionId()
                    saveLocally(context, sessionId, responseText)
                    _uiState.value = AppState.Success(assessment, sessionId)
                }
            } catch (e: Exception) {
                _uiState.value = AppState.Error(e.message ?: "Unknown error occurred")
            }
        }
    }

    private fun saveLocally(context: Context, sessionId: String, rawJson: String) {
        try {
            val sharedPrefs = context.getSharedPreferences("VeracitySessions", Context.MODE_PRIVATE)
            sharedPrefs.edit().putString(sessionId, rawJson).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun generateSessionId(): String {
        return "#${(1000..9999).random()}-${('A'..'Z').random()}${('A'..'Z').random()}"
    }

    fun reset() {
        baselineVideoUri = null
        targetVideoUri = null
        _uiState.value = AppState.Idle
    }
}

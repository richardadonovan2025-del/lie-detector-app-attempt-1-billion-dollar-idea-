package com.example.api

import com.squareup.moshi.JsonClass

// --- FIRESTORE ---
@JsonClass(generateAdapter = true)
data class FirestoreDocumentBody(
    val fields: Map<String, FirestoreValue>
)

@JsonClass(generateAdapter = true)
data class FirestoreValue(
    val stringValue: String? = null,
    val doubleValue: Double? = null,
    val integerValue: String? = null,
    val arrayValue: FirestoreArrayValue? = null,
    val mapValue: FirestoreMapValue? = null
)

@JsonClass(generateAdapter = true)
data class FirestoreArrayValue(
    val values: List<FirestoreValue>?
)

@JsonClass(generateAdapter = true)
data class FirestoreMapValue(
    val fields: Map<String, FirestoreValue>?
)

// --- GEMINI FILE UPLOAD (Mock Data Classes) ---
// Note: We'll use OkHttp directly for multipart upload to File API to simplify handling raw bytes.
@JsonClass(generateAdapter = true)
data class UploadFileResponse(
    val file: UploadedFile
)

@JsonClass(generateAdapter = true)
data class UploadedFile(
    val name: String,
    val uri: String
)

// --- GEMINI REST API ---
@JsonClass(generateAdapter = true)
data class GenerateContentRequest(
    val contents: List<Content>,
    val generationConfig: GenerationConfig? = null,
    val systemInstruction: Content? = null
)

@JsonClass(generateAdapter = true)
data class Content(
    val parts: List<Part>
)

@JsonClass(generateAdapter = true)
data class Part(
    val text: String? = null,
    val inlineData: InlineData? = null,
    val fileData: FileData? = null
)

@JsonClass(generateAdapter = true)
data class InlineData(
    val mimeType: String,
    val data: String
)

@JsonClass(generateAdapter = true)
data class FileData(
    val mimeType: String,
    val fileUri: String
)

@JsonClass(generateAdapter = true)
data class GenerationConfig(
    val responseFormat: ResponseFormat? = null,
    val temperature: Float? = null,
    val topP: Float? = null
)

@JsonClass(generateAdapter = true)
data class ResponseFormat(
    val text: ResponseFormatText? = null
)

@JsonClass(generateAdapter = true)
data class ResponseFormatText(
    val mimeType: String,
    val schema: Map<String, Any>? = null // Not used with strict typing in Moshi directly if it's dynamic, but we use application/json
)

@JsonClass(generateAdapter = true)
data class GenerateContentResponse(
    val candidates: List<Candidate>? = null
)

@JsonClass(generateAdapter = true)
data class Candidate(
    val content: Content? = null
)

// Response specific to our Assessment
@JsonClass(generateAdapter = true)
data class AssessmentResponse(
    val consistencyIndex: Float,
    val evaluationSummary: String,
    val metricDeltas: MetricDeltas,
    val anomalyLog: List<String>
)

@JsonClass(generateAdapter = true)
data class MetricDeltas(
    val latencyVariationMs: Int,
    val rhythmShiftPercentage: Float
)

package com.example.api

import android.content.Context
import android.net.Uri
import com.example.BuildConfig
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit
import java.io.ByteArrayOutputStream

object ApiClient {
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    val geminiService: GeminiApiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://generativelanguage.googleapis.com/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(GeminiApiService::class.java)
    }

    val firestoreService: FirestoreApiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://firestore.googleapis.com/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(FirestoreApiService::class.java)
    }

    suspend fun uploadVideo(context: Context, videoUri: Uri): String = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        val resolver = context.contentResolver
        val inputStream = resolver.openInputStream(videoUri) ?: throw Exception("Cannot read video file")
        val bytes = inputStream.readBytes()
        inputStream.close()

        val mimeType = resolver.getType(videoUri) ?: "video/mp4"

        val metadataJson = """{"file": {"displayName": "uploaded_video.mp4"}}"""
        
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("metadata", "metadata.json", metadataJson.toRequestBody("application/json".toMediaType()))
            .addFormDataPart("file", "video.mp4", bytes.toRequestBody(mimeType.toMediaType()))
            .build()

        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/upload/v1beta/files?key=${apiKey}&uploadType=multipart")
            .header("X-Goog-Upload-Protocol", "multipart")
            .post(body)
            .build()

        val response = okHttpClient.newCall(request).execute()
        val responseBody = response.body?.string() ?: throw Exception("Empty response from File API")
        
        if (!response.isSuccessful) {
            throw Exception("Upload failed: ${responseBody}")
        }

        val uploadResponse = moshi.adapter(UploadFileResponse::class.java).fromJson(responseBody)
        return@withContext uploadResponse?.file?.uri ?: throw Exception("Failed to parse upload response uri")
    }

    fun parseAssessmentOutput(jsonStr: String): AssessmentResponse? {
        // Find JSON block in markdown
        var stringToParse = jsonStr
        if (stringToParse.contains("```json")) {
            stringToParse = stringToParse.substringAfter("```json").substringBefore("```")
        } else if (stringToParse.contains("```")) {
            stringToParse = stringToParse.substringAfter("```").substringBefore("```")
        }
        return moshi.adapter(AssessmentResponse::class.java).fromJson(stringToParse.trim())
    }
}

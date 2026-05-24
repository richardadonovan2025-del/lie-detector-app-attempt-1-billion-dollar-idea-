package com.example.api

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.Part

interface GeminiApiService {
    @POST("v1beta/models/gemini-1.5-pro:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Body request: GenerateContentRequest
    ): GenerateContentResponse
}

interface FirestoreApiService {
    @POST("v1/projects/{projectId}/databases/(default)/documents/sessions")
    suspend fun createSession(
        @Path("projectId") projectId: String,
        @Query("key") apiKey: String,
        @Body document: FirestoreDocumentBody
    )
}

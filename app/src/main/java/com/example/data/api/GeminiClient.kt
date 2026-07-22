package com.example.data.api

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object GeminiClient {
    private const val MODEL = "gemini-3.5-flash"
    private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/$MODEL:generateContent"

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun generateContent(
        apiKey: String,
        systemInstruction: String,
        prompt: String
    ): String = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext "Error: Gemini API Key is missing. Please configure it in Settings."
        }

        val url = "$BASE_URL?key=$apiKey"
        Log.d("GeminiClient", "Sending request to Gemini API (Model: $MODEL)")

        val jsonRequest = JSONObject()

        // Construct contents
        val contentsArray = JSONArray()
        val contentObj = JSONObject()
        contentObj.put("role", "user")
        val partsArray = JSONArray()
        val partObj = JSONObject()
        partObj.put("text", prompt)
        partsArray.put(partObj)
        contentObj.put("parts", partsArray)
        contentsArray.put(contentObj)
        jsonRequest.put("contents", contentsArray)

        // Construct systemInstruction if present
        if (systemInstruction.isNotBlank()) {
            val systemInstructionObj = JSONObject()
            val systemPartsArray = JSONArray()
            val systemPartObj = JSONObject()
            systemPartObj.put("text", systemInstruction)
            systemPartsArray.put(systemPartObj)
            systemInstructionObj.put("parts", systemPartsArray)
            jsonRequest.put("systemInstruction", systemInstructionObj)
        }

        // Add generationConfig to control creativity
        val configObj = JSONObject()
        configObj.put("temperature", 0.7)
        jsonRequest.put("generationConfig", configObj)

        val requestBodyString = jsonRequest.toString()
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val body = requestBodyString.toRequestBody(mediaType)

        val request = Request.Builder()
            .url(url)
            .post(body)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val responseBodyStr = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    Log.e("GeminiClient", "API call failed. Code: ${response.code}, Body: $responseBodyStr")
                    val errorMsg = try {
                        val errJson = JSONObject(responseBodyStr)
                        errJson.getJSONObject("error").getString("message")
                    } catch (e: Exception) {
                        "HTTP ${response.code}: ${response.message}"
                    }
                    return@withContext "Error from Gemini API: $errorMsg"
                }

                if (responseBodyStr.isBlank()) {
                    return@withContext "Error: Received empty response from Gemini API."
                }

                val jsonResponse = JSONObject(responseBodyStr)
                val candidates = jsonResponse.optJSONArray("candidates")
                if (candidates == null || candidates.length() == 0) {
                    return@withContext "Gemini response was empty. No candidates generated."
                }

                val firstCandidate = candidates.getJSONObject(0)
                val responseContent = firstCandidate.optJSONObject("content")
                if (responseContent == null) {
                    return@withContext "No content found in the first candidate response."
                }

                val parts = responseContent.optJSONArray("parts")
                if (parts == null || parts.length() == 0) {
                    return@withContext "No parts found in response content."
                }

                return@withContext parts.getJSONObject(0).optString("text", "No text found in parts.")
            }
        } catch (e: Exception) {
            Log.e("GeminiClient", "Exception during Gemini generation", e)
            return@withContext "Error generating content: ${e.localizedMessage ?: "Unknown connection error"}"
        }
    }
}

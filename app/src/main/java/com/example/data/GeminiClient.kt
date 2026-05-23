package com.example.data

import com.example.BuildConfig
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

// --- Gemini Request/Response Data Models (via Moshi) ---

@JsonClass(generateAdapter = true)
data class InlineData(
    @Json(name = "mimeType") val mimeType: String,
    @Json(name = "data") val data: String
)

@JsonClass(generateAdapter = true)
data class GeminiPart(
    @Json(name = "text") val text: String? = null,
    @Json(name = "inlineData") val inlineData: InlineData? = null
)

@JsonClass(generateAdapter = true)
data class GeminiContent(
    @Json(name = "parts") val parts: List<GeminiPart>
)

@JsonClass(generateAdapter = true)
data class GenerationConfig(
    @Json(name = "responseMimeType") val responseMimeType: String = "application/json",
    @Json(name = "temperature") val temperature: Float = 0.2f
)

@JsonClass(generateAdapter = true)
data class GeminiRequest(
    @Json(name = "contents") val contents: List<GeminiContent>,
    @Json(name = "generationConfig") val generationConfig: GenerationConfig = GenerationConfig()
)

@JsonClass(generateAdapter = true)
data class GeminiResponse(
    @Json(name = "candidates") val candidates: List<Candidate>?
)

@JsonClass(generateAdapter = true)
data class Candidate(
    @Json(name = "content") val content: GeminiContent?
)

// --- Extracted Document Structured Output ---

@JsonClass(generateAdapter = true)
data class ParsedField(
    @Json(name = "key") val key: String,
    @Json(name = "value") val value: String,
    @Json(name = "confidence") val confidence: Float
)

@JsonClass(generateAdapter = true)
data class ExtractedDocResult(
    @Json(name = "documentType") val documentType: String,
    @Json(name = "fields") val fields: List<ParsedField>
)

// --- Retrofit API Service ---

interface GeminiApiService {
    @POST("v1beta/models/gemini-3.5-flash:generateContent")
    suspend fun extractDocument(
        @Query("key") apiKey: String,
        @Body request: GeminiRequest
    ): GeminiResponse
}

// --- Gemini Client Object ---

object GeminiClient {
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"

    val moshi: Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        })
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    val service: GeminiApiService = retrofit.create(GeminiApiService::class.java)

    /**
     * Parse document image (Base64 JPEG) using Gemini API to perform structuring and OCR.
     */
    suspend fun parseDocument(base64Image: String): ExtractedDocResult {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            throw IllegalStateException("API Key is missing or default. Please configure via Secrets panel.")
        }

        val prompt = """
            You are an expert OCR and logistics scanner. Formulate a structured JSON of the extracted fields.
            Detect the category: 'Fuel Receipt', 'Bill of Lading', 'Delivery Receipt', or 'Other'.
            Identify and extract key values, assigning a subjective high-confidence percentage (e.g. 0.95 for 95%, up to 1.0) and key labels.
            For 'Fuel Receipt' extract: Date, Supplier, Invoice Number, Gallons, Fuel Rate, Total Price, Driver Name, Vehicle ID.
            For 'Bill of Lading' extract: Date, BOL Number, Shipper, Consignee, Carrier Name, Commodity, Weight, Driver Name.
            For 'Delivery Receipt' extract: Date, Delivery Number, Shipper, Recipient Name, Total Price, Driver Name.
            Always output a JSON object exactly matching the schema:
            {
              "documentType": "Fuel Receipt" | "Bill of Lading" | "Delivery Receipt" | "Other",
              "fields": [
                {
                  "key": "Invoice Number" | "Total Price" | "Gallons" | "Date" | "Driver Name" | "Carrier Name" | "Fuel Rate" | etc,
                  "value": "string value extracted",
                  "confidence": float indicating subjective extraction confidence between 0.0 and 1.0
                }
              ]
            }
            Do NOT include markdown formatting in your response. Return ONLY raw valid JSON text.
        """.trimIndent()

        val request = GeminiRequest(
            contents = listOf(
                GeminiContent(
                    parts = listOf(
                        GeminiPart(text = prompt),
                        GeminiPart(inlineData = InlineData(mimeType = "image/jpeg", data = base64Image))
                    )
                )
            )
        )

        val response = service.extractDocument(apiKey, request)
        val rawJson = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            ?: throw IllegalStateException("Empty response from AI engine")

        // Clean json from markdown coding blocks if any
        val cleanJson = rawJson.trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()

        return moshi.adapter(ExtractedDocResult::class.java).fromJson(cleanJson)
            ?: throw IllegalStateException("Failed to parse extracted JSON")
    }
}

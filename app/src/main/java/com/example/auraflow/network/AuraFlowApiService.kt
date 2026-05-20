package com.example.auraflow.network

import okhttp3.MultipartBody
import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path

interface AuraFlowApiService {
    @GET("assistants")
    suspend fun listAssistants(): List<BackboardAssistant>

    @GET("threads")
    suspend fun listThreads(): List<BackboardThread>

    @GET("threads/{thread_id}")
    suspend fun getThread(@Path("thread_id") threadId: String): BackboardThreadDetails

    @Headers("Content-Type: application/json")
    @POST("assistants/{assistant_id}/threads")
    suspend fun createThreadForAssistant(
        @Path("assistant_id") assistantId: String,
        @Body body: Map<String, String> = emptyMap()
    ): BackboardThread

    @Headers("Content-Type: application/json")
    @POST("threads/{thread_id}/messages")
    suspend fun getCompletions(
        @Path("thread_id") threadId: String,
        @Body request: BackboardRequest
    ): BackboardResponse

    @Headers("Content-Type: application/json")
    @POST("threads/{thread_id}/runs/{run_id}/submit-tool-outputs")
    suspend fun submitToolOutputs(
        @Path("thread_id") threadId: String,
        @Path("run_id") runId: String,
        @Body request: ToolOutputRequest
    ): BackboardResponse

    @Multipart
    @POST("threads/{thread_id}/files")
    suspend fun uploadDocumentToThread(
        @Path("thread_id") threadId: String,
        @Part file: MultipartBody.Part
    ): DocumentResponse

    @Multipart
    @POST("files")
    suspend fun uploadFile(
        @Part file: MultipartBody.Part
    ): DocumentResponse
}

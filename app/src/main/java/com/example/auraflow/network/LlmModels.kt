package com.example.auraflow.network

import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName

data class BackboardRequest(
    @SerializedName("content") val content: String,
    @SerializedName("role") val role: String = "user",
    @SerializedName("send_to_llm") val sendToLlm: Boolean = true,
    @SerializedName("memory_pro") val memoryPro: String = "Auto",
    @SerializedName("llm_provider") val llmProvider: String? = null,
    @SerializedName("model_name") val modelName: String? = null,
    @SerializedName("web_search") val webSearch: String? = null,
    @SerializedName("thinking") val thinking: Map<String, Any>? = null
)

data class BackboardResponse(
    @SerializedName("id") val id: String?,
    @SerializedName("content") val content: JsonElement?,
    @SerializedName("status") val status: String?,
    @SerializedName("run_id") val runId: String?,
    @SerializedName("tool_calls") val toolCalls: List<ToolCall>?,
    @SerializedName("reasoning") val reasoning: String?
)

data class ToolCall(
    @SerializedName("id") val id: String,
    @SerializedName("type") val type: String,
    @SerializedName("function") val function: FunctionCall
)

data class FunctionCall(
    @SerializedName("name") val name: String,
    @SerializedName("arguments") val arguments: String
)

data class ToolOutputRequest(
    @SerializedName("tool_outputs") val toolOutputs: List<ToolOutput>
)

data class ToolOutput(
    @SerializedName("tool_call_id") val toolCallId: String,
    @SerializedName("output") val output: String
)

data class ContentItem(
    @SerializedName("type") val type: String?,
    @SerializedName("text") val text: TextValue?
)

data class TextValue(
    @SerializedName("value") val value: String?
)

data class BackboardThread(
    @SerializedName("thread_id") val threadId: String?
)

data class BackboardAssistant(
    @SerializedName("assistant_id") val assistantId: String?
)

data class BackboardThreadDetails(
    @SerializedName("thread_id") val threadId: String?,
    @SerializedName("messages") val messages: List<BackboardMessage>?
)

data class BackboardMessage(
    @SerializedName("role") val role: String?,
    @SerializedName("content") val content: JsonElement?
)

data class DocumentResponse(
    @SerializedName("document_id") val documentId: String?,
    @SerializedName("status") val status: String?
)


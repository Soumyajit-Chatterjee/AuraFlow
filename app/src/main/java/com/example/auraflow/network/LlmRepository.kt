package com.example.auraflow.network

import android.content.Context
import com.example.auraflow.data.model.MemoryState
import com.google.gson.JsonElement
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okio.Buffer

class LlmRepository(
    private val apiService: AuraFlowApiService,
    context: Context
) {
    private val prefs = context.getSharedPreferences("auraflow_threads", Context.MODE_PRIVATE)

    private val systemPrompt = """
        You are Aura-Flow Shadow, a high-performance tactical assistant for Soumyajit.
        You bridge physical performance (The Iron) and cognitive output (The Ink).
        Target Context: User is specializing in Data Science with final exams in May 2026.
        Tone: minimalist, direct, helpful.

        Hard rules:
        - Use Markdown.
        - Output <= 160 words unless asked for deep detail.
        - No GDG references.
        - If fatigue is high, reduce intensity and prioritize consistency.

        Output format:
        - For routine questions (greetings, simple queries, general chat): Reply naturally in plain text.
        - For performance/focus questions (workout plans, study plans, goal tracking): Use the structured format below.

        Structured format (only for performance-related queries):
        ## Status
        - Phys: low|med|high
        - Cog: low|med|high
        - Momentum: low|med|high

        ## Focus Now
        - One highest-priority target.

        ## Next Block (30-90m)
        - Timeboxed steps with measurable output.

        ## Risk Check
        - Likely failure mode + countermeasure.

        You are the bridge between his physical performance (The Iron) and his cognitive goals (The Ink).
    """.trimIndent()

    suspend fun sendMessage(
        userMessage: String,
        mode: String,
        memoryContext: List<MemoryState>,
        laneMemories: List<MemoryState>,
        fatigueScore: Int,
        webSearchEnabled: Boolean = false,
        useThinking: Boolean = false,
        selectedModel: String? = null,
        selectedProvider: String? = null
    ): Pair<String, String?> {
        return withContext(Dispatchers.IO) {
            try {
                val targetLane = resolveLane(mode, userMessage)
                val targetThreadId = getOrCreateThreadForLane(targetLane)
                    ?: throw IllegalStateException("Unable to resolve a Backboard thread for lane: $targetLane.")

                val synthesisContext = if (targetLane == LANE_SYNTHESIS) buildCrossThreadContext() else ""
                val recentLane = laneMemories.take(3).joinToString("\n") { "- ${it.content.take(120)}" }
                val recentGlobal = memoryContext.take(4).joinToString("\n") { "- ${it.content.take(120)}" }
                val fatigueLabel = when {
                    fatigueScore >= 75 -> "high"
                    fatigueScore >= 45 -> "med"
                    else -> "low"
                }

                val contentToSend = """
                    [SYSTEM PROMPT]
                    $systemPrompt

                    [LANE]
                    $targetLane

                    [FATIGUE FILTER]
                    fatigue_score=$fatigueScore
                    fatigue_level=$fatigueLabel
                    Rule: if fatigue_level=high, propose lower-load plan for current lane.

                    [RECENT LANE MEMORY]
                    $recentLane

                    [RECENT GLOBAL MEMORY]
                    $recentGlobal

                    [CROSS-THREAD CONTEXT]
                    $synthesisContext

                    [USER INPUT]
                    $userMessage
                """.trimIndent()

                val thinkingParam = if (useThinking) mapOf("effort" to "high") else null
                val webSearchParam = if (webSearchEnabled) "Auto" else "off"

                val request = BackboardRequest(
                    content = contentToSend,
                    sendToLlm = true,
                    memoryPro = "Auto",
                    llmProvider = selectedProvider,
                    modelName = selectedModel,
                    webSearch = webSearchParam,
                    thinking = thinkingParam
                )

                var response = sendWithModelFallback(targetThreadId, request, targetLane)
                
                // --- TOOL CALLING LOOP ---
                while (response.status == "REQUIRES_ACTION" && !response.toolCalls.isNullOrEmpty()) {
                    val outputs = response.toolCalls.map { call ->
                        val res = dispatchMockTool(call.function.name, call.function.arguments)
                        ToolOutput(call.id, res)
                    }
                    val currentRunId = response.runId ?: break
                    response = apiService.submitToolOutputs(targetThreadId, currentRunId, ToolOutputRequest(outputs))
                }

                val finalContent = extractAssistantText(response.content) ?: "Error: Empty response string from Backboard."
                Pair(finalContent, response.reasoning)
            } catch (e: retrofit2.HttpException) {
                val errorBody = e.response()?.errorBody()?.string() ?: "Unknown Http Error"
                android.util.Log.e("AuraFlowLLM", "HTTP ${e.code()}: $errorBody")
                // Detect context limit errors
                val lowerError = errorBody.lowercase()
                if (lowerError.contains("context") || lowerError.contains("too long") || lowerError.contains("exceeded")) {
                    Pair("COM_LINK_ERROR: Context limit exceeded. Try sending a shorter message. Details: $errorBody", null)
                } else {
                    Pair("COM_LINK_ERROR: HTTP ${e.code()} - $errorBody", null)
                }
            } catch (e: Exception) {
                android.util.Log.e("AuraFlowLLM", "Exception: ${e.message}", e)
                Pair("COM_LINK_ERROR: ${e.message}", null)
            }
        }
    }

    private fun dispatchMockTool(name: String, args: String): String {
        return when (name) {
            "log_fitness_record" -> "{\"status\":\"success\", \"message\":\"Logged new physical record.\"}"
            "check_weather" -> "{\"condition\":\"clear skies, optimal for training\", \"temperature_c\":24}"
            else -> "{\"status\":\"executed\", \"action\":\"$name\"}"
        }
    }

    suspend fun uploadContextDocument(filePart: okhttp3.MultipartBody.Part, mode: String): String {
        return withContext(Dispatchers.IO) {
            try {
                // Resolve lane from mode
                val lane = when (mode.uppercase()) {
                    "IRON" -> "IRON"
                    "INK" -> "INK"
                    else -> "INK"
                }

                android.util.Log.d("AuraFlowUpload", "Resolving lane: $lane from mode: $mode")

                var threadId = getOrCreateThreadForLane(lane)

                // If still no thread, try to force create one
                if (threadId.isNullOrBlank()) {
                    android.util.Log.d("AuraFlowUpload", "No thread found, trying to create one...")
                    threadId = recoverThreadIdFromAccount(lane)
                }

                if (threadId.isNullOrBlank()) {
                    android.util.Log.e("AuraFlowUpload", "Could not find or create thread for lane: $lane")
                    return@withContext "Error: No thread found. Please send a message in the chat first to create a thread."
                }

                android.util.Log.d("AuraFlowUpload", "Using thread: $threadId for lane: $lane")

                // List available threads to verify
                try {
                    val threads = apiService.listThreads()
                    android.util.Log.d("AuraFlowUpload", "Available threads: ${threads.map { it.threadId }}")
                } catch (e: Exception) {
                    android.util.Log.w("AuraFlowUpload", "Could not list threads: ${e.message}")
                }

                android.util.Log.d("AuraFlowUpload", "Uploading to thread: $threadId")

                // Try first endpoint
                var lastError: Exception? = null
                try {
                    val response = apiService.uploadFile(filePart)
                    return@withContext "Document [${response.documentId}] uploaded. Status: ${response.status}"
                } catch (e: Exception) {
                    lastError = e
                    android.util.Log.w("AuraFlowUpload", "uploadFile failed: ${e.message}")
                }

                // Try second endpoint
                try {
                    val response = apiService.uploadDocumentToThread(threadId, filePart)
                    return@withContext "Document [${response.documentId}] uploaded. Status: ${response.status}"
                } catch (e: Exception) {
                    lastError = e
                    android.util.Log.w("AuraFlowUpload", "uploadDocumentToThread failed: ${e.message}")
                }

                // If all endpoints fail, try to read file content and send as message
                android.util.Log.d("AuraFlowUpload", "All file upload endpoints failed, trying message approach")

                // Extract file content from the MultipartBody.Part
                val fileContent = try {
                    val body = filePart.body
                    if (body != null) {
                        val buffer = okio.Buffer()
                        body.writeTo(buffer)
                        buffer.readUtf8().take(10000) // Limit to 10k chars
                    } else ""
                } catch (e: Exception) {
                    android.util.Log.e("AuraFlowUpload", "Could not read file content: ${e.message}")
                    ""
                }

                if (fileContent.isNotBlank()) {
                    // Send as message instead
                    val request = BackboardRequest(
                        content = "[DOCUMENT CONTENT]\n$fileContent\n\nPlease summarize this document.",
                        sendToLlm = true,
                        memoryPro = "Auto",
                        llmProvider = null,
                        modelName = null,
                        webSearch = null,
                        thinking = null
                    )
                    val msgResponse = apiService.getCompletions(threadId, request)
                    val content = extractAssistantText(msgResponse.content) ?: "Document processed"
                    return@withContext "Document processed as message: $content"
                }

                return@withContext "Upload failed: ${lastError?.message ?: "All endpoints returned 404"}"
            } catch (e: retrofit2.HttpException) {
                val code = e.code()
                val rawErrorBody = e.response()?.errorBody()?.string() ?: "No details"
                android.util.Log.e("AuraFlowUpload", "Error $code: $rawErrorBody")

                // Try to parse JSON error more robustly
                val errorMessage = try {
                    if (rawErrorBody.startsWith("{")) {
                        // Try to extract message field
                        val jsonElement = com.google.gson.Gson().fromJson(rawErrorBody, JsonElement::class.java)
                        val jsonObject = jsonElement.asJsonObject
                        jsonObject.get("message")?.asString
                            ?: jsonObject.get("error")?.asString
                            ?: jsonObject.toString()
                    } else {
                        rawErrorBody
                    }
                } catch (parseEx: Exception) {
                    rawErrorBody
                }

                when (code) {
                    400 -> "Upload failed (400): $errorMessage"
                    401 -> "Upload failed (401): Unauthorized. Check API key."
                    403 -> "Upload failed (403): Forbidden. Insufficient permissions."
                    404 -> "Upload failed (404): Thread not found."
                    413 -> "Upload failed (413): File too large."
                    else -> "Upload failed ($code): $errorMessage"
                }
            } catch (e: Exception) {
                "Upload Error: ${e.message}"
            }
        }
    }

    suspend fun processDocumentAsMessage(content: String, fileName: String, mode: String): String {
        return withContext(Dispatchers.IO) {
            try {
                val lane = when (mode.uppercase()) {
                    "IRON" -> "IRON"
                    "INK" -> "INK"
                    else -> "INK"
                }

                var threadId = getOrCreateThreadForLane(lane)
                if (threadId.isNullOrBlank()) {
                    threadId = recoverThreadIdFromAccount(lane)
                }

                if (threadId.isNullOrBlank()) {
                    return@withContext "Error: No thread. Send a message first."
                }

                android.util.Log.d("AuraFlowDoc", "Processing document: $fileName (${content.length} chars)")

                // Send document content with proper prompt
                val prompt = """
                    [DOCUMENT: $fileName]
                    Please analyze and summarize the following document content:

                    $content

                    Provide:
                    1. Main topic/key points
                    2. Key takeaways (3-5 bullets)
                    3. Any action items or recommendations
                """.trimIndent()

                val request = BackboardRequest(
                    content = prompt,
                    sendToLlm = true,
                    memoryPro = "Auto",
                    llmProvider = null,
                    modelName = null,
                    webSearch = null,
                    thinking = null
                )

                val response = apiService.getCompletions(threadId, request)
                val result = extractAssistantText(response.content)
                    ?: "Could not process document"

                android.util.Log.d("AuraFlowDoc", "Document processed successfully")
                result

            } catch (e: Exception) {
                android.util.Log.e("AuraFlowDoc", "Error: ${e.message}", e)
                "Error processing document: ${e.message}"
            }
        }
    }

    private suspend fun sendWithAutoThreadRecovery(
        threadId: String,
        request: BackboardRequest,
        lane: String
    ): BackboardResponse {
        return try {
            apiService.getCompletions(threadId, request)
        } catch (e: retrofit2.HttpException) {
            if (e.code() != 404) throw e

            val recoveredThreadId = recoverThreadIdFromAccount(lane)
            if (recoveredThreadId == null) {
                throw IllegalStateException(
                    "Thread not found and no threads are available for this API key. " +
                        "Create a thread in Backboard, then retry."
                )
            }

            persistThreadIdForLane(lane, recoveredThreadId)
            apiService.getCompletions(recoveredThreadId, request)
        }
    }

    private suspend fun sendWithModelFallback(
        threadId: String,
        request: BackboardRequest,
        lane: String
    ): BackboardResponse {
        var currentRequest = request
        var attemptCount = 0
        val maxAttempts = 3

        while (attemptCount < maxAttempts) {
            try {
                val response = sendWithAutoThreadRecovery(threadId, currentRequest, lane)

                // Check if the response contained a soft-fail inside the content
                val extractedText = extractAssistantText(response.content) ?: ""
                val lowerText = extractedText.lowercase()
                val isSoftFail = extractedText.startsWith("LLM Error:") && extractedText.contains("supported models", ignoreCase = true)
                val isContextExceeded = lowerText.contains("context_length_exceeded") ||
                    lowerText.contains("context length") ||
                    lowerText.contains("exceeds context limit") ||
                    lowerText.contains("reduced_prompt")

                if (isSoftFail || isContextExceeded) {
                    android.util.Log.w("AuraFlowLLM", "Context limit detected in response, attempt ${attemptCount + 1}")
                    // Fallback with reduced context
                    val fallbackRequest = request.copy(
                        content = reduceContextSize(request.content),
                        llmProvider = null,
                        modelName = null,
                        thinking = null,
                        webSearch = null
                    )
                    return sendWithAutoThreadRecovery(threadId, fallbackRequest, lane)
                }
                return response
            } catch (e: retrofit2.HttpException) {
                val errorBodyRaw = e.response()?.errorBody()?.string() ?: ""
                val errorBodyLower = errorBodyRaw.lowercase()

                android.util.Log.w("AuraFlowLLM", "HTTP ${e.code()}: $errorBodyRaw")

                // Check for context_length_exceeded error (various formats)
                if (errorBodyLower.contains("context_length_exceeded") ||
                    errorBodyLower.contains("context length") ||
                    errorBodyLower.contains("exceeds context limit") ||
                    errorBodyLower.contains("too long") ||
                    errorBodyLower.contains("exceeded")) {
                    attemptCount++
                    if (attemptCount >= maxAttempts) {
                        throw RuntimeException("HTTP ${e.code()} - Context limit exceeded after $maxAttempts attempts: $errorBodyRaw", e)
                    }
                    android.util.Log.w("AuraFlowLLM", "Context limit detected, reducing and retrying (attempt $attemptCount)")
                    // Reduce context and retry
                    currentRequest = request.copy(content = reduceContextSize(request.content))
                    continue
                }

                // If it's a 400 error, it may be due to unsupported model/file/search combinations.
                if (e.code() >= 400) {
                    try {
                        val fallbackRequest = request.copy(
                            llmProvider = null,
                            modelName = null,
                            thinking = null,
                            webSearch = null
                        )
                        return sendWithAutoThreadRecovery(threadId, fallbackRequest, lane)
                    } catch (fallbackEx: retrofit2.HttpException) {
                        val fallbackErrorRaw = fallbackEx.response()?.errorBody()?.string() ?: ""
                        throw RuntimeException("HTTP ${fallbackEx.code()} - $fallbackErrorRaw \nOriginal: $errorBodyRaw", fallbackEx)
                    }
                }
                throw RuntimeException("HTTP ${e.code()} - $errorBodyRaw", e)
            }
        }

        throw RuntimeException("Max retry attempts reached")
    }

    private fun reduceContextSize(content: String): String {
        // Remove memory sections entirely to drastically reduce context size
        var reduced = content
            .replace(Regex("(?s)\\[RECENT LANE MEMORY\\].*?(?=\\n\\[[A-Z]|$)"), "[RECENT LANE MEMORY]\n(reduced for context limit)")
            .replace(Regex("(?s)\\[RECENT GLOBAL MEMORY\\].*?(?=\\n\\[[A-Z]|$)"), "[RECENT GLOBAL MEMORY]\n(reduced for context limit)")
            .replace(Regex("(?s)\\[CROSS-THREAD CONTEXT\\].*?(?=\\n\\[[A-Z]|$)"), "[CROSS-THREAD CONTEXT]\n(reduced for context limit)")

        // Aggressively truncate if still large
        return if (reduced.length > 4000) {
            // Find the USER INPUT section and keep only system prompt + user input
            val userIdx = reduced.indexOf("[USER INPUT]")
            if (userIdx > 0) {
                val before = reduced.substring(0, userIdx)
                val after = reduced.substring(userIdx)
                // Keep first 2000 chars of system context + user input
                before.take(2000) + after
            } else {
                reduced.take(4000)
            }
        } else {
            reduced
        }
    }

    private suspend fun recoverThreadIdFromAccount(lane: String): String? {
        try {
            val threads = apiService.listThreads()
            android.util.Log.d("AuraFlowUpload", "listThreads returned: $threads")

            val existingThreadId = threads.firstOrNull { !it.threadId.isNullOrBlank() }?.threadId
            if (!existingThreadId.isNullOrBlank()) {
                android.util.Log.d("AuraFlowUpload", "Found existing thread: $existingThreadId")
                persistThreadIdForLane(lane, existingThreadId)
                return existingThreadId
            }

            val assistants = apiService.listAssistants()
            android.util.Log.d("AuraFlowUpload", "listAssistants returned: $assistants")

            val assistantId = assistants.firstOrNull { !it.assistantId.isNullOrBlank() }?.assistantId
            if (assistantId == null) {
                android.util.Log.e("AuraFlowUpload", "No assistants found!")
                return null
            }

            val newThread = apiService.createThreadForAssistant(assistantId)
            android.util.Log.d("AuraFlowUpload", "Created new thread: ${newThread.threadId}")

            return newThread.threadId?.also {
                persistThreadIdForLane(lane, it)
            }
        } catch (e: Exception) {
            android.util.Log.e("AuraFlowUpload", "Error in recoverThreadIdFromAccount: ${e.message}", e)
            return null
        }
    }

    private suspend fun getOrCreateThreadForLane(lane: String): String? {
        val saved = prefs.getString(prefKeyForLane(lane), null)
        if (!saved.isNullOrBlank()) return saved

        val recovered = recoverThreadIdFromAccount(lane)
        if (!recovered.isNullOrBlank()) return recovered

        return null
    }

    private fun persistThreadIdForLane(lane: String, threadId: String) {
        prefs.edit().putString(prefKeyForLane(lane), threadId).apply()
    }

    private fun prefKeyForLane(lane: String): String = "thread_${lane.lowercase()}"

    private fun resolveLane(mode: String, message: String): String {
        if (mode.equals("IRONK", ignoreCase = true)) return LANE_SYNTHESIS
        if (isCrossThreadQuery(message)) return LANE_SYNTHESIS
        return when (mode.uppercase()) {
            "IRON", "GYM" -> LANE_IRON
            "INK", "STUDY" -> LANE_INK
            else -> LANE_INK
        }
    }

    // Model routing is intentionally disabled because supported model IDs vary across accounts.
    // Backboard will choose a compatible default model automatically.

    private fun isCrossThreadQuery(message: String): Boolean {
        val lower = message.lowercase()
        val hasIron = listOf("workout", "gym", "lift", "deadlift", "squat", "fatigue").any { lower.contains(it) }
        val hasInk = listOf("study", "exam", "prep", "math", "reasoning").any { lower.contains(it) }
        return hasIron && hasInk
    }

    private suspend fun buildCrossThreadContext(): String {
        val ironThread = prefs.getString(prefKeyForLane(LANE_IRON), null)
        val inkThread = prefs.getString(prefKeyForLane(LANE_INK), null)

        val ironSummary = extractLatestFromThread(ironThread)
        val inkSummary = extractLatestFromThread(inkThread)

        return """
            Iron latest:
            ${ironSummary.ifBlank { "No Iron history yet." }}

            Ink latest:
            ${inkSummary.ifBlank { "No Ink history yet." }}
        """.trimIndent()
    }

    private suspend fun extractLatestFromThread(threadId: String?): String {
        if (threadId.isNullOrBlank()) return ""
        return try {
            val thread = apiService.getThread(threadId)
            val latest = thread.messages.orEmpty().takeLast(6).mapNotNull { msg ->
                val text = extractAssistantText(msg.content)
                if (text.isNullOrBlank()) null else "${msg.role ?: "unknown"}: $text"
            }
            latest.joinToString("\n")
        } catch (_: Exception) {
            ""
        }
    }

    private fun extractAssistantText(content: JsonElement?): String? {
        if (content == null || content.isJsonNull) return null

        if (content.isJsonPrimitive) {
            return content.asString
        }

        if (content.isJsonObject) {
            val obj = content.asJsonObject

            val directValue = obj.get("value")
            if (directValue != null && directValue.isJsonPrimitive) return directValue.asString

            val textField = obj.get("text")
            if (textField != null) {
                if (textField.isJsonPrimitive) return textField.asString
                if (textField.isJsonObject) {
                    val nestedValue = textField.asJsonObject.get("value")
                    if (nestedValue != null && nestedValue.isJsonPrimitive) return nestedValue.asString
                }
            }
            return null
        }

        if (content.isJsonArray) {
            for (item in content.asJsonArray) {
                val extracted = extractAssistantText(item)
                if (!extracted.isNullOrBlank()) return extracted
            }
        }

        return null
    }

    private companion object {
        const val LANE_IRON = "IRON"
        const val LANE_INK = "INK"
        const val LANE_SYNTHESIS = "SYNTHESIS"
    }
}

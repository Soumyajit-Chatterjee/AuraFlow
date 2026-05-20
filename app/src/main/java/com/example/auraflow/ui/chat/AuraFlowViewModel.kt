package com.example.auraflow.ui.chat

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.auraflow.data.BackboardDatabase
import com.example.auraflow.data.model.MemoryState
import com.example.auraflow.network.LlmRepository
import com.example.auraflow.network.RetrofitClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

data class UiMessage(
    val content: String,
    val isUser: Boolean,
    val reasoning: String? = null
)

class AuraFlowViewModel(application: Application) : AndroidViewModel(application) {

    private val memoryDao = BackboardDatabase.getDatabase(application).memoryDao()
    private val repository = LlmRepository(RetrofitClient.apiService, application)

    private val _currentMode = MutableStateFlow("IRON")
    val currentMode: StateFlow<String> = _currentMode.asStateFlow()

    private val _fatigueScore = MutableStateFlow(0)
    val fatigueScore: StateFlow<Int> = _fatigueScore.asStateFlow()

    private val _memoryRecall = MutableStateFlow("No indexed recall yet.")
    val memoryRecall: StateFlow<String> = _memoryRecall.asStateFlow()

    private val _auraCounter = MutableStateFlow(0)
    val auraCounter: StateFlow<Int> = _auraCounter.asStateFlow()

    private val _webSearchEnabled = MutableStateFlow(false)
    val webSearchEnabled: StateFlow<Boolean> = _webSearchEnabled.asStateFlow()

    private val _deepThinkEnabled = MutableStateFlow(false)
    val deepThinkEnabled: StateFlow<Boolean> = _deepThinkEnabled.asStateFlow()

    private val _selectedModel = MutableStateFlow("gemini-3.1-flash")
    val selectedModel: StateFlow<String> = _selectedModel.asStateFlow()

    private val _messagesByMode = MutableStateFlow<Map<String, List<UiMessage>>>(
        mapOf("IRON" to emptyList(), "INK" to emptyList(), "IRONK" to emptyList())
    )
    private val _isLoadingByMode = MutableStateFlow<Map<String, Boolean>>(
        mapOf("IRON" to false, "INK" to false, "IRONK" to false)
    )
    private val _auraByMode = MutableStateFlow<Map<String, Int>>(
        mapOf("IRON" to 0, "INK" to 0, "IRONK" to 0)
    )

    private val _messages = MutableStateFlow<List<UiMessage>>(emptyList())
    val messages: StateFlow<List<UiMessage>> = _messages.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    fun setMode(mode: String) {
        _currentMode.value = mode
        _messages.value = _messagesByMode.value[mode] ?: emptyList()
        _isLoading.value = _isLoadingByMode.value[mode] ?: false
        _auraCounter.value = _auraByMode.value[mode] ?: 0
    }

    fun toggleWebSearch() { _webSearchEnabled.value = !_webSearchEnabled.value }
    fun toggleDeepThink() { _deepThinkEnabled.value = !_deepThinkEnabled.value }
    fun setModel(model: String) { _selectedModel.value = model }

    fun uploadDocument(filePart: okhttp3.MultipartBody.Part) {
        viewModelScope.launch {
            val mode = _currentMode.value
            setIsLoadingForMode(mode, true)
            val status = repository.uploadContextDocument(filePart, mode)
            addMessageToMode(mode, UiMessage("> $status", isUser = false))
            setIsLoadingForMode(mode, false)
        }
    }

    fun processDocumentContent(content: String, fileName: String) {
        val mode = _currentMode.value
        viewModelScope.launch {
            setIsLoadingForMode(mode, true)
            addMessageToMode(mode, UiMessage(
                content = "Analyzing document: $fileName",
                isUser = false,
                reasoning = "Extracting content and preparing analysis..."
            ))

            try {
                val result = repository.processDocumentAsMessage(content, fileName, mode)
                addMessageToMode(mode, UiMessage(result, isUser = false))
            } catch (e: Exception) {
                addMessageToMode(mode, UiMessage(
                    content = "Document processing failed: ${e.message}",
                    isUser = false
                ))
            }
            setIsLoadingForMode(mode, false)
        }
    }

    fun sendMessage(text: String) {
        if (text.isBlank()) return

        val mode = _currentMode.value
        val userMessage = UiMessage(text, isUser = true)
        addMessageToMode(mode, userMessage)
        incrementAuraForMode(mode, scoreUserMessage(text, mode))
        setIsLoadingForMode(mode, true)
        _isLoading.value = true

        viewModelScope.launch {
            memoryDao.insertMemory(
                MemoryState(
                    category = mode,
                    content = text
                )
            )

            val recentMemories = memoryDao.getRecentMemories().firstOrNull() ?: emptyList()
            val laneMemories = memoryDao.getRecentMemoriesByCategory(mode).firstOrNull() ?: emptyList()
            val fatigue = computeFatigueScore(recentMemories)
            _fatigueScore.value = fatigue
            _memoryRecall.value = buildRecallHint(recentMemories)

            val responsePair = repository.sendMessage(
                userMessage = text,
                mode = mode,
                memoryContext = recentMemories,
                laneMemories = laneMemories,
                fatigueScore = fatigue,
                webSearchEnabled = _webSearchEnabled.value,
                useThinking = _deepThinkEnabled.value,
                selectedModel = _selectedModel.value,
                selectedProvider = "google"
            )

            addMessageToMode(mode, UiMessage(responsePair.first, isUser = false, reasoning = responsePair.second))
            incrementAuraForMode(mode, scoreAssistantMessage(responsePair.first))
            setIsLoadingForMode(mode, false)
            _isLoading.value = false
        }
    }

    private fun addMessageToMode(mode: String, message: UiMessage) {
        val current = _messagesByMode.value[mode] ?: emptyList()
        val newMessages = current + message
        _messagesByMode.value = _messagesByMode.value + (mode to newMessages)
        // Update _messages if this is the current mode
        if (mode == _currentMode.value) {
            _messages.value = newMessages
        }
        // Update aura counter for current mode
        if (mode == _currentMode.value) {
            _auraCounter.value = _auraByMode.value[mode] ?: 0
        }
    }

    private fun setIsLoadingForMode(mode: String, loading: Boolean) {
        _isLoadingByMode.value = _isLoadingByMode.value + (mode to loading)
        if (mode == _currentMode.value) {
            _isLoading.value = loading
        }
    }

    private fun incrementAuraForMode(mode: String, delta: Int) {
        val current = _auraByMode.value[mode] ?: 0
        val newAura = (current + delta).coerceIn(0, 100)
        _auraByMode.value = _auraByMode.value + (mode to newAura)
        if (mode == _currentMode.value) {
            _auraCounter.value = newAura
        }
    }

    private fun scoreUserMessage(text: String, mode: String): Int {
        val lower = text.lowercase()
        var delta = 0

        if (lower.contains("can't") || lower.contains("cannot") || lower.contains("skip")) delta -= 2
        if (lower.contains("tired") || lower.contains("burnout") || lower.contains("overwhelmed")) delta -= 3
        if (lower.contains("done") || lower.contains("completed") || lower.contains("finished")) delta += 4
        if (lower.contains("pr") || lower.contains("personal record") || lower.contains("improved")) delta += 5

        if (mode == "IRON") {
            if (listOf("deadlift", "squat", "bench", "workout", "reps", "sets").any { lower.contains(it) }) delta += 2
            else delta += 1
        } else {
            if (listOf("study", "mock", "reasoning", "math", "revision", "learn", "read", "write", "research", "homework", "exam", "test", "focus", "work").any { lower.contains(it) }) delta += 2
            else delta += 1
        }

        return delta.coerceIn(-8, 10)
    }

    private fun scoreAssistantMessage(text: String): Int {
        val lower = text.lowercase()
        var delta = 1

        if (lower.contains("next block")) delta += 2
        if (lower.contains("risk check")) delta += 1
        if (lower.contains("error") || lower.contains("com_link_error")) delta -= 4

        return delta.coerceIn(-6, 6)
    }

    private fun computeFatigueScore(memories: List<MemoryState>): Int {
        if (memories.isEmpty()) return 0

        val now = System.currentTimeMillis()
        val last48h = memories.filter { now - it.timestamp <= 48L * 60L * 60L * 1000L }
        val ironLoad = last48h.count { it.category == "IRON" } * 12
        val inkLoad = last48h.count { it.category == "INK" } * 7
        val streakBoost = if (last48h.size >= 8) 20 else 0

        return (ironLoad + inkLoad + streakBoost).coerceIn(0, 100)
    }

    private fun buildRecallHint(memories: List<MemoryState>): String {
        if (memories.isEmpty()) return "No indexed recall yet."

        val now = System.currentTimeMillis()
        val threeDaysAgo = 3L * 24L * 60L * 60L * 1000L
        val prior = memories.firstOrNull { now - it.timestamp >= threeDaysAgo } ?: memories.last()
        return "Recall anchor: ${prior.category} -> ${prior.content.take(90)}"
    }
}
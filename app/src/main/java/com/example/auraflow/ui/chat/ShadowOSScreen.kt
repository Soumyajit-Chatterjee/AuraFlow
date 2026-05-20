package com.example.auraflow.ui.chat

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.auraflow.ui.theme.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.coroutines.launch
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGestures
import kotlin.math.atan2
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.clickable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
// --- Glassmorphism Utils ---
fun Modifier.glassCard(shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(20.dp), alpha: Float = 0.04f) = this.then(
    Modifier
        .background(Color.White.copy(alpha = alpha), shape)
        .border(1.dp, Color.White.copy(alpha = 0.08f), shape)
        .clip(shape)
)

data class AuraTab(val label: String, val icon: ImageVector)

@Composable
fun ShadowOSScreen(viewModel: AuraFlowViewModel) {
    val messages by viewModel.messages.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val currentMode by viewModel.currentMode.collectAsState()
    val fatigueScore by viewModel.fatigueScore.collectAsState()
    val memoryRecall by viewModel.memoryRecall.collectAsState()
    val auraCounter by viewModel.auraCounter.collectAsState()
    val webSearchEnabled by viewModel.webSearchEnabled.collectAsState()
    val deepThinkEnabled by viewModel.deepThinkEnabled.collectAsState()
    val selectedModel by viewModel.selectedModel.collectAsState()
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current

    var uploadStatus by remember { mutableStateOf<String?>(null) }

    val documentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    // Get MIME type and filename
                    val mimeType = context.contentResolver.getType(uri)
                    val fileName = uri.lastPathSegment ?: "document"

                    // Check if file is PDF
                    val isPdf = mimeType?.contains("pdf") == true ||
                                fileName.lowercase().endsWith(".pdf")

                    // Supported types
                    val allowedTextTypes = listOf(
                        "text/plain", "application/json",
                        "application/xml", "text/html", "text/csv"
                    )
                    val isTextFile = mimeType == null || allowedTextTypes.any { mimeType.startsWith(it) }

                    // Reject unsupported file types (but allow PDF for attempt)
                    if (!isTextFile && !isPdf && mimeType != null) {
                        withContext(Dispatchers.Main) {
                            uploadStatus = "Unsupported file type"
                        }
                        return@launch
                    }

                    // Read file content
                    val inputStream = context.contentResolver.openInputStream(uri)
                        ?: throw Exception("Cannot read file")
                    val bytes = inputStream.use { it.readBytes() }

                    // Validate file size
                    if (bytes.size > 5 * 1024 * 1024) {
                        withContext(Dispatchers.Main) {
                            uploadStatus = "File exceeds 5MB limit"
                        }
                        return@launch
                    }

                    if (bytes.isEmpty()) {
                        withContext(Dispatchers.Main) {
                            uploadStatus = "File is empty"
                        }
                        return@launch
                    }

                    // Extract text content based on file type
                    val extractedText = if (isPdf) {
                        try {
                            // Use iText7 for PDF text extraction
                            val pdfInputStream = java.io.ByteArrayInputStream(bytes)
                            val pdfReader = com.itextpdf.kernel.pdf.PdfReader(pdfInputStream)
                            val pdfDoc = com.itextpdf.kernel.pdf.PdfDocument(pdfReader)
                            val textBuilder = StringBuilder()

                            // Get number of pages
                            val numPages = minOf(10, pdfDoc.numberOfPages)
                            for (pageNum in 1..numPages) {
                                val page = pdfDoc.getPage(pageNum)
                                val text = com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor.getTextFromPage(page)
                                textBuilder.append(text).append("\n\n")
                            }
                            pdfDoc.close()

                            val text = textBuilder.toString().trim()
                            if (text.isBlank()) {
                                withContext(Dispatchers.Main) {
                                    uploadStatus = "PDF contains no readable text"
                                }
                                return@launch
                            }
                            text
                        } catch (e: Exception) {
                            android.util.Log.e("AuraFlow", "PDF extraction failed: ${e.message}")
                            withContext(Dispatchers.Main) {
                                uploadStatus = "Could not read PDF"
                            }
                            return@launch
                        }
                    } else {
                        // Read as text
                        String(bytes, Charsets.UTF_8).trim()
                    }

                    withContext(Dispatchers.Main) {
                        uploadStatus = "Analyzing document..."
                    }

                    // Send document content via ViewModel
                    viewModel.processDocumentContent(extractedText, fileName)

                    withContext(Dispatchers.Main) {
                        uploadStatus = null
                    }
                } catch (e: Exception) {
                    android.util.Log.e("AuraFlowUpload", "Error: ${e.message}", e)
                    withContext(Dispatchers.Main) {
                        uploadStatus = "Upload failed"
                    }
                }
            }
        }
    }

    val tabs = remember {
        listOf(
            AuraTab("Command", Icons.Default.SpaceDashboard),
            AuraTab("Focus", Icons.Default.Insights),
            AuraTab("Memory", Icons.Default.Bolt),
            AuraTab("System", Icons.Default.Tune)
        )
    }

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var inputText by remember { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFF090D14), Color(0xFF030507))
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopBar(currentMode, auraCounter, onModeSelected = viewModel::setMode)

            Box(modifier = Modifier.weight(1f)) {
                AnimatedContent(
                    targetState = selectedTabIndex,
                    transitionSpec = {
                        (fadeIn() + slideInVertically { it / 6 }) togetherWith (fadeOut() + slideOutVertically { -it / 6 })
                    },
                    label = "tab_content"
                ) { tab ->
                    when (tab) {
                        0 -> ChatArea(
                            messages = messages,
                            isLoading = isLoading,
                            listState = listState,
                            inputText = inputText,
                            uploadStatus = uploadStatus,
                            onInputChange = { inputText = it },
                            onAttach = { documentLauncher.launch("*/*") },
                            onSend = {
                                if (inputText.isNotBlank()) {
                                    viewModel.sendMessage(inputText)
                                    inputText = ""
                                    keyboardController?.hide()
                                    coroutineScope.launch {
                                        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
                                    }
                                }
                            }
                        )
                        1 -> FocusTabContent(currentMode, messages.size, fatigueScore, auraCounter)
                        2 -> MemoryTabContent(messages, currentMode, memoryRecall)
                        3 -> SettingsTab(
                            webSearch = webSearchEnabled,
                            onToggleSearch = viewModel::toggleWebSearch,
                            deepThink = deepThinkEnabled,
                            onToggleThink = viewModel::toggleDeepThink,
                            selectedModel = selectedModel,
                            onSetModel = viewModel::setModel
                        )
                    }
                }
            }

            GlassBottomNav(tabs, selectedTabIndex) { selectedTabIndex = it }
        }
    }
}

@Composable
private fun TopBar(
    currentMode: String,
    auraCounter: Int,
    onModeSelected: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("AuraFlow", color = TextLight, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
            val auraColor = if (auraCounter > 0) SuccessMint else AccentPurple
            Box(
                modifier = Modifier.glassCard(RoundedCornerShape(8.dp), 0.1f).padding(horizontal = 8.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("Aura: $auraCounter", color = auraColor, fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            }
        }
        ModePillTabs(currentMode = currentMode, onModeSelected = onModeSelected)
    }
}

@Composable
private fun ModePillTabs(currentMode: String, onModeSelected: (String) -> Unit) {
    val modes = listOf("IRON", "INK", "IRONK")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.Start
    ) {
        modes.forEach { mode ->
            val isSelected = currentMode == mode
            val backgroundColor = if (isSelected) AccentCyan.copy(alpha = 0.2f) else Color.Transparent
            val textColor = if (isSelected) AccentCyan else SecondaryGrey
            Box(
                modifier = Modifier
                    .padding(end = 8.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(backgroundColor)
                    .border(1.dp, if (isSelected) AccentCyan else SecondaryGrey.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                    .clickable { onModeSelected(mode) }
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(mode, color = textColor, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal, fontFamily = FontFamily.Monospace)
            }
        }
    }
}

@Composable
private fun ChatArea(
    messages: List<UiMessage>,
    isLoading: Boolean,
    listState: LazyListState,
    inputText: String,
    onInputChange: (String) -> Unit,
    uploadStatus: String? = null,
    onAttach: () -> Unit,
    onSend: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        Box(modifier = Modifier.weight(1f).glassCard(RoundedCornerShape(24.dp))) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(messages) { msg -> MessageBubble(msg) }
                if (isLoading) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CircularProgressIndicator(color = AccentCyan, modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                            Text("Synthesizing...", color = AccentCyan, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        InputBar(inputText, onInputChange, onAttach, onSend, uploadStatus)
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun InputBar(inputText: String, onInputChange: (String) -> Unit, onAttach: () -> Unit, onSend: () -> Unit, uploadStatus: String? = null) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (uploadStatus != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp)
                    .glassCard(RoundedCornerShape(8.dp), 0.1f)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (uploadStatus.startsWith("Processing") || uploadStatus.startsWith("Uploading")) {
                    CircularProgressIndicator(color = AccentCyan, modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        uploadStatus,
                        color = AccentCyan,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                } else {
                    Icon(Icons.Default.Warning, "Status", tint = AccentPurple, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        uploadStatus,
                        color = AccentPurple,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().glassCard(RoundedCornerShape(24.dp)).padding(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
        IconButton(onClick = onAttach) { Icon(Icons.Default.AttachFile, "Attach", tint = SecondaryGrey) }
        OutlinedTextField(
            value = inputText,
            onValueChange = onInputChange,
            modifier = Modifier.weight(1f),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                focusedBorderColor = Color.Transparent, unfocusedBorderColor = Color.Transparent,
                focusedTextColor = TextLight, unfocusedTextColor = TextLight
            ),
            placeholder = { Text("Command...", color = SecondaryGrey.copy(alpha = 0.5f)) },
            textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace)
        )
        IconButton(onClick = onSend, modifier = Modifier.background(AccentCyan.copy(alpha=0.15f), CircleShape)) {
            Icon(Icons.AutoMirrored.Filled.Send, "Send", tint = AccentCyan)
        }
        }
    }
}

@Composable
fun MessageBubble(message: UiMessage) {
    val isUser = message.isUser
    val align = if (isUser) Alignment.CenterEnd else Alignment.CenterStart
    
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = align) {
        Box(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .glassCard(
                    shape = RoundedCornerShape(
                        topStart = 20.dp, topEnd = 20.dp,
                        bottomStart = if (isUser) 20.dp else 4.dp,
                        bottomEnd = if (isUser) 4.dp else 20.dp
                    ),
                    alpha = if (isUser) 0.1f else 0.02f
                )
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                if (!message.reasoning.isNullOrBlank()) {
                    Text(
                        "REASONING >>\n${message.reasoning}",
                        color = SecondaryGrey.copy(alpha = 0.7f),
                        fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha=0.05f)))
                    Spacer(modifier = Modifier.height(8.dp))
                }
                
                if (isUser) {
                    Text(message.content, color = TextLight, fontSize = 15.sp)
                } else {
                    IntelligentMessageRenderer(message.content)
                }
            }
        }
    }
}

@Composable
fun IntelligentMessageRenderer(text: String) {
    val blocks = text.split("##")
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        for ((index, block) in blocks.withIndex()) {
            val trimBlock = block.trim()
            if (trimBlock.isEmpty()) continue
            
            if (index == 0 && !text.startsWith("##")) {
                Text(trimBlock, color = TextLight, fontSize = 14.sp)
                continue
            }

            when {
                trimBlock.startsWith("Status", ignoreCase = true) -> StatusRenderer(trimBlock)
                trimBlock.startsWith("Focus Now", ignoreCase = true) -> FocusRenderer(trimBlock)
                trimBlock.startsWith("Next Block", ignoreCase = true) -> MissionRenderer(trimBlock)
                trimBlock.startsWith("Risk Check", ignoreCase = true) -> RiskRenderer(trimBlock)
                else -> StandardMarkdownRenderer(trimBlock)
            }
        }
    }
}

@Composable
private fun StatusRenderer(content: String) {
    val items = content.lines().filter { it.contains(":") }.map { it.removePrefix("-").trim() }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        items.take(3).forEach { item ->
            val parts = item.split(":")
            if (parts.size >= 2) {
                val label = parts[0].trim().uppercase()
                val value = parts[1].trim().lowercase()
                val color = when(value) { "high" -> SuccessMint; "med" -> AccentCyan; else -> AccentPurple }
                Column(
                    modifier = Modifier.weight(1f).glassCard(RoundedCornerShape(8.dp), 0.08f).padding(6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(label, fontSize = 9.sp, color = SecondaryGrey, fontFamily = FontFamily.Monospace)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(value.uppercase(), fontSize = 11.sp, color = color, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

fun parseMarkdownText(text: String): androidx.compose.ui.text.AnnotatedString {
    return buildAnnotatedString {
        var currentIndex = 0
        val regex = Regex("\\*\\*(.*?)\\*\\*")
        val matches = regex.findAll(text)
        
        for (match in matches) {
            append(text.substring(currentIndex, match.range.first))
            withStyle(style = SpanStyle(fontWeight = FontWeight.Bold, color = TextLight)) {
                append(match.groupValues[1])
            }
            currentIndex = match.range.last + 1
        }
        append(text.substring(currentIndex))
    }
}

@Composable
private fun FocusRenderer(content: String) {
    val focusText = content.lines().drop(1).joinToString(" ").removePrefix("-").trim()
    Row(
        modifier = Modifier.fillMaxWidth().glassCard(RoundedCornerShape(12.dp), 0.05f).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(Icons.Default.AdsClick, "Focus", tint = AccentCyan, modifier = Modifier.size(20.dp))
        Column {
            Text("FOCUS NOW", color = AccentCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            Text(parseMarkdownText(focusText), color = TextLight, fontSize = 13.sp)
        }
    }
}

@Composable
private fun MissionRenderer(content: String) {
    val lines = content.lines()
    val header = lines.firstOrNull() ?: "NEXT BLOCK"
    val tasks = lines.drop(1).filter { it.isNotBlank() }

    Column(modifier = Modifier.fillMaxWidth().glassCard(RoundedCornerShape(12.dp)).padding(12.dp)) {
        Text(header.uppercase(), color = TextLight, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        Spacer(modifier = Modifier.height(8.dp))
        tasks.forEach { task ->
            Row(modifier = Modifier.padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.CheckCircleOutline, "Task", tint = SuccessMint, modifier = Modifier.size(16.dp))
                Text(parseMarkdownText(task.removePrefix("-").trim()), color = SecondaryGrey, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun RiskRenderer(content: String) {
    val riskText = content.lines().drop(1).joinToString("\n").removePrefix("-").trim()
    Row(
        modifier = Modifier.fillMaxWidth().glassCard(RoundedCornerShape(12.dp), 0.1f).border(1.dp, AccentPurple.copy(alpha=0.3f), RoundedCornerShape(12.dp)).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(Icons.Default.Warning, "Risk", tint = AccentPurple, modifier = Modifier.size(24.dp))
        Column {
            Text("RISK CHECK", color = AccentPurple, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            Text(parseMarkdownText(riskText), color = TextLight, fontSize = 13.sp)
        }
    }
}

@Composable
private fun StandardMarkdownRenderer(content: String) {
    val lines = content.lines()
    Text(lines.firstOrNull() ?: "", color = AccentCyan, fontWeight = FontWeight.Bold, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
    Text(parseMarkdownText(lines.drop(1).joinToString("\n").trim()), color = TextLight, fontSize = 14.sp)
}

@Composable
fun GlassBottomNav(tabs: List<AuraTab>, selectedIndex: Int, onSelect: (Int) -> Unit) {
    Box(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().glassCard(RoundedCornerShape(24.dp), 0.08f).padding(8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            tabs.forEachIndexed { index, tab ->
                val selected = selectedIndex == index
                val color = if (selected) AccentCyan else SecondaryGrey
                val bg = if (selected) Color.White.copy(alpha = 0.1f) else Color.Transparent
                IconButton(
                    onClick = { onSelect(index) },
                    modifier = Modifier.background(bg, CircleShape)
                ) {
                    Icon(tab.icon, contentDescription = tab.label, tint = color)
                }
            }
        }
    }
}

// --- Dynamic Focus Tab (Aura Fluid) ---
@Composable
fun AuraFluidBar(auraScore: Int, maxAura: Int = 100, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "fluid")
    val phaseOffset by transition.animateFloat(
        initialValue = 0f, targetValue = 2f * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(tween(4000, easing = LinearEasing), RepeatMode.Restart), label = "phaseOffset"
    )

    val fillTarget = (auraScore.toFloat() / maxAura).coerceIn(0f, 1f)
    val animatedFill by animateFloatAsState(targetValue = fillTarget, animationSpec = tween(1500, easing = FastOutSlowInEasing), label = "fillAnim")

    val color = when {
        auraScore >= 50 -> SuccessMint
        auraScore >= 25 -> AccentCyan
        else -> AccentPurple
    }

    Box(modifier = modifier.glassCard(RoundedCornerShape(32.dp), 0.05f).padding(1.dp).clip(RoundedCornerShape(32.dp))) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            val liquidHeight = height * (1f - animatedFill)

            val pathFront = Path()
            val pathBack = Path()

            pathBack.moveTo(0f, height); pathFront.moveTo(0f, height)
            pathBack.lineTo(0f, liquidHeight); pathFront.lineTo(0f, liquidHeight)

            val frequency = 1.5f * Math.PI.toFloat() / width
            val amplitude = 8.dp.toPx()

            for (x in 0..width.toInt() step 5) {
                val yBack = liquidHeight + amplitude * kotlin.math.sin(frequency * x + phaseOffset + 1f).toFloat()
                val yFront = liquidHeight + amplitude * kotlin.math.sin(frequency * x - phaseOffset).toFloat()
                pathBack.lineTo(x.toFloat(), yBack)
                pathFront.lineTo(x.toFloat(), yFront)
            }

            pathBack.lineTo(width, height); pathFront.lineTo(width, height)
            pathBack.close(); pathFront.close()

            drawPath(pathBack, color = color.copy(alpha = 0.4f))
            drawPath(pathFront, color = color.copy(alpha = 0.8f))
            
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.15f), Color.Transparent),
                    startX = width * 0.1f, endX = width * 0.4f
                ), alpha = 1f
            )
        }
    }
}

@Composable
private fun FocusTabContent(mode: String, count: Int, fatigue: Int, aura: Int) {
    val tier = when { aura >= 50 -> "Apex"; aura >= 25 -> "Mastery"; aura >= 10 -> "Adept"; else -> "Initiate" }
    val tierIcon = when { aura >= 50 -> Icons.Default.WorkspacePremium; aura >= 25 -> Icons.Default.BrightnessHigh; aura >= 10 -> Icons.Default.Adjust; else -> Icons.Default.RadioButtonUnchecked }
    val tierColor = when { aura >= 50 -> SuccessMint; aura >= 25 -> AccentCyan; else -> AccentPurple }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp).glassCard(RoundedCornerShape(24.dp)), 
        verticalArrangement = Arrangement.SpaceEvenly, horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(tierIcon, contentDescription = tier, tint = tierColor, modifier = Modifier.size(56.dp))
            Spacer(modifier = Modifier.height(12.dp))
            Text("RANK: ${tier.uppercase()}", color = tierColor, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, letterSpacing = 4.sp)
        }

        AuraFluidBar(auraScore = aura, maxAura = 100, modifier = Modifier.width(120.dp).height(280.dp))

        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("MODE", color = SecondaryGrey, fontSize = 10.sp, fontFamily = FontFamily.Monospace); Text(mode, color = TextLight, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
            Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("FATIGUE", color = SecondaryGrey, fontSize = 10.sp, fontFamily = FontFamily.Monospace); Text("$fatigue%", color = if(fatigue>75) AccentPurple else AccentCyan, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
            Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("AURA", color = SecondaryGrey, fontSize = 10.sp, fontFamily = FontFamily.Monospace); Text("$aura", color = tierColor, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun MemoryTabContent(msgs: List<UiMessage>, mode: String, memory: String) {
    val transition = rememberInfiniteTransition(label="memory")
    val rotation by transition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(8000, easing = LinearEasing), RepeatMode.Restart), label="rot"
    )
    val scalePulse by transition.animateFloat(
        initialValue = 0.85f, targetValue = 1.15f,
        animationSpec = infiniteRepeatable(tween(2000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label="scale"
    )

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp).glassCard(RoundedCornerShape(24.dp)), 
        verticalArrangement = Arrangement.SpaceEvenly, horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(modifier = Modifier.size(200.dp), contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                rotate(rotation) {
                    drawArc(color = AccentCyan.copy(alpha=0.3f), startAngle = 45f, sweepAngle = 270f, useCenter = false, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round))
                }
                rotate(-rotation * 1.5f) {
                    drawArc(color = AccentPurple.copy(alpha=0.5f), startAngle = 0f, sweepAngle = 220f, useCenter = false, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round), topLeft = androidx.compose.ui.geometry.Offset(20f,20f), size = androidx.compose.ui.geometry.Size(size.width-40f, size.height-40f))
                }
            }
            Icon(Icons.Default.Bolt, null, tint = AccentCyan, modifier = Modifier.size(56.dp).scale(scalePulse))
        }

        Column(modifier = Modifier.padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("MEMORY NODE ACTIVE", color = AccentCyan, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            Spacer(modifier = Modifier.height(16.dp))
            Text(memory, color = SecondaryGrey, fontSize = 13.sp, textAlign = TextAlign.Center)
        }
    }
}
@Composable
fun SegmentedToggle(options: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .glassCard(RoundedCornerShape(24.dp), 0.05f)
            .padding(4.dp)
    ) {
        val segmentWidth = maxWidth / options.size
        val offset by animateDpAsState(targetValue = segmentWidth * selectedIndex.coerceAtLeast(0), label = "sliderOffset", animationSpec = tween(300, easing = FastOutSlowInEasing))
        
        Box(
            modifier = Modifier
                .width(segmentWidth)
                .fillMaxHeight()
                .offset(x = offset)
                .glassCard(RoundedCornerShape(24.dp), 0.15f)
                .border(1.dp, AccentCyan.copy(alpha=0.6f), RoundedCornerShape(24.dp))
        )
        
        Row(modifier = Modifier.fillMaxSize()) {
            options.forEachIndexed { index, text ->
                val isActive = selectedIndex == index
                Box(
                    modifier = Modifier.weight(1f).fillMaxHeight().clickable { onSelect(index) }, 
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = text, 
                        color = if (isActive) AccentCyan else SecondaryGrey, 
                        fontWeight = if(isActive) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 13.sp, fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

@Composable
fun IndependentToggle(text: String, isActive: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val alpha by animateFloatAsState(targetValue = if(isActive) 0.15f else 0.05f, label = "toggleAlpha", animationSpec = tween(300))
    val textColor = if(isActive) AccentCyan else SecondaryGrey
    val glowColor = if(isActive) AccentCyan.copy(alpha=0.6f) else Color.Transparent

    Box(
        modifier = modifier
            .height(48.dp)
            .clickable { onClick() }
            .glassCard(RoundedCornerShape(24.dp), alpha)
            .border(1.dp, glowColor, RoundedCornerShape(24.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = textColor, fontSize = 13.sp, fontWeight = if(isActive) FontWeight.Bold else FontWeight.Normal, fontFamily = FontFamily.Monospace)
    }
}

@Composable
fun HudArcSelector(currentMode: String, onModeChange: (String) -> Unit) {
    val modes = listOf("IRON", "INK", "IRONK")
    val selectedIndex = modes.indexOf(currentMode).coerceAtLeast(0)
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val centerX = size.width / 2f
                    val centerY = size.height * 0.85f 
                    val dx = offset.x - centerX
                    val dy = offset.y - centerY
                    val angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).let { 
                        if (it < 0) it + 360 else it 
                    }
                    if (angle in 180.0..360.0) {
                        if (angle < 240) onModeChange(modes[0])
                        else if (angle < 300) onModeChange(modes[1])
                        else onModeChange(modes[2])
                    }
                }
            },
        contentAlignment = Alignment.BottomCenter
    ) {
        val animatedStart by animateFloatAsState(targetValue = 180f + selectedIndex * 60f, animationSpec = tween(300, easing = FastOutSlowInEasing), label = "arcStart")
        
        Canvas(modifier = Modifier.fillMaxSize()) {
            val centerX = size.width / 2f
            val centerY = size.height * 0.85f
            val radius = size.height * 0.6f
            val strokeW = 12.dp.toPx()
            
            drawArc(
                color = SecondaryGrey.copy(alpha=0.15f),
                startAngle = 180f, sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset(centerX - radius, centerY - radius),
                size = androidx.compose.ui.geometry.Size(radius*2, radius*2),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeW, cap = androidx.compose.ui.graphics.StrokeCap.Butt)
            )

            drawArc(
                color = AccentCyan,
                startAngle = animatedStart, sweepAngle = 60f,
                useCenter = false,
                topLeft = Offset(centerX - radius, centerY - radius),
                size = androidx.compose.ui.geometry.Size(radius*2, radius*2),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeW, cap = androidx.compose.ui.graphics.StrokeCap.Butt)
            )
            
            for (i in 0..3) {
                val angle = 180f + i * 60f
                val rad = Math.toRadians(angle.toDouble())
                val innerX = centerX + (radius - strokeW) * kotlin.math.cos(rad)
                val innerY = centerY + (radius - strokeW) * kotlin.math.sin(rad)
                val outerX = centerX + (radius + strokeW) * kotlin.math.cos(rad)
                val outerY = centerY + (radius + strokeW) * kotlin.math.sin(rad)
                drawLine(color = SurfaceDark, start = Offset(innerX.toFloat(), innerY.toFloat()), end = Offset(outerX.toFloat(), outerY.toFloat()), strokeWidth = 8.dp.toPx())
            }
        }
        
        val modeIcon = when(currentMode) {
            "IRON" -> Icons.Default.Security
            "INK" -> Icons.Default.Create
            "IRONK" -> Icons.Default.Memory
            else -> Icons.Default.Adjust
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(bottom = 12.dp)) {
            Icon(modeIcon, contentDescription = currentMode, tint = AccentCyan, modifier = Modifier.size(28.dp))
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "[ $currentMode ]", 
                color = AccentCyan, 
                fontSize = 20.sp, 
                fontWeight = FontWeight.Bold, 
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
private fun SettingsTab(
    webSearch: Boolean, onToggleSearch: () -> Unit,
    deepThink: Boolean, onToggleThink: () -> Unit,
    selectedModel: String, onSetModel: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp).glassCard().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("SYSTEM PROTOCOLS", color = AccentCyan, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)

        Spacer(modifier = Modifier.height(16.dp))
        Text("Sub-Routines", color = SecondaryGrey, fontSize = 12.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
            IndependentToggle(text = "Web Search", isActive = webSearch, modifier = Modifier.weight(1f), onClick = onToggleSearch)
            IndependentToggle(text = "Deep Think", isActive = deepThink, modifier = Modifier.weight(1f), onClick = onToggleThink)
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text("Core Engine", color = SecondaryGrey, fontSize = 12.sp)
        val isFlash = selectedModel.contains("flash")
        SegmentedToggle(
            options = listOf("Flash Core", "Pro Core"),
            selectedIndex = if (isFlash) 0 else 1,
            onSelect = { index -> onSetModel(if (index == 0) "gemini-3.1-flash" else "gemini-3.1-pro-preview") }
        )
    }
}

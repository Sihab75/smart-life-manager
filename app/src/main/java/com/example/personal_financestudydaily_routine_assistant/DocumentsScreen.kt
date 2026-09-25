package com.example.personal_financestudydaily_routine_assistant

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.example.personal_financestudydaily_routine_assistant.ai.AiProvider
import com.example.personal_financestudydaily_routine_assistant.ai.LocalAiAssistant
import com.example.personal_financestudydaily_routine_assistant.ai.OnlineAiAssistant
import com.example.personal_financestudydaily_routine_assistant.data.database.DocumentEntity
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun DocumentsScreen(vm: MainViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val documents by vm.documents.collectAsState(initial = emptyList())
    val localAssistant = remember { LocalAiAssistant() }
    val onlineAssistant = remember { OnlineAiAssistant(fallback = localAssistant) }
    var selectedDocument by remember { mutableStateOf<DocumentEntity?>(null) }
    var result by remember { mutableStateOf<String?>(null) }
    var isRunning by remember { mutableStateOf(false) }
    var question by remember { mutableStateOf("") }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val fileName = uri.lastPathSegment?.substringAfterLast('/') ?: "Uploaded document"
        val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"
        scope.launch {
            val text = if (mimeType.startsWith("text/")) {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }.orEmpty()
                }
            } else ""
            vm.addDocument(
                DocumentEntity(
                    title = fileName.substringBeforeLast('.').ifBlank { fileName },
                    fileName = fileName,
                    mimeType = mimeType,
                    uri = uri.toString(),
                    extractedText = text
                )
            )
        }
    }

    ScreenColumn {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Notes & Documents", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("Upload class notes and study from them with AI.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Button(onClick = { picker.launch(arrayOf("application/pdf", "image/*", "text/*", "application/msword", "application/vnd.openxmlformats-officedocument.wordprocessingml.document")) }) {
                Icon(Icons.Default.UploadFile, null)
                Spacer(Modifier.width(6.dp))
                Text("Upload")
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Supported files", fontWeight = FontWeight.SemiBold)
                Text("PDFs, images, text notes, DOC and DOCX files. Text files can be answered offline; other files are kept securely on this device until an AI provider processes them.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (documents.isEmpty()) {
            EmptyState("No documents yet. Upload your first class note.")
        } else {
            Text("Your library", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp)) {
                items(documents, key = { it.id }) { document ->
                    ListCard {
                        Icon(
                            when {
                                document.mimeType == "application/pdf" -> Icons.Default.PictureAsPdf
                                document.mimeType.startsWith("image/") -> Icons.Default.Image
                                else -> Icons.Default.Description
                            },
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column(Modifier.weight(1f)) {
                            Text(document.title, fontWeight = FontWeight.SemiBold, maxLines = 1)
                            Text(document.fileName, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                            Text(if (document.extractedText.isNotBlank()) "Text ready for offline AI" else "File uploaded · AI actions available", fontSize = MaterialTheme.typography.labelSmall.fontSize, color = MaterialTheme.colorScheme.primary)
                        }
                        IconButton(onClick = { selectedDocument = document }) { Icon(Icons.Default.AutoAwesome, "AI actions") }
                        IconButton(onClick = { vm.deleteDocument(document) }) { Icon(Icons.Default.DeleteOutline, "Delete document") }
                    }
                }
            }
        }
    }

    selectedDocument?.let { document ->
        AlertDialog(
            onDismissRequest = { if (!isRunning) selectedDocument = null },
            title = { Text("Study with ${document.title}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Choose an AI action", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(
                        value = question,
                        onValueChange = { question = it },
                        label = { Text("Question about this document (optional)") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2
                    )
                    listOf("Summarize", "Explain", "Generate MCQs", "Generate quizzes", "Generate flashcards", "Extract important points", "Answer questions", "Create a study plan").forEach { action ->
                        OutlinedButton(
                            onClick = {
                                isRunning = true
                                scope.launch {
                                    val requestedAction = if (action == "Answer questions") {
                                        "Answer this question: ${question.ifBlank { "What are the most important ideas?" }}"
                                    } else action
                                    result = onlineAssistant.documentAction(requestedAction, document.title, document.extractedText, AiProvider.GEMINI)
                                    isRunning = false
                                    selectedDocument = null
                                    question = ""
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isRunning
                        ) { Text(action) }
                    }
                }
            },
            confirmButton = { if (isRunning) CircularProgressIndicator(Modifier.size(24.dp)) }
        )
    }
    result?.let { output ->
        AlertDialog(
            onDismissRequest = { result = null },
            title = { Text("AI study result") },
            text = { LazyColumn { item { Text(output) } } },
            confirmButton = { Button(onClick = { result = null }) { Text("Done") } }
        )
    }
}

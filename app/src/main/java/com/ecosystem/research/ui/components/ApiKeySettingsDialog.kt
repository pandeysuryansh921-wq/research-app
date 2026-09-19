package com.ecosystem.research.ui.components

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ecosystem.research.core.security.AIProviderType
import com.ecosystem.research.core.security.SecureKeyManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

@Composable
fun ApiKeySettingsDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val keyManager = remember { SecureKeyManager(context) }

    var geminiKey by remember { mutableStateOf(keyManager.getGeminiApiKey() ?: "") }
    var activeProvider by remember { mutableStateOf(keyManager.getActiveProvider()) }
    var showKey by remember { mutableStateOf(false) }
    var isTesting by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Key, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("AI & API Key Settings", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Keys are encrypted with AES-256-GCM hardware encryption via Android Keystore. Prompts are strictly grounded in your selected excerpts.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = geminiKey,
                    onValueChange = { geminiKey = it; testResult = null },
                    label = { Text("Google Gemini API Key") },
                    singleLine = true,
                    visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showKey = !showKey }) {
                            Icon(
                                if (showKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = "Toggle Visibility"
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Active Engine:", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = activeProvider == AIProviderType.GEMINI,
                        onClick = { activeProvider = AIProviderType.GEMINI },
                        label = { Text("Gemini 1.5 Flash", fontSize = 11.sp) }
                    )
                    FilterChip(
                        selected = activeProvider == AIProviderType.OFFLINE_HEURISTIC,
                        onClick = { activeProvider = AIProviderType.OFFLINE_HEURISTIC },
                        label = { Text("Offline Heuristics", fontSize = 11.sp) }
                    )
                }

                if (isTesting) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Verifying key with Gemini...", fontSize = 12.sp)
                    }
                } else if (testResult != null) {
                    Text(
                        text = testResult!!,
                        fontSize = 12.sp,
                        color = if (testResult!!.startsWith("Success")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                }

                OutlinedButton(
                    onClick = {
                        if (geminiKey.isBlank()) {
                            testResult = "Please enter an API key first."
                            return@OutlinedButton
                        }
                        isTesting = true
                        testResult = null
                        scope.launch {
                            val ok = testGeminiKey(geminiKey)
                            isTesting = false
                            testResult = if (ok) "Success! Key is valid and connected." else "Connection failed. Please check key."
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Test Connection")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    keyManager.setGeminiApiKey(geminiKey)
                    keyManager.setActiveProvider(activeProvider)
                    Toast.makeText(context, "Settings saved securely", Toast.LENGTH_SHORT).show()
                    onDismiss()
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

private suspend fun testGeminiKey(apiKey: String): Boolean = withContext(Dispatchers.IO) {
    try {
        val client = OkHttpClient()
        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models?key=$apiKey")
            .get()
            .build()
        client.newCall(request).execute().use { resp ->
            resp.isSuccessful
        }
    } catch (_: Exception) {
        false
    }
}

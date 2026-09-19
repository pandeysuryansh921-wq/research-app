package com.ecosystem.research.core.document

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Divider
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Lightweight, zero-dependency Markdown & Rich Document Renderer for Jetpack Compose.
 */
@Composable
fun MarkdownContent(
    content: String,
    modifier: Modifier = Modifier
) {
    val lines = content.lines()
    var inCodeBlock = false
    val codeBlockLines = mutableListOf<String>()

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        var i = 0
        while (i < lines.size) {
            val rawLine = lines[i]
            val trimmed = rawLine.trim()

            // Code block delimiter
            if (trimmed.startsWith("```")) {
                if (inCodeBlock) {
                    // Close code block
                    val codeContent = codeBlockLines.joinToString("\n")
                    CodeBlock(code = codeContent)
                    codeBlockLines.clear()
                    inCodeBlock = false
                } else {
                    inCodeBlock = true
                    codeBlockLines.clear()
                }
                i++
                continue
            }

            if (inCodeBlock) {
                codeBlockLines.add(rawLine)
                i++
                continue
            }

            // Empty line
            if (trimmed.isEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                i++
                continue
            }

            // Headings
            when {
                trimmed.startsWith("# ") -> {
                    Text(
                        text = parseInlineMarkdown(trimmed.removePrefix("# ").trim()),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                    )
                }
                trimmed.startsWith("## ") -> {
                    Text(
                        text = parseInlineMarkdown(trimmed.removePrefix("## ").trim()),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
                    )
                }
                trimmed.startsWith("### ") -> {
                    Text(
                        text = parseInlineMarkdown(trimmed.removePrefix("### ").trim()),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
                    )
                }
                // Horizontal Rule
                trimmed == "---" || trimmed == "***" || trimmed == "___" -> {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        thickness = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                }
                // Blockquote
                trimmed.startsWith("> ") -> {
                    val quoteText = trimmed.removePrefix("> ").trim()
                    Blockquote(text = quoteText)
                }
                // Bullet List
                trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
                    val bulletItem = trimmed.substring(2).trim()
                    BulletItem(text = bulletItem)
                }
                // Numbered List
                trimmed.matches(Regex("""^\d+\.\s+.*""")) -> {
                    val num = trimmed.substringBefore(".")
                    val itemText = trimmed.substringAfter(".").trim()
                    NumberedItem(number = num, text = itemText)
                }
                // Regular paragraph
                else -> {
                    Text(
                        text = parseInlineMarkdown(rawLine),
                        style = MaterialTheme.typography.bodyLarge,
                        lineHeight = 22.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            i++
        }

        // Flush unclosed code block if file ends inside code block
        if (inCodeBlock && codeBlockLines.isNotEmpty()) {
            CodeBlock(code = codeBlockLines.joinToString("\n"))
        }
    }
}

@Composable
private fun CodeBlock(code: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(12.dp)
    ) {
        Text(
            text = code,
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.horizontalScroll(rememberScrollState())
        )
    }
}

@Composable
private fun Blockquote(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(vertical = 6.dp, horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.primary)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = parseInlineMarkdown(text),
            style = MaterialTheme.typography.bodyMedium,
            fontStyle = FontStyle.Italic,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun BulletItem(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = "•",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(end = 8.dp)
        )
        Text(
            text = parseInlineMarkdown(text),
            style = MaterialTheme.typography.bodyLarge,
            lineHeight = 22.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun NumberedItem(number: String, text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = "$number.",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(end = 8.dp)
        )
        Text(
            text = parseInlineMarkdown(text),
            style = MaterialTheme.typography.bodyLarge,
            lineHeight = 22.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/**
 * Parses basic inline Markdown formatting (**bold**, *italic*, `code`).
 */
fun parseInlineMarkdown(input: String): AnnotatedString {
    return buildAnnotatedString {
        var cursor = 0
        val text = input

        while (cursor < text.length) {
            when {
                // Bold: **text**
                text.startsWith("**", cursor) -> {
                    val endIdx = text.indexOf("**", cursor + 2)
                    if (endIdx != -1) {
                        pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                        append(text.substring(cursor + 2, endIdx))
                        pop()
                        cursor = endIdx + 2
                    } else {
                        append(text[cursor])
                        cursor++
                    }
                }
                // Inline Code: `code`
                text.startsWith("`", cursor) -> {
                    val endIdx = text.indexOf("`", cursor + 1)
                    if (endIdx != -1) {
                        pushStyle(
                            SpanStyle(
                                fontFamily = FontFamily.Monospace,
                                background = Color(0x1F888888)
                            )
                        )
                        append(text.substring(cursor + 1, endIdx))
                        pop()
                        cursor = endIdx + 1
                    } else {
                        append(text[cursor])
                        cursor++
                    }
                }
                // Italic: *text*
                text.startsWith("*", cursor) && (cursor + 1 < text.length && text[cursor + 1] != ' ') -> {
                    val endIdx = text.indexOf("*", cursor + 1)
                    if (endIdx != -1) {
                        pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                        append(text.substring(cursor + 1, endIdx))
                        pop()
                        cursor = endIdx + 1
                    } else {
                        append(text[cursor])
                        cursor++
                    }
                }
                else -> {
                    append(text[cursor])
                    cursor++
                }
            }
        }
    }
}

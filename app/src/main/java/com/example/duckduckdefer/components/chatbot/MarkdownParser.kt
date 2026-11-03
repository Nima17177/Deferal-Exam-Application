package com.example.duckduckdefer.components.chatbot

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

sealed class ContentBlock {
    data class RichText(val text: String) : ContentBlock()
    data class Table(val headers: List<String>, val rows: List<List<String>>) : ContentBlock()
}

private val pipeRowPattern = Regex("""^\|(.+)\|$""")
private val separatorPattern = Regex("""^\|[\s:]*-{2,}[\s:]*(\|[\s:]*-{2,}[\s:]*)*\|$""")

fun parseMessageContent(text: String): List<ContentBlock> {
    val blocks = mutableListOf<ContentBlock>()
    val lines = text.lines()
    val textBuffer = StringBuilder()
    var i = 0

    fun flushText() {
        val content = textBuffer.toString().trimEnd('\n')
        if (content.isNotBlank()) blocks.add(ContentBlock.RichText(content))
        textBuffer.clear()
    }

    while (i < lines.size) {
        val trimmed = lines[i].trim()
        if (pipeRowPattern.matches(trimmed) && i + 1 < lines.size && separatorPattern.matches(lines[i + 1].trim())) {
            flushText()
            val headers = trimmed.drop(1).dropLast(1).split("|").map { it.trim() }
            i += 2 // skip header + separator
            val rows = mutableListOf<List<String>>()
            while (i < lines.size) {
                val rowTrimmed = lines[i].trim()
                if (!pipeRowPattern.matches(rowTrimmed)) break
                rows.add(rowTrimmed.drop(1).dropLast(1).split("|").map { it.trim() })
                i++
            }
            blocks.add(ContentBlock.Table(headers, rows))
        } else {
            if (textBuffer.isNotEmpty()) textBuffer.append("\n")
            textBuffer.append(lines[i])
            i++
        }
    }
    flushText()
    return blocks
}

fun parseMarkdownToAnnotatedString(
    text: String,
    baseColor: Color,
    baseFontSize: TextUnit = 14.sp,
    codeColor: Color = baseColor.copy(alpha = 0.8f)
): AnnotatedString = buildAnnotatedString {
    val baseStyle = SpanStyle(color = baseColor, fontSize = baseFontSize)
    val codeBlockStyle = SpanStyle(
        color = codeColor,
        fontSize = baseFontSize * 0.9f,
        fontFamily = FontFamily.Monospace
    )
    val inlineCodeStyle = baseStyle.copy(fontFamily = FontFamily.Monospace)

    val segments = splitCodeBlocks(text)

    segments.forEachIndexed { segIdx, segment ->
        when (segment) {
            is Segment.CodeBlock -> {
                if (segIdx > 0) append("\n")
                withStyle(codeBlockStyle) {
                    append(segment.code)
                }
            }
            is Segment.Text -> {
                val lines = segment.content.lines()
                lines.forEachIndexed { lineIdx, rawLine ->
                    if (segIdx > 0 && lineIdx == 0 || lineIdx > 0) append("\n")

                    val line = rawLine.trimEnd()

                    when {
                        line.matches(Regex("""^-{3,}$""")) || line.matches(Regex("""^\*{3,}$""")) -> {
                            withStyle(baseStyle.copy(color = baseColor.copy(alpha = 0.3f))) {
                                append("─────────────────")
                            }
                        }
                        line.startsWith("> ") -> {
                            val content = line.removePrefix("> ")
                            withStyle(baseStyle.copy(color = baseColor.copy(alpha = 0.7f))) {
                                append("┃ ")
                            }
                            appendInlineMarkdown(
                                content,
                                baseStyle.copy(fontStyle = FontStyle.Italic),
                                inlineCodeStyle
                            )
                        }
                        line.startsWith("### ") -> {
                            appendInlineMarkdown(
                                line.removePrefix("### "),
                                baseStyle.copy(fontWeight = FontWeight.Bold, fontSize = baseFontSize * 1.1f),
                                inlineCodeStyle
                            )
                        }
                        line.startsWith("## ") -> {
                            appendInlineMarkdown(
                                line.removePrefix("## "),
                                baseStyle.copy(fontWeight = FontWeight.Bold, fontSize = baseFontSize * 1.2f),
                                inlineCodeStyle
                            )
                        }
                        line.startsWith("# ") -> {
                            appendInlineMarkdown(
                                line.removePrefix("# "),
                                baseStyle.copy(fontWeight = FontWeight.Bold, fontSize = baseFontSize * 1.35f),
                                inlineCodeStyle
                            )
                        }
                        line.matches(Regex("""^[-*]\s+.*""")) -> {
                            val content = line.replaceFirst(Regex("""^[-*]\s+"""), "")
                            withStyle(baseStyle) { append("  \u2022 ") }
                            appendInlineMarkdown(content, baseStyle, inlineCodeStyle)
                        }
                        line.matches(Regex("""^\d+\.\s+.*""")) -> {
                            val match = Regex("""^(\d+\.)\s+(.*)""").find(line)!!
                            withStyle(baseStyle) { append("  ${match.groupValues[1]} ") }
                            appendInlineMarkdown(match.groupValues[2], baseStyle, inlineCodeStyle)
                        }
                        else -> {
                            appendInlineMarkdown(line, baseStyle, inlineCodeStyle)
                        }
                    }
                }
            }
        }
    }
}

private sealed class Segment {
    data class Text(val content: String) : Segment()
    data class CodeBlock(val language: String, val code: String) : Segment()
}

private fun splitCodeBlocks(text: String): List<Segment> {
    val segments = mutableListOf<Segment>()
    val fencePattern = Regex("""^```(\w*)""")
    val lines = text.lines()
    var i = 0
    val textBuffer = StringBuilder()

    while (i < lines.size) {
        val match = fencePattern.find(lines[i].trimEnd())
        if (match != null) {
            if (textBuffer.isNotEmpty()) {
                segments.add(Segment.Text(textBuffer.toString()))
                textBuffer.clear()
            }
            val lang = match.groupValues[1]
            val codeLines = mutableListOf<String>()
            i++
            while (i < lines.size && !lines[i].trimEnd().startsWith("```")) {
                codeLines.add(lines[i])
                i++
            }
            segments.add(Segment.CodeBlock(lang, codeLines.joinToString("\n")))
            i++ // skip closing fence
        } else {
            if (textBuffer.isNotEmpty()) textBuffer.append("\n")
            textBuffer.append(lines[i])
            i++
        }
    }

    if (textBuffer.isNotEmpty()) {
        segments.add(Segment.Text(textBuffer.toString()))
    }

    return segments
}

private fun AnnotatedString.Builder.appendInlineMarkdown(
    text: String,
    baseStyle: SpanStyle,
    inlineCodeStyle: SpanStyle
) {
    var i = 0
    val plainRun = StringBuilder()

    fun flushPlain() {
        if (plainRun.isNotEmpty()) {
            withStyle(baseStyle) { append(plainRun.toString()) }
            plainRun.clear()
        }
    }

    while (i < text.length) {
        when {
            text.startsWith("**", i) -> {
                flushPlain()
                val closeIdx = text.indexOf("**", i + 2)
                if (closeIdx != -1) {
                    withStyle(baseStyle.copy(fontWeight = FontWeight.Bold)) {
                        append(text.substring(i + 2, closeIdx))
                    }
                    i = closeIdx + 2
                } else {
                    plainRun.append("**")
                    i += 2
                }
            }
            text[i] == '*' && (i + 1 < text.length && text[i + 1] != '*') -> {
                flushPlain()
                val closeIdx = text.indexOf('*', i + 1)
                if (closeIdx != -1) {
                    withStyle(baseStyle.copy(fontStyle = FontStyle.Italic)) {
                        append(text.substring(i + 1, closeIdx))
                    }
                    i = closeIdx + 1
                } else {
                    plainRun.append('*')
                    i += 1
                }
            }
            text[i] == '`' -> {
                flushPlain()
                val closeIdx = text.indexOf('`', i + 1)
                if (closeIdx != -1) {
                    withStyle(inlineCodeStyle) {
                        append(text.substring(i + 1, closeIdx))
                    }
                    i = closeIdx + 1
                } else {
                    plainRun.append('`')
                    i += 1
                }
            }
            else -> {
                val codePoint = Character.codePointAt(text, i)
                plainRun.appendCodePoint(codePoint)
                i += Character.charCount(codePoint)
            }
        }
    }
    flushPlain()
}

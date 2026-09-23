package com.mccal.folio

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle

/**
 * The little of Markdown a package's page is allowed to use.
 *
 * `format-v1.md` promises authors paragraphs, bold, italic, lists and links, and nothing else - no headings, no
 * HTML, no images, no tables. Folio drew the text raw until now, so an author following the documentation got
 * asterisks on the screen. This reads that subset and leaves everything else exactly as it was typed, which is the
 * safe way round: an author's stray asterisk shows up as an asterisk rather than swallowing the rest of a sentence.
 *
 * Deliberately not a Markdown parser. It is a few lines of rules over text a signed source published, and the only
 * thing it can produce is styled text and an https link, so there is nothing here for a page to escape into.
 */
internal object MarketText {

    /** A paragraph of a page: [bullet] marks the lines that were written as a list. */
    data class Para(val text: AnnotatedString, val bullet: Boolean)

    /** Splits on blank lines, and turns each run of `- ` lines into its own bulleted paragraphs. */
    fun paragraphs(source: String, link: Color = FolioColors.BlueOnDark): List<Para> = buildList {
        for (chunk in source.split(Regex("\n\\s*\n"))) {
            val lines = chunk.trim().lines().map { it.trim() }.filter { it.isNotEmpty() }
            if (lines.isEmpty()) continue
            // A list item is one paragraph each; anything else is joined back up, the way Markdown wraps.
            if (lines.all { it.startsWith("- ") || it.startsWith("* ") }) {
                lines.forEach { add(Para(inline(it.drop(2), link), bullet = true)) }
            } else {
                add(Para(inline(lines.joinToString(" "), link), bullet = false))
            }
        }
    }

    /**
     * Bold, italic and links inside one paragraph.
     *
     * `**bold**`, `*italic*` (and `_italic_`), `[words](https://…)`. A marker with nothing to close it, or a link to
     * anything but https, is left as the author typed it rather than guessed at.
     */
    fun inline(source: String, link: Color = FolioColors.BlueOnDark): AnnotatedString = buildAnnotatedString {
        var i = 0
        while (i < source.length) {
            val rest = source.substring(i)
            val emphasis = MARKERS.firstOrNull { rest.startsWith(it.mark) }
            val closed = emphasis?.let { rest.indexOf(it.mark, it.mark.length).takeIf { end -> end > it.mark.length } }
            val linked = LINK.matchAt(rest, 0)?.takeIf { it.groupValues[2].startsWith("https://") }
            when {
                emphasis != null && closed != null -> {
                    withStyle(emphasis.style) { append(inline(rest.substring(emphasis.mark.length, closed), link)) }
                    i += closed + emphasis.mark.length
                }
                linked != null -> {
                    val url = linked.groupValues[2]
                    withLink(LinkAnnotation.Url(url, TextLinkStyles(SpanStyle(color = link)))) {
                        append(inline(linked.groupValues[1], link))
                    }
                    i += linked.value.length
                }
                else -> {
                    append(source[i])
                    i++
                }
            }
        }
    }

    private data class Marker(val mark: String, val style: SpanStyle)

    /** Longest first, so `**bold**` isn't read as an empty italic. */
    private val MARKERS = listOf(
        Marker("**", SpanStyle(fontWeight = FontWeight.SemiBold)),
        Marker("__", SpanStyle(fontWeight = FontWeight.SemiBold)),
        Marker("*", SpanStyle(fontStyle = FontStyle.Italic)),
        Marker("_", SpanStyle(fontStyle = FontStyle.Italic)),
    )

    private val LINK = Regex("""\[([^\[\]]{1,200})]\(([^()\s]{1,500})\)""")
}

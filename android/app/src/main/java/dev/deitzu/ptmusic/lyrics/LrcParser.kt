package dev.deitzu.ptmusic.lyrics

data class LrcLine(val timeMs: Long, val text: String)

data class LyricLineBundle(
    val timeMs: Long,
    val original: String,
    val romanized: String = "",
    val translated: String = ""
)

object LrcParser {
    private val prefix = Regex("""^\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?\](.*)$""")

    fun parse(input: String, offsetMs: Long = 0L): List<LrcLine> {
        if (input.isBlank()) return emptyList()
        val out = mutableListOf<LrcLine>()
        input.lineSequence().forEach { raw ->
            var line = raw.trim()
            while (true) {
                val m = prefix.matchEntire(line) ?: break
                val min = m.groupValues[1].toLongOrNull() ?: break
                val sec = m.groupValues[2].toLongOrNull() ?: break
                val f = m.groupValues[3]
                val ms = when (f.length) {
                    0 -> 0L
                    1 -> f.toLong() * 100L
                    2 -> f.toLong() * 10L
                    else -> f.take(3).toLong()
                }
                out += LrcLine(
                    (min * 60_000L + sec * 1_000L + ms + offsetMs).coerceAtLeast(0L),
                    m.groupValues[4].trim()
                )
                line = m.groupValues[4].trim()
            }
        }
        return out.filter { it.text.isNotBlank() }.sortedBy { it.timeMs }
    }

    fun merge(original: String, romanized: String, translated: String, offsetMs: Long): List<LyricLineBundle> {
        val o = parse(original, offsetMs)
        val r = parse(romanized, offsetMs)
        val t = parse(translated, offsetMs)
        return o.mapIndexed { index, value ->
            LyricLineBundle(
                value.timeMs,
                value.text,
                r.getOrNull(index)?.text.orEmpty(),
                t.getOrNull(index)?.text.orEmpty()
            )
        }
    }

    fun lineAt(lines: List<LyricLineBundle>, positionMs: Long): LyricLineBundle? =
        lines.lastOrNull { it.timeMs <= positionMs }
}

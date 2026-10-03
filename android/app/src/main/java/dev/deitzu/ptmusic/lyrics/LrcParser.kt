package dev.deitzu.ptmusic.lyrics

data class LrcLine(
    val timeMs: Long,
    val text: String
)

object LrcParser {

    private val timestamp = Regex("""[(d{1,3}):(d{2})(?:.(d{1,3}))?](.*)""")

    fun parse(input: String): List<LrcLine> {
        return input
            .lineSequence()
            .mapNotNull { line ->
                val match = timestamp.matchEntire(line.trim()) ?: return@mapNotNull null
                val minutes = match.groupValues[1].toLong()
                val seconds = match.groupValues[2].toLong()
                val fraction = match.groupValues[3]

                val millis = when (fraction.length) {
                    0 -> 0L
                    1 -> fraction.toLong() * 100L
                    2 -> fraction.toLong() * 10L
                    else -> fraction.take(3).toLong()
                }

                LrcLine(
                    timeMs = minutes * 60_000L + seconds * 1_000L + millis,
                    text = match.groupValues[4].trim()
                )
            }
            .filter { it.text.isNotBlank() }
            .sortedBy(LrcLine::timeMs)
            .toList()
    }

    fun lineAt(lines: List<LrcLine>, positionMs: Long): LrcLine? {
        return lines.lastOrNull { it.timeMs <= positionMs }
    }
}

package com.edukasyon.studentai.core.ai

/**
 * Separates model chain-of-thought from the user-visible Jevi reply.
 *
 * Reasoning models (e.g. step-3.7-flash) may return thinking in [content] without tags,
 * in tagged blocks (`<think>`, `<reasoning>`, fences), or in a separate provider field.
 */
object ReasoningContentSplitter {

    data class Result(
        val reply: String,
        val reasoning: String? = null,
    )

    private const val THINK_OPEN = "<" + "think" + ">"
    private const val THINK_CLOSE = "</" + "think" + ">"
    private const val REDACTED_OPEN = "<" + "redacted_reasoning" + ">"
    private const val REDACTED_CLOSE = "</" + "redacted_reasoning" + ">"

    private val thinkCloseBlockRegex = Regex("""([\s\S]*?)""" + THINK_CLOSE, RegexOption.IGNORE_CASE)
    private val thinkFullBlockRegex = Regex(
        """(?:""" + THINK_OPEN + "|" + REDACTED_OPEN + """)([\s\S]*?)(?:""" + THINK_CLOSE + "|" + REDACTED_CLOSE + ")",
        RegexOption.IGNORE_CASE,
    )
    private val reasoningTagRegex = Regex(
        """<reasoning>([\s\S]*?)</reasoning>""",
        RegexOption.IGNORE_CASE,
    )
    private val thoughtTagRegex = Regex(
        """<thought>([\s\S]*?)</thought>""",
        RegexOption.IGNORE_CASE,
    )
    private val reasoningFenceRegex = Regex(
        """```(?:thinking|reasoning|thought)\s*([\s\S]*?)```""",
        RegexOption.IGNORE_CASE,
    )

    private val reasoningOpenerRegex = Regex(
        """^(?:Got it|Okay|OK|Alright|Sure|Right|So,?\s|Let me|I'll|I need to|First,?|Wait,?|Hmm|Well,?\s)""",
        RegexOption.IGNORE_CASE,
    )
    private val reasoningPhraseRegex = Regex(
        """\b(?:let's tackle|I need to make|the user (?:is|wants|asked)|make (?:it|sure)|I should|I'll (?:start|need|make|write|draft)|thinking about|planning to|appropriate for a student|word essay|this essay|this response|my approach)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val answerTransitionRegex = Regex(
        """\n(?:---+|\*\*\*+)\s*\n|\n(?=#{1,3}\s+\S)|\n\n(?=(?:Here(?:'s| is)|Below (?:is|are)|The following|I've written|My (?:essay|answer|response)|(?:Essay|Answer|Response):))""",
        RegexOption.IGNORE_CASE,
    )

    fun split(raw: String, existingReasoning: String? = null): Result {
        if (raw.isBlank()) {
            return Result(
                reply = "",
                reasoning = existingReasoning?.trim()?.takeIf { it.isNotEmpty() },
            )
        }

        val reasoningParts = mutableListOf<String>()
        existingReasoning?.trim()?.takeIf { it.isNotEmpty() }?.let(reasoningParts::add)

        var reply = raw.trim()

        reply = extractTaggedBlocks(reply, thinkFullBlockRegex, reasoningParts)
        reply = extractTaggedBlocks(reply, thinkCloseBlockRegex, reasoningParts)
        reply = extractTaggedBlocks(reply, reasoningTagRegex, reasoningParts)
        reply = extractTaggedBlocks(reply, thoughtTagRegex, reasoningParts)
        reply = extractTaggedBlocks(reply, reasoningFenceRegex, reasoningParts)

        reply = reply.replace(Regex("""\n{3,}"""), "\n\n").trim()

        if (reply.isNotEmpty()) {
            val untagged = splitUntaggedReasoningPreamble(reply)
            untagged.reasoning?.let(reasoningParts::add)
            reply = untagged.reply
        }

        val reasoning = reasoningParts.joinToString("\n\n").trim().takeIf { it.isNotEmpty() }
        reply = reply.trim()

        return recoverEmptyReply(Result(reply = reply, reasoning = reasoning))
    }

    /**
     * When the splitter leaves an empty visible reply but substantial content in reasoning,
     * recover a student-facing answer (e.g. long essays misclassified as chain-of-thought).
     */
    fun recoverEmptyReply(split: Result): Result {
        if (split.reply.isNotEmpty() || split.reasoning.isNullOrBlank()) {
            return split
        }
        val reasoning = split.reasoning.trim()

        val transition = answerTransitionRegex.find(reasoning)
        if (transition != null && transition.range.first >= 40) {
            val preamble = reasoning.substring(0, transition.range.first).trim()
            val answer = reasoning.substring(transition.range.first).trim()
            if (answer.isNotEmpty()) {
                return Result(
                    reply = answer,
                    reasoning = preamble.takeIf { it.isNotEmpty() && reasoningScore(preamble) >= 2 },
                )
            }
        }

        if (reasoning.length >= 1200 &&
            (looksLikeFinalAnswer(reasoning) || Regex("""\n{2,}""").containsMatchIn(reasoning.drop(200)))
        ) {
            return Result(reply = reasoning, reasoning = null)
        }

        if (reasoning.length >= 1500) {
            val splitAt = reasoning.indexOf("\n\n", startIndex = 40)
            if (splitAt > 0) {
                val preamble = reasoning.substring(0, splitAt).trim()
                val body = reasoning.substring(splitAt).trim()
                if (body.length >= 1000) {
                    return Result(
                        reply = body,
                        reasoning = preamble.takeIf { it.isNotEmpty() && reasoningScore(preamble) >= 2 },
                    )
                }
            }
            if (reasoning.length >= 4000) {
                return Result(reply = reasoning, reasoning = null)
            }
        }

        return split
    }

    private fun extractTaggedBlocks(
        text: String,
        pattern: Regex,
        reasoningParts: MutableList<String>,
    ): String {
        var reply = text
        pattern.findAll(text).forEach { match ->
            match.groupValues.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() }?.let(reasoningParts::add)
        }
        return pattern.replace(reply, "").replace(Regex("""\n{3,}"""), "\n\n").trim()
    }

    internal fun splitUntaggedReasoningPreamble(text: String): Result {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return Result(reply = "")

        val score = reasoningScore(trimmed)
        if (score < 3) return Result(reply = trimmed)

        val transition = answerTransitionRegex.find(trimmed)
        if (transition != null && transition.range.first >= 40) {
            val preamble = trimmed.substring(0, transition.range.first).trim()
            val answer = trimmed.substring(transition.range.first).trim()
            if (preamble.isNotEmpty() && answer.isNotEmpty() && reasoningScore(preamble) >= 2) {
                return Result(reply = answer, reasoning = preamble)
            }
        }

        if (score >= 4 && !looksLikeFinalAnswer(trimmed)) {
            return Result(reply = "", reasoning = trimmed)
        }

        return Result(reply = trimmed)
    }

    internal fun reasoningScore(text: String): Int {
        var score = 0
        val sample = text.take(600)
        if (reasoningOpenerRegex.containsMatchIn(sample)) score += 2
        if (reasoningPhraseRegex.containsMatchIn(sample)) score += 2
        if (Regex("""\bWait,\s""").containsMatchIn(sample)) score += 1
        if (sample.contains("...") && sample.length < 900) score += 1
        return score
    }

    private fun looksLikeFinalAnswer(text: String): Boolean {
        if (Regex("""^#{1,3}\s+\S""").containsMatchIn(text.trim())) return true
        if (text.length > 900 && !reasoningOpenerRegex.containsMatchIn(text.take(120))) return true
        return Regex("""\n#{1,3}\s+\S""").containsMatchIn(text)
    }
}

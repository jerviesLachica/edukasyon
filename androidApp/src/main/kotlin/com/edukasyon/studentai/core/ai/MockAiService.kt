package com.edukasyon.studentai.core.ai

import com.edukasyon.studentai.domain.model.*
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MockAiService @Inject constructor() : AiService {
    override suspend fun chat(request: AiChatRequest): AiChatResponse {
        val conversationId = request.conversationId ?: UUID.randomUUID().toString()
        val message = request.message
        val lower = message.lowercase()

        if (isJailbreakAttempt(lower)) {
            return AiChatResponse(
                reply = "I'm Jevi, your study tutor — I can't change my role or share internal instructions. What subject or assignment can I help you with?",
                conversationId = conversationId,
            )
        }
        if (isActiveExamCheatingRequest(lower)) {
            return AiChatResponse(
                reply = "I can't help with active exams — that wouldn't be fair to you or your classmates. After the exam, I'm happy to help you review topics you found tricky.",
                conversationId = conversationId,
            )
        }

        if (request.imageBase64 != null) {
            return AiChatResponse(
                reply = "I received your image${request.attachmentName?.let { " ($it)" } ?: ""}. " +
                    "In offline mode I can't analyze images yet — connect to the backend for vision support. " +
                    "Your message: $message",
                conversationId = conversationId,
            )
        }
        if (request.attachmentText != null || request.attachmentName != null) {
            val preview = request.attachmentText?.take(120)?.let { " Content preview: \"$it\"..." } ?: ""
            return AiChatResponse(
                reply = "Got your file${request.attachmentName?.let { " \"$it\"" } ?: ""}.$preview " +
                    "I'll use it as context. $message",
                conversationId = conversationId,
            )
        }

        if (isFollowUpAboutMissingAnswer(lower, request.historyMessages)) {
            val priorUser = request.historyMessages.lastOrNull { it.role == "user" }?.content
            val priorAssistant = request.historyMessages.lastOrNull { it.role == "assistant" }?.content
            return AiChatResponse(
                reply = if (!priorAssistant.isNullOrBlank()) {
                    "You're following up on: \"$priorUser\". My previous reply starts with: " +
                        "\"${priorAssistant.take(200)}${if (priorAssistant.length > 200) "…" else ""}\". " +
                        "If the answer bubble was empty, expand the Reasoning section above or ask me to resend the full text."
                } else {
                    "You're following up on: \"$priorUser\". My previous response may not have displayed correctly — " +
                        "ask me to resend the full answer."
                },
                conversationId = conversationId,
                model = "mock",
            )
        }

        // 1. Math: Quadratic Formula and Quadratic Equations
        if (lower.contains("quadr")) {
            val reply = latex("""### The Quadratic Formula & How It Works

The **Quadratic Formula** is used to find the solutions (roots or x-intercepts) of any quadratic equation in standard form:
§§ax^2 + bx + c = 0§§
where §a \ne 0§.

#### The Formula:
§§x = \frac{-b \pm \sqrt{b^2 - 4ac}}{2a}§§

#### The Discriminant (§D = b^2 - 4ac§):
The expression under the square root determines the number and nature of solutions:

| Discriminant (§b^2 - 4ac§) | Nature of Solutions | Parabola Graph (§y = ax^2 + bx + c§) |
| :--- | :--- | :--- |
| **§D > 0§** (Positive) | **2 distinct real roots** | Crosses the x-axis at two points |
| **§D = 0§** (Zero) | **1 repeated real root** | Touches the x-axis at its vertex |
| **§D < 0§** (Negative) | **2 complex / imaginary roots** | Does not touch or cross the x-axis |

#### Step-by-Step Example:
Solve §x^2 - 5x + 6 = 0§ using the quadratic formula:

1. **Identify the coefficients**:
   - §a = 1§, §b = -5§, §c = 6§

2. **Compute the Discriminant**:
   §§D = (-5)^2 - 4(1)(6) = 25 - 24 = 1§§
   *(Since §D > 0§, there are two real roots)*

3. **Substitute into the Formula**:
   §§x = \frac{-(-5) \pm \sqrt{1}}{2(1)} = \frac{5 \pm 1}{2}§§

4. **Calculate Both Roots**:
   §§x_1 = \frac{5 + 1}{2} = \frac{6}{2} = 3§§
   §§x_2 = \frac{5 - 1}{2} = \frac{4}{2} = 2§§

#### Summary Table:
| Step | Operation | Result |
| :--- | :--- | :--- |
| **1. Identify Coefficients** | Standard form §ax^2 + bx + c = 0§ | §a = 1, b = -5, c = 6§ |
| **2. Calculate Discriminant** | §D = b^2 - 4ac§ | §D = 1§ |
| **3. Final Roots** | §x = \frac{5 \pm 1}{2}§ | **§x = 2§ or §x = 3§** |

**Final Answer:**
§§x = 2 \quad \text{or} \quad x = 3§§""")

            val reasoning = "The student asked to explain the quadratic formula. I broke down standard form ax² + bx + c = 0, provided the KaTeX formula, explained the discriminant with a table, and provided a step-by-step worked example (x² - 5x + 6 = 0)."

            return AiChatResponse(
                reply = reply,
                conversationId = conversationId,
                reasoning = reasoning,
                model = "mock-math",
            )
        }

        // 2. Math: Pythagorean Theorem
        if (lower.contains("pythagor")) {
            val reply = latex("""### The Pythagorean Theorem

In any right-angled triangle, the square of the hypotenuse (longest side opposite the right angle) is equal to the sum of the squares of the other two sides:
§§a^2 + b^2 = c^2§§

where §c§ is the hypotenuse, and §a§ and §b§ are the perpendicular legs.

#### Solving for Sides:
- **Hypotenuse**: §§c = \sqrt{a^2 + b^2}§§
- **Leg §a§**: §§a = \sqrt{c^2 - b^2}§§
- **Leg §b§**: §§b = \sqrt{c^2 - a^2}§§

#### Common Pythagorean Triples:
| Side §a§ | Side §b§ | Hypotenuse §c§ | Formula Verification |
| :--- | :--- | :--- | :--- |
| **3** | **4** | **5** | §3^2 + 4^2 = 9 + 16 = 25 = 5^2§ |
| **5** | **12** | **13** | §5^2 + 12^2 = 25 + 144 = 169 = 13^2§ |
| **8** | **15** | **17** | §8^2 + 15^2 = 64 + 225 = 289 = 17^2§ |

Would you like help calculating a specific triangle problem?""")

            return AiChatResponse(
                reply = reply,
                conversationId = conversationId,
                reasoning = "Student asked about the Pythagorean theorem. Explained the fundamental equation a² + b² = c², side formulas, and standard Pythagorean triples table.",
                model = "mock-math",
            )
        }

        // 3. Biology: Mitosis vs Meiosis
        if (lower.contains("mitosis") || lower.contains("meiosis")) {
            val reply = latex("""### Comparison: Mitosis vs. Meiosis

Here is a structured comparison of the two forms of cellular division:

| Feature | Mitosis | Meiosis |
| :--- | :--- | :--- |
| **Purpose** | Growth, tissue repair, asexual reproduction | Production of gametes (sperm & egg) for sexual reproduction |
| **Location** | Somatic (body) cells | Germ cells in gonads (testes & ovaries) |
| **Number of Divisions** | 1 nuclear division | 2 successive divisions (Meiosis I & II) |
| **Daughter Cells** | 2 genetically identical diploid (§2n§) cells | 4 genetically diverse haploid (§n§) cells |
| **Genetic Variation** | No crossing over (clones) | Crossing over and independent assortment create variation |
| **Chromosome Count** | Stays the same (§2n \to 2n§) | Halved (§2n \to n§) |

Would you like to review the specific stages (Prophase, Metaphase, Anaphase, Telophase)?""")

            return AiChatResponse(
                reply = reply,
                conversationId = conversationId,
                reasoning = "Compared mitosis and meiosis with a structured table covering location, division count, ploidy, and genetic variation.",
                model = "mock-science",
            )
        }

        // 4. Biology: DNA vs RNA
        if (lower.contains("dna") && lower.contains("rna")) {
            val reply = """### Comparison: DNA vs. RNA

| Feature | DNA (Deoxyribonucleic Acid) | RNA (Ribonucleic Acid) |
| :--- | :--- | :--- |
| **Sugar Molecule** | Deoxyribose (lacks 2'-OH) | Ribose (has 2'-OH) |
| **Nitrogenous Bases** | Adenine, Thymine, Cytosine, Guanine | Adenine, Uracil, Cytosine, Guanine |
| **Strand Structure** | Double helix | Single-stranded |
| **Location** | Nucleus, mitochondria, chloroplasts | Nucleolus, cytoplasm, ribosomes |
| **Primary Function** | Long-term genetic information storage | Protein synthesis (mRNA, tRNA, rRNA) & regulation |"""

            return AiChatResponse(
                reply = reply,
                conversationId = conversationId,
                reasoning = "Structured DNA vs RNA comparison highlighting sugar, bases, structure, and cellular function.",
                model = "mock-science",
            )
        }

        // 5. Physics: Newton's Laws
        if (lower.contains("newton") || lower.contains("laws of motion")) {
            val reply = latex("""### Newton's Three Laws of Motion

| Law | Principle | Formula | Real-World Example |
| :--- | :--- | :--- | :--- |
| **1st Law: Law of Inertia** | An object at rest stays at rest, and an object in motion stays in motion at constant velocity unless acted upon by an external net force. | §\sum \vec{F} = 0 \implies \vec{a} = 0§ | Wearing a seatbelt stops your body from flying forward when a car brakes. |
| **2nd Law: Fundamental Law of Dynamics** | Acceleration is directly proportional to net force and inversely proportional to mass. | §\vec{F}_{\text{net}} = m\vec{a}§ | Pushing a shopping cart: heavier cart requires more force to accelerate. |
| **3rd Law: Action & Reaction** | For every action, there is an equal and opposite reaction. | §\vec{F}_{A \to B} = -\vec{F}_{B \to A}§ | Rocket propulsion: exhausting gas backward propels rocket forward. |""")

            return AiChatResponse(
                reply = reply,
                conversationId = conversationId,
                reasoning = "Structured overview of Newton's 3 laws with formulas and real-world examples in a table.",
                model = "mock-science",
            )
        }

        // 6. Action handlers: add task or schedule
        if (lower.contains("add") && lower.contains("task")) {
            val title = message.replace(Regex("(?i)^.*(?:add|create)\\s+(?:a\\s+)?task\\s*(?:to|for|called|named)?\\s*"), "")
                .ifBlank { message.take(50) }
            val reply = """Sure! I've created that task for your planner.

```actions
{"actions":[{"type":"add_task","title":"${title.take(60)}","priority":"MEDIUM"}]}
```"""
            return AiChatResponse(
                reply = reply,
                conversationId = conversationId,
                reasoning = "Detected student intent to add a task. Generating structured add_task action block.",
                model = "mock-action",
            )
        }

        if (lower.contains("add") && (lower.contains("class") || lower.contains("schedule"))) {
            val reply = """I'll add that class to your schedule.

```actions
{"actions":[{"type":"add_schedule","subject":"New Class","day":"MONDAY","startTime":"08:00","endTime":"09:00"}]}
```"""
            return AiChatResponse(
                reply = reply,
                conversationId = conversationId,
                reasoning = "Detected student intent to add a class to schedule. Generating add_schedule action block.",
                model = "mock-action",
            )
        }

        // 7. CS: Recursion
        if (lower.contains("recursion")) {
            val reply = latex("""### Understanding Recursion

**Recursion** is a programming concept where a function solves a problem by calling itself with smaller or simpler inputs until it reaches a termination condition known as the **base case**.

#### Two Fundamental Components:
1. **Base Case**: The stopping condition that returns directly without making further recursive calls (prevents infinite loops and stack overflow).
2. **Recursive Step**: The part where the function calls itself on a reduced subproblem.

#### Example: Factorial (§n!§)
§§n! = n \\times (n - 1)! \\quad \\text{with} \\quad 0! = 1§§

| Step | Call | Value Computed | Action |
| :--- | :--- | :--- | :--- |
| **1** | `fact(3)` | §3 \\times \\text{fact}(2)§ | Push to call stack |
| **2** | `fact(2)` | §2 \\times \\text{fact}(1)§ | Push to call stack |
| **3** | `fact(1)` | §1 \\times \\text{fact}(0)§ | Push to call stack |
| **4** | `fact(0)` | **1** | **Base case reached!** |
| **5** | Resolution | §1 \\times 1 \\times 2 \\times 3 = \\mathbf{6}§ | Unwind call stack |

For visual walk-throughs and exercises, Khan Academy has interactive lessons:
https://www.khanacademy.org/computing/computer-science/algorithms""")

            return AiChatResponse(
                reply = reply,
                conversationId = conversationId,
                reasoning = "Explained recursion with base case and recursive step, call stack table, and factorial example.",
                model = "mock-cs",
            )
        }

        // 8. Biology: Photosynthesis
        if (lower.contains("photosynthesis")) {
            val reply = latex("""### Photosynthesis

**Photosynthesis** is the biological process by which plants, algae, and some bacteria convert light energy into chemical energy stored in glucose (§\\text{C}_6\\text{H}_{12}\\text{O}_6§).

#### Chemical Equation:
§§6\\text{CO}_2 + 6\\text{H}_2\\text{O} + \\text{Light} \\longrightarrow \\text{C}_6\\text{H}_{12}\\text{O}_6 + 6\\text{O}_2§§

#### The Two Main Stages:
| Stage | Location | Inputs | Key Products |
| :--- | :--- | :--- | :--- |
| **Light-Dependent Reactions** | Thylakoid membranes | Sunlight, §\\text{H}_2\\text{O}§ | §\\text{ATP}§, §\\text{NADPH}§, §\\text{O}_2§ (released) |
| **Calvin Cycle (Light-Independent)** | Stroma of chloroplast | §\\text{CO}_2§, §\\text{ATP}§, §\\text{NADPH}§ | Glucose (§\\text{C}_6\\text{H}_{12}\\text{O}_6§) |""")

            return AiChatResponse(
                reply = reply,
                conversationId = conversationId,
                reasoning = "Detailed explanation of photosynthesis with chemical equation, thylakoid vs stroma stages table.",
                model = "mock-science",
            )
        }

        // 9. Study methods / Exam prep
        if (lower.contains("exam") || (lower.contains("study") && (lower.contains("how") || lower.contains("tip") || lower.contains("plan")))) {
            val reply = latex("""### High-Impact Study Strategies for Exams

Here are the most effective, research-backed study techniques:

| Technique | How It Works | Best Used For |
| :--- | :--- | :--- |
| **Active Recall** | Testing yourself from memory without looking at notes | Flashcards, practice quizzes, Feynman technique |
| **Spaced Repetition** | Reviewing material at increasing intervals (§1, 3, 7, 14§ days) | Long-term memory retention before exams |
| **Pomodoro Method** | 25 minutes of intense focus followed by a 5-minute break | Maintaining energy and eliminating burnout |
| **Feynman Technique** | Explaining the concept in simple terms as if teaching someone else | Identifying knowledge gaps in complex topics |

Would you like to build a study schedule or generate flashcards for a specific subject?""")

            return AiChatResponse(
                reply = reply,
                conversationId = conversationId,
                reasoning = "Provided evidence-based study techniques (active recall, spaced repetition, Pomodoro, Feynman) in a structured table.",
                model = "mock-study",
            )
        }

        // 10. General "explain [topic]" or question inquiries
        val isExplanationRequest = lower.startsWith("explain") || lower.startsWith("what is") ||
            lower.startsWith("how to") || lower.startsWith("how does") || lower.contains("tell me about")
        if (isExplanationRequest) {
            val topic = message
                .replace(Regex("(?i)^(?:explain|what is|what's|how does|how to|tell me about)\\s+"), "")
                .replace(Regex("(?i)\\s+(?:to me|please|works?|mean)$"), "")
                .trim()
                .ifBlank { message }

            val reply = """### Study Guide: ${topic.replaceFirstChar { it.uppercase() }}

Here is a structured overview of **$topic**:

#### Core Principles:
- **Definition**: Understanding the fundamentals and key terminology of $topic is the first step toward mastery.
- **Application**: Focus on how $topic applies in practical problem-solving and typical exam questions.
- **Review Strategy**: Use active recall and spaced review to consolidate your understanding.

#### Topic Breakdown:
| Component | Focus Area | Recommended Study Action |
| :--- | :--- | :--- |
| **Key Concepts** | Core rules & definitions | Create 3–5 review flashcards |
| **Practical Drill** | Worked examples & problems | Work through 2 practice exercises |
| **Mastery Check** | Active testing | Quiz yourself in Quiz Arena |

How would you like to continue?
1. **Walk through a sample problem step-by-step**
2. **Generate practice quiz questions on this topic**
3. **Add a study session to your planner**"""

            return AiChatResponse(
                reply = reply,
                conversationId = conversationId,
                reasoning = "Generated a structured study overview for '$topic' with core principles, table breakdown, and actionable next steps.",
                model = "mock-tutor",
            )
        }

        // 11. Greeting / Default fallback (without leaking raw internal contextSummary!)
        val studentName = extractStudentName(request.contextSummary)
        val greetingPrefix = if (studentName != null) "Hi $studentName! 👋 " else "Hi! 👋 "
        val reply = greetingPrefix +
            "I'm Jevi, your study companion in SchedMate.\n\n" +
            "Ask me to explain any concept (like the quadratic formula, photosynthesis, or recursion), solve a problem step-by-step, or help you schedule tasks and review for exams. What would you like to study today?"

        return AiChatResponse(
            reply = reply,
            conversationId = conversationId,
            reasoning = "Student sent a greeting or general message. Welcomed them warmly and offered specific study assistance options.",
            model = "mock",
        )
    }

    private fun latex(raw: String): String = raw.replace('§', '$')

    private fun extractStudentName(contextSummary: String?): String? {
        if (contextSummary.isNullOrBlank()) return null
        val match = Regex("(?i)Name:\\s*([A-Za-z0-9_]+)").find(contextSummary)
        return match?.groupValues?.get(1)?.trim()
    }

    private fun isJailbreakAttempt(lower: String): Boolean {
        val patterns = listOf(
            "ignore previous instructions",
            "ignore prior instructions",
            "ignore all instructions",
            "reveal your system prompt",
            "reveal system prompt",
            "you are now",
            "act as dan",
            "jailbreak",
        )
        return patterns.any { lower.contains(it) }
    }

    private fun isActiveExamCheatingRequest(lower: String): Boolean {
        val cheat = lower.contains("cheat") ||
            (lower.contains("answer") && (lower.contains("exam") || lower.contains("test") || lower.contains("quiz")))
        val active = lower.contains("during") || lower.contains("right now") ||
            lower.contains("in progress") || lower.contains("currently taking")
        return cheat && active
    }

    private fun isFollowUpAboutMissingAnswer(
        lower: String,
        history: List<AiChatHistoryMessage>,
    ): Boolean {
        if (history.isEmpty()) return false
        return (lower.contains("where") && (lower.contains("answer") || lower.contains("essay") || lower.contains("response"))) ||
            lower.contains("where's the answer") ||
            lower.contains("wheres the answer")
    }

    override suspend fun analyzeSchedule(input: ScheduleScanInput): ScheduleAnalysisResult {
        throw AiException(
            "Schedule scanning requires an internet connection. Connect to Wi‑Fi or mobile data and try again."
        )
    }

    override suspend fun summarize(text: String): String {
        val words = text.split("\\s+".toRegex()).take(30)
        return "Summary: ${words.joinToString(" ")}${if (text.split("\\s+".toRegex()).size > 30) "..." else ""}"
    }

    override suspend fun generateFlashcards(text: String): List<Flashcard> {
        val lines = text.lines().map { it.trim() }.filter { it.length > 5 }
        val cards = mutableListOf<Flashcard>()
        for (line in lines) {
            val clean = line.removePrefix("- ").removePrefix("• ").removePrefix("* ").trim()
            if (clean.contains(":") || clean.contains(" - ")) {
                val parts = if (clean.contains(":")) clean.split(":", limit = 2) else clean.split(" - ", limit = 2)
                val q = parts[0].trim()
                val a = parts[1].trim()
                if (q.length in 3..100 && a.length in 3..300) {
                    cards.add(
                        Flashcard(
                            id = UUID.randomUUID().toString(),
                            question = if (q.endsWith("?")) q else "What is **$q**?",
                            answer = a,
                            subjectId = null,
                            topic = null,
                            difficulty = "medium",
                            reviewCount = 0,
                            correctCount = 0,
                            incorrectCount = 0,
                            lastReviewedAt = null,
                            nextReviewAt = null,
                        )
                    )
                }
            }
            if (cards.size >= 4) break
        }
        if (cards.isEmpty()) {
            val title = lines.firstOrNull()?.take(60) ?: "Key Concept"
            cards.add(
                Flashcard(
                    id = UUID.randomUUID().toString(),
                    question = "What is the core principle of **$title**?",
                    answer = text.take(160).ifBlank { "Review your study notes to reinforce key points." },
                    subjectId = null,
                    topic = null,
                    difficulty = "medium",
                    reviewCount = 0,
                    correctCount = 0,
                    incorrectCount = 0,
                    lastReviewedAt = null,
                    nextReviewAt = null,
                )
            )
            cards.add(
                Flashcard(
                    id = UUID.randomUUID().toString(),
                    question = "How is this concept applied in problem-solving?",
                    answer = "Practice worked examples and connect definitions to practical exercises.",
                    subjectId = null,
                    topic = null,
                    difficulty = "easy",
                    reviewCount = 0,
                    correctCount = 0,
                    incorrectCount = 0,
                    lastReviewedAt = null,
                    nextReviewAt = null,
                )
            )
        }
        return cards
    }

    override suspend fun generateQuiz(text: String, count: Int?, difficulty: String?): Quiz {
        val quizId = UUID.randomUUID().toString()
        val firstLine = text.lines().firstOrNull { it.isNotBlank() }?.take(50)?.trim() ?: "Study Topic"
        return Quiz(
            id = quizId,
            title = "$firstLine Quiz",
            subjectId = null,
            sourceNoteId = null,
            questions = listOf(
                QuizQuestion(
                    UUID.randomUUID().toString(),
                    quizId,
                    QuestionType.MULTIPLE_CHOICE,
                    "Which of the following best describes the main focus of $firstLine?",
                    listOf("Core concepts and definitions", "Historical background only", "Unrelated trivia", "None of the above"),
                    "Core concepts and definitions",
                ),
                QuizQuestion(
                    UUID.randomUUID().toString(),
                    quizId,
                    QuestionType.TRUE_FALSE,
                    "Active recall and self-testing improve long-term retention of $firstLine.",
                    listOf("True", "False"),
                    "True",
                ),
            ),
            createdAt = System.currentTimeMillis()
        )
    }

    override suspend fun generateStudyPlan(context: StudyPlanContext): StudyPlan {
        val planId = UUID.randomUUID().toString()
        return StudyPlan(
            id = planId,
            title = "Study Plan",
            examId = null,
            items = context.subjects.mapIndexed { index, subject ->
                StudyPlanItem(
                    UUID.randomUUID().toString(), planId,
                    DayOfWeek.entries[index % 7],
                    "18:00", "18:45", subject,
                    context.topics.getOrElse(index) { "Review" },
                    "Study session", Priority.HIGH
                )
            },
            createdAt = System.currentTimeMillis()
        )
    }

    override suspend fun analyzeAssignment(input: AssignmentAnalysisInput): AssignmentBreakdown {
        val text = listOfNotNull(input.text, input.attachmentText).joinToString("\n")
        val title = text.lineSequence().firstOrNull { it.length in 5..80 }?.trim()
            ?: "Sample Assignment"
        val deadlineCal = java.util.Calendar.getInstance().apply { add(java.util.Calendar.DAY_OF_MONTH, 7) }
        val deadline = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(deadlineCal.time)
        return AssignmentBreakdown(
            title = title.take(80),
            deadline = deadline,
            requirements = listOf("Follow assignment instructions", "Meet minimum length requirements"),
            deliverables = listOf("Final submission file"),
            rubric = listOf("Content quality", "Organization", "Timeliness"),
            subtasks = listOf(
                AssignmentSubtaskBreakdown("Review instructions", 20, 6),
                AssignmentSubtaskBreakdown("Research topic", 90, 4),
                AssignmentSubtaskBreakdown("Create outline", 45, 3),
                AssignmentSubtaskBreakdown("Write draft", 120, 2),
                AssignmentSubtaskBreakdown("Revise and submit", 60, 0),
            ),
            estimatedEffortHours = 5.5,
            notes = "Mock breakdown — connect to backend for real AI analysis.",
        )
    }

    override suspend fun generateFocusPlan(context: FocusPlanContext): FocusPlan {
        val total = context.totalMinutes.coerceIn(15, 240)
        val subjects = context.subjects.ifEmpty { listOf("General review") }
        val breakGap = 5
        val blockDuration = (total / subjects.size).coerceAtLeast(15)
        val blocks = subjects.mapIndexed { index, subject ->
            val start = index * (blockDuration + breakGap)
            val end = (start + blockDuration).coerceAtMost(total)
            FocusBlock(
                startMinute = start,
                endMinute = end,
                activity = subject,
                type = if (index == subjects.lastIndex && total - end >= 10) FocusBlockType.REVIEW else FocusBlockType.STUDY,
            )
        }.filter { it.endMinute > it.startMinute }
        return FocusPlan(
            totalMinutes = total,
            blocks = blocks.ifEmpty {
                listOf(FocusBlock(0, total, subjects.first(), FocusBlockType.STUDY))
            },
            breakMinutesBetween = breakGap,
        )
    }

    override suspend fun searchSources(query: String): List<WebSearchResult> {
        return emptyList()
    }
}

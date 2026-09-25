package com.example.personal_financestudydaily_routine_assistant.ai

import com.example.personal_financestudydaily_routine_assistant.data.database.*
import com.example.personal_financestudydaily_routine_assistant.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class AssistantContext(
    val todayExpense: Double,
    val monthExpense: Double,
    val todayStudyMinutes: Int,
    val openTasks: Int,
    val totalTasks: Int,
    val classCount: Int,
    val categorySpend: Map<String, Double> = emptyMap(),
    val cpSolvedByPlatform: Map<String, Int> = emptyMap(),
    val cpTargetsByPlatform: Map<String, Int> = emptyMap(),
    val cpTopicsThisWeek: Map<String, Int> = emptyMap(),
    val cpTopicsPreviousWeek: Map<String, Int> = emptyMap(),
    val academicClasses: List<String> = emptyList(),
    val upcomingExams: List<String> = emptyList(),
    val todayPlan: List<String> = emptyList(),
    val monthCategorySpend: Map<String, Double> = emptyMap()
)

interface AiAssistant {
    fun reply(message: String, context: AssistantContext): String
}

enum class AiProvider(val id: String, val displayName: String) {
    GEMINI("gemini", "Gemini"),
    OPENAI("openai", "OpenAI"),
    DEEPSEEK("deepseek", "DeepSeek"),
    LOCAL("local", "Local/Offline AI");

    companion object {
        fun fromId(id: String): AiProvider = entries.firstOrNull { it.id == id } ?: GEMINI
    }
}

/**
 * Offline-first assistant. A remote provider can implement the same interface later.
 */
class LocalAiAssistant : AiAssistant {
    fun documentAction(action: String, title: String, text: String): String {
        val label = action.lowercase()
        val source = text.trim().ifBlank { "No extracted text is available. Use the uploaded file as the source." }
        return when {
            label.contains("summar") -> "Summary of $title:\n${source.take(900)}"
            label.contains("important") -> "Important points from $title:\n" +
                source.split(Regex("(?<=[.!?])\\s+|\\n")).filter { it.isNotBlank() }.take(6)
                    .joinToString("\n") { "• ${it.trim()}" }
            label.contains("flashcard") -> "Flashcards from $title:\n" +
                source.split(Regex("(?<=[.!?])\\s+|\\n")).filter { it.isNotBlank() }.take(5)
                    .mapIndexed { index, sentence -> "${index + 1}. Q: What is the key idea here?\n   A: ${sentence.trim()}" }
                    .joinToString("\n\n")
            label.contains("mcq") || label.contains("quiz") -> "Practice quiz for $title:\n" +
                source.split(Regex("(?<=[.!?])\\s+|\\n")).filter { it.isNotBlank() }.take(5)
                    .mapIndexed { index, sentence -> "${index + 1}. Which statement is supported by the document?\n   A. ${sentence.trim()}\n   B. None of the above\n   Answer: A" }
                    .joinToString("\n\n")
            label.contains("study plan") -> "Study plan for $title:\nDay 1: Read and annotate the document.\nDay 2: Review the important points.\nDay 3: Practice with questions and flashcards.\nDay 4: Recall the topic without notes."
            label.contains("explain") -> "Explanation of $title:\n${source.take(1200)}"
            label.contains("answer") -> "Answer based on $title:\n${source.take(1200)}"
            else -> "Ask a question about $title and I will answer using the document."
        }
    }

    override fun reply(message: String, context: AssistantContext): String {
        val text = message.trim().lowercase()
            .replace('\u2019', '\'')
            .replace('\u2018', '\'')
            .replace('\u201c', '"')
            .replace('\u201d', '"')
        if (text.isBlank()) return "আপনি কী জানতে চান? খরচ, পড়াশোনা, task বা class সম্পর্কে জিজ্ঞেস করুন।"

        return when {
            containsAny(text, "হ্যালো", "hello", "hi", "হাই") ->
                "হ্যালো! আমি Smart Life Assistant। আপনার খরচ, পড়াশোনা, task এবং class routine নিয়ে সাহায্য করতে পারি।"
            containsAny(text, "আজ খরচ", "today expense", "আজকের expense") ->
                "আজ আপনার মোট খরচ ৳${money(context.todayExpense)}। কোনো expense যোগ করতে Expenses tab থেকে + চাপুন।"
            containsAny(text, "এই মাস", "this month", "monthly", "মাসে খরচ", "মাসের খরচ", "মাসে কত", "spent this month") ->
                monthlyExpenseSummary(context)
            isTodayPlanRequest(text) ->
                todayPlan(context)
            containsAny(text, "competitive", "coding", "cp", "problem", "graph", "analysis", "insight", "বিশ্লেষণ", "কোথায় বেশি") ->
                insight(context)
            containsAny(text, "পড়", "study", "স্টাডি", "কতক্ষণ পড়েছি") ->
                "আজ আপনি ${duration(context.todayStudyMinutes)} পড়েছেন। ${if (context.todayStudyMinutes == 0) "একটি study session শুরু করলে আমি progress track করব।" else "ভালো ধারাবাহিকতা রাখুন!"}"
            containsAny(text, "task", "কাজ", "todo") ->
                "আপনার ${context.openTasks}টি open task আছে, মোট task ${context.totalTasks}টি। Tasks tab থেকে নতুন task যোগ বা complete করতে পারবেন।"
            containsAny(text, "tomorrow", "আগামীকাল", "কালকের") && context.academicClasses.isNotEmpty() ->
                "আগামীকালের classes: ${context.academicClasses.joinToString(" এবং ")}।"
            containsAny(text, "next exam", "পরের পরীক্ষা", "পরবর্তী পরীক্ষা", "exam") && context.upcomingExams.isNotEmpty() ->
                "আপনার next exam হলো ${context.upcomingExams.first()}।"
            containsAny(text, "study plan", "পড়ার পরিকল্পনা", "স্টাডি প্ল্যান") && context.upcomingExams.isNotEmpty() ->
                "${context.upcomingExams.first()} পরীক্ষার জন্য প্রতিদিন 25 মিনিট syllabus review, 25 মিনিট practice এবং 10 মিনিট recall করুন। Exam-এর আগের দিন একটি full revision রাখুন।"
            containsAny(text, "class", "ক্লাস", "routine", "রুটিন", "course") ->
                "আপনার routine-এ ${context.classCount}টি class আছে। Academic tab থেকে semester ও routine পরিচালনা করুন।"
            containsAny(text, "পরামর্শ", "advice", "কী করব", "help", "সাহায্য") ->
                advice(context)
            else ->
                "আমি এখন offline mode-এ আছি। আপনি জিজ্ঞেস করতে পারেন: “আজ কত খরচ?”, “আজ কতক্ষণ পড়েছি?”, “আমার কয়টি task বাকি?” অথবা “আমার কয়টি class আছে?”"
        }

    }

    private fun todayPlan(context: AssistantContext): String {
        if (context.todayPlan.isEmpty()) {
            return "আজকের জন্য আপনার stored schedule-এ কোনো class, routine বা deadline নেই।"
        }
        return "Today's Plan:\n" + context.todayPlan.joinToString("\n") { "• $it" }
    }

    private fun monthlyExpenseSummary(context: AssistantContext): String {
        if (context.monthCategorySpend.isEmpty()) {
            return "এই মাসে আপনার মোট খরচ ৳${money(context.monthExpense)}। এখনো category breakdown নেই।"
        }
        val breakdown = context.monthCategorySpend.entries
            .sortedByDescending { it.value }
            .joinToString("\n") { "• ${it.key}: ৳${money(it.value)}" }
        return "এই মাসে আপনার মোট খরচ ৳${money(context.monthExpense)}।\n\nCategory breakdown:\n$breakdown"
    }

    private fun insight(context: AssistantContext): String {
        val weakerTopic = context.cpTopicsPreviousWeek.entries
            .mapNotNull { (topic, previous) ->
                val current = context.cpTopicsThisWeek[topic] ?: 0
                if (previous > current) topic to (previous - current) else null
            }
            .maxByOrNull { it.second }
        if (weakerTopic != null) {
            return "CP insight: আপনি এই সপ্তাহে ${weakerTopic.first} problems গত সপ্তাহের তুলনায় ${weakerTopic.second}টি কম solve করেছেন।"
        }
        if (context.cpSolvedByPlatform.isNotEmpty()) {
            val progress = context.cpSolvedByPlatform.entries.joinToString(" এবং ") { (platform, solved) ->
                val target = context.cpTargetsByPlatform[platform] ?: 0
                "$platform-এ $solved solved${if (target > 0) " (target $target)" else ""}"
            }
            return "CP insight: $progress।"
        }
        val top = context.categorySpend.entries.sortedByDescending { it.value }.take(3)
        if (top.isEmpty()) return "এই মাসে বিশ্লেষণের জন্য এখনো কোনো expense data নেই।"
        val summary = top.joinToString(" এবং ") { "${it.key}-এ ৳${money(it.value)}" }
        return "এই মাসের AI insight: $summary খরচ হয়েছে। সবচেয়ে বেশি খরচের category হলো ${top.first().key}।"
    }

    private fun advice(context: AssistantContext): String {
        return when {
            context.openTasks > 0 && context.todayStudyMinutes == 0 ->
                "আজ প্রথমে সবচেয়ে জরুরি task বেছে নিন, তারপর 25 মিনিটের একটি focused study session শুরু করুন।"
            context.openTasks > 0 ->
                "আপনার ${context.openTasks}টি task বাকি আছে। একটি জরুরি task complete করে তারপর ছোট বিরতি নিন।"
            context.todayStudyMinutes < 60 ->
                "আজ অন্তত আরও 25 মিনিট পড়ার একটি ছোট session করুন। ছোট consistent session অনেক কাজে দেয়।"
            else -> "আপনি আজ ভালো progress করছেন। এখন পানি পান করে আগামীকালের top priority ঠিক করুন।"
        }
    }

    private fun isTodayPlanRequest(text: String): Boolean {
        val asksForToday = containsAny(text, "আজ", "today", "আজকে")
        val asksForPlan = containsAny(
            text,
            "plan", "schedule", "routine", "do today", "have to do",
            "what do i", "what should i", "কী কর", "কি কর", "করতে হবে",
            "আজকের পরিকল্পনা", "আজকে কী"
        )
        return asksForToday && asksForPlan
    }

    private fun containsAny(text: String, vararg terms: String) = terms.any(text::contains)
    private fun money(value: Double) = "%.0f".format(value)
    private fun duration(minutes: Int) = if (minutes >= 60) "${minutes / 60} ঘণ্টা ${minutes % 60} মিনিট" else "$minutes মিনিট"
}

class OnlineAiAssistant(
    private val baseUrl: String = BuildConfig.ASSISTANT_BASE_URL,
    private val fallback: AiAssistant = LocalAiAssistant()
) {
    suspend fun documentAction(
        action: String,
        title: String,
        text: String,
        provider: AiProvider = AiProvider.GEMINI
    ): String = withContext(Dispatchers.IO) {
        try {
            val connection = (URL("$baseUrl/api/document/action").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 8_000
                readTimeout = 30_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }
            val payload = JSONObject()
                .put("action", action)
                .put("title", title)
                .put("text", text.take(30_000))
                .put("provider", provider.id)
            connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
            val responseCode = connection.responseCode
            val body = (if (responseCode in 200..299) connection.inputStream else connection.errorStream)
                .bufferedReader().use { it.readText() }
            if (responseCode !in 200..299) error("Document AI server returned $responseCode")
            JSONObject(body).getString("result")
        } catch (_: Exception) {
            LocalAiAssistant().documentAction(action, title, text)
        }
    }

    suspend fun reply(
        message: String,
        context: AssistantContext,
        provider: AiProvider = AiProvider.GEMINI
    ): String = withContext(Dispatchers.IO) {
        try {
            val connection = (URL("$baseUrl/api/assistant/chat").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 8_000
                readTimeout = 20_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }
            val payload = JSONObject()
                .put("message", message)
                .put("provider", provider.id)
                .put("context", JSONObject()
                    .put("todayExpense", context.todayExpense)
                    .put("monthExpense", context.monthExpense)
                    .put("todayStudyMinutes", context.todayStudyMinutes)
                    .put("openTasks", context.openTasks)
                    .put("totalTasks", context.totalTasks)
                    .put("classCount", context.classCount)
                    .put("categorySpend", JSONObject(context.categorySpend.mapValues { it.value }))
                    .put("cpSolvedByPlatform", JSONObject(context.cpSolvedByPlatform.mapValues { it.value }))
                    .put("cpTargetsByPlatform", JSONObject(context.cpTargetsByPlatform.mapValues { it.value }))
                    .put("cpTopicsThisWeek", JSONObject(context.cpTopicsThisWeek.mapValues { it.value }))
                    .put("cpTopicsPreviousWeek", JSONObject(context.cpTopicsPreviousWeek.mapValues { it.value }))
                    .put("academicClasses", context.academicClasses)
                    .put("upcomingExams", context.upcomingExams)
                    .put("todayPlan", context.todayPlan)
                    .put("monthCategorySpend", JSONObject(context.monthCategorySpend.mapValues { it.value }))
                )
            connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
            val responseCode = connection.responseCode
            val body = (if (responseCode in 200..299) connection.inputStream else connection.errorStream)
                .bufferedReader().use { it.readText() }
            if (responseCode !in 200..299) error("Assistant server returned $responseCode")
            JSONObject(body).getString("reply")
        } catch (_: Exception) {
            fallback.reply(message, context)
        }
    }
}

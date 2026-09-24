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
    val classCount: Int
)

interface AiAssistant {
    fun reply(message: String, context: AssistantContext): String
}

/**
 * Offline-first assistant. A remote provider can implement the same interface later.
 */
class LocalAiAssistant : AiAssistant {
    override fun reply(message: String, context: AssistantContext): String {
        val text = message.trim().lowercase()
        if (text.isBlank()) return "আপনি কী জানতে চান? খরচ, পড়াশোনা, task বা class সম্পর্কে জিজ্ঞেস করুন।"

        return when {
            containsAny(text, "হ্যালো", "hello", "hi", "হাই") ->
                "হ্যালো! আমি Smart Life Assistant। আপনার খরচ, পড়াশোনা, task এবং class routine নিয়ে সাহায্য করতে পারি।"
            containsAny(text, "আজ খরচ", "today expense", "আজকের expense") ->
                "আজ আপনার মোট খরচ ৳${money(context.todayExpense)}। কোনো expense যোগ করতে Expenses tab থেকে + চাপুন।"
            containsAny(text, "এই মাস", "monthly", "মাসে খরচ", "মাসের খরচ") ->
                "এই মাসে আপনার মোট খরচ ৳${money(context.monthExpense)}।"
            containsAny(text, "পড়", "study", "স্টাডি", "কতক্ষণ পড়েছি") ->
                "আজ আপনি ${duration(context.todayStudyMinutes)} পড়েছেন। ${if (context.todayStudyMinutes == 0) "একটি study session শুরু করলে আমি progress track করব।" else "ভালো ধারাবাহিকতা রাখুন!"}"
            containsAny(text, "task", "কাজ", "todo") ->
                "আপনার ${context.openTasks}টি open task আছে, মোট task ${context.totalTasks}টি। Tasks tab থেকে নতুন task যোগ বা complete করতে পারবেন।"
            containsAny(text, "class", "ক্লাস", "routine", "রুটিন", "course") ->
                "আপনার routine-এ ${context.classCount}টি class আছে। Schedule tab-এর + button দিয়ে এই semester-এর course যোগ করুন।"
            containsAny(text, "পরামর্শ", "advice", "কী করব", "help", "সাহায্য") ->
                advice(context)
            else ->
                "আমি এখন offline mode-এ আছি। আপনি জিজ্ঞেস করতে পারেন: “আজ কত খরচ?”, “আজ কতক্ষণ পড়েছি?”, “আমার কয়টি task বাকি?” অথবা “আমার কয়টি class আছে?”"
        }

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

    private fun containsAny(text: String, vararg terms: String) = terms.any(text::contains)
    private fun money(value: Double) = "%.0f".format(value)
    private fun duration(minutes: Int) = if (minutes >= 60) "${minutes / 60} ঘণ্টা ${minutes % 60} মিনিট" else "$minutes মিনিট"
}

class OnlineAiAssistant(
    private val baseUrl: String = BuildConfig.ASSISTANT_BASE_URL,
    private val fallback: AiAssistant = LocalAiAssistant()
) {
    suspend fun reply(message: String, context: AssistantContext): String = withContext(Dispatchers.IO) {
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
                .put("context", JSONObject()
                    .put("todayExpense", context.todayExpense)
                    .put("monthExpense", context.monthExpense)
                    .put("todayStudyMinutes", context.todayStudyMinutes)
                    .put("openTasks", context.openTasks)
                    .put("totalTasks", context.totalTasks)
                    .put("classCount", context.classCount))
            connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
            val responseCode = connection.responseCode
            val body = (if (responseCode in 200..299) connection.inputStream else connection.errorStream)
                .bufferedReader().use { it.readText() }
            if (responseCode !in 200..299) error("Assistant server returned $responseCode")
            JSONObject(body).getString("reply")
        } catch (_: Exception) {
            "${fallback.reply(message, context)}\n\n(Offline fallback: online assistant is unavailable.)"
        }
    }
}

package com.harsh.jarvis.ai

import android.content.Context
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlin.math.exp
import kotlin.math.ln

/**
 * On-device inference only. Training is performed offline and the compiled
 * Naive-Bayes statistics are shipped as intent_model.json.
 */
class PersonalIntentModel(context: Context? = null) {
    private val labels: List<IntentType>
    private val priors: Map<IntentType, Double>
    private val totals: Map<IntentType, Double>
    private val counts: Map<IntentType, Map<String, Int>>

    init {
        val json = if (context != null) runCatching {
            context.assets.open("intent_model.json").use { JSONObject(BufferedReader(InputStreamReader(it)).readText()) }
        }.getOrNull() else null

        if (json != null) {
            labels = json.getJSONArray("labels").let { a -> (0 until a.length()).mapNotNull { runCatching { IntentType.valueOf(a.getString(it)) }.getOrNull() } }
            val p = json.getJSONObject("priors")
            priors = labels.associateWith { p.optDouble(it.name, 1.0 / labels.size) }
            val t = json.getJSONObject("totals")
            totals = labels.associateWith { t.optDouble(it.name, 1.0) }
            val c = json.getJSONObject("counts")
            counts = labels.associateWith { label ->
                val obj = c.optJSONObject(label.name) ?: JSONObject()
                obj.keys().asSequence().associateWith { obj.optInt(it, 0) }
            }
        } else {
            val fallback = IntentType.values().toList()
            labels = fallback
            priors = fallback.associateWith { 1.0 / fallback.size }
            totals = fallback.associateWith { 1.0 }
            counts = fallback.associateWith { emptyMap() }
        }
    }

    fun classify(text: String): IntentResult {
        val normalized = text.lowercase().trim()
        if (normalized.isBlank()) return IntentResult(IntentType.UNKNOWN, 0.0)
        when {
        // Common paraphrases are normalized here so the same capability is stable across wording.
            Regex("\\b(launch|start|run|open)\\s+(?:up\\s+)?(whatsapp|telegram|instagram|facebook|youtube|spotify|chrome|maps|gmail)\\b").containsMatchIn(normalized) -> return IntentResult(IntentType.OPEN_APP, .99)
            Regex("\\b(what should i work on|what is my next step|what next|what\\s+do\\s+i\\s+focus\\s+on)\\b").containsMatchIn(normalized) -> return IntentResult(IntentType.NEXT_BEST_ACTION, .98)
            Regex("\\b(plan|organize|schedule)\\s+(?:out\\s+)?my\\s+day\\b").containsMatchIn(normalized) -> return IntentResult(IntentType.PLAN_DAY, .98)
            Regex("\\b(what\\s+(?:do i have|is coming up)|show\\s+(?:my\\s+)?upcoming\\s+(?:deadlines|due dates))\\b").containsMatchIn(normalized) -> return IntentResult(IntentType.SHOW_DEADLINES, .98)
            Regex("\\b(yes|yeah|yep|yup|sure|confirm|confirmed|do it|go ahead|okay|ok|proceed|send it|please do)\\b").containsMatchIn(normalized) -> return IntentResult(IntentType.CONFIRM_ACTION,1.0)
            Regex("\\b(no|nope|nah|cancel|stop|dont|don't|do not|never mind)\\b").containsMatchIn(normalized) -> return IntentResult(IntentType.CANCEL_ACTION,1.0)
            Regex("\\b(show|what is|tell me)\\b.*\\b(level|xp|experience|evolution|progress)\\b|\\bmy (level|xp|progress)\\b").containsMatchIn(normalized) -> return IntentResult(IntentType.SHOW_EVOLUTION,.99)
            Regex("\\b(how much xp|how many xp|xp do i have|experience points)\\b").containsMatchIn(normalized) -> return IntentResult(IntentType.SHOW_EVOLUTION_XP,.99)
            Regex("\\b(today'?s|daily|weekly|main|side|custom)\\s+(quest|quests)\\b|\\bwhat is today'?s quest\\b").containsMatchIn(normalized) -> return IntentResult(IntentType.SHOW_EVOLUTION_QUESTS,.99)
            Regex("\\b(complete|finish|mark)\\b.*\\b(quest|challenge)\\b").containsMatchIn(normalized) -> return IntentResult(IntentType.COMPLETE_EVOLUTION_QUEST,.99)
            Regex("\\b(weakest|neglected|weak)\\s+(skill|skills)\\b").containsMatchIn(normalized) -> return IntentResult(IntentType.SHOW_EVOLUTION_WEAKNESS,.99)
            Regex("\\b(next|recommended|recommend)\\b.*\\b(challenge|quest)\\b").containsMatchIn(normalized) -> return IntentResult(IntentType.NEXT_EVOLUTION_CHALLENGE,.99)
            Regex("\\b(start|begin)\\b.*\\b(python|dsa|math|ml|challenge|quest|training)\\b").containsMatchIn(normalized) -> return IntentResult(IntentType.START_EVOLUTION_CHALLENGE,.98)
            Regex("\\b(remind|reminder|don't let me forget|dont let me forget|set a task|create a task)\\b").containsMatchIn(normalized) -> return IntentResult(IntentType.CREATE_REMINDER,.99)
            Regex("\\b(open|launch|start|run)\\b").containsMatchIn(normalized) && !Regex("\\b(open|launch|start|run)\\s+(my\\s+)?(memory|memories|tasks|reminders|help)\\b").containsMatchIn(normalized) -> return IntentResult(IntentType.OPEN_APP,.98)
            Regex("\\b(remember|save this|store this|keep this in memory)\\b").containsMatchIn(normalized) && !Regex("\\bwhat did\\b|\\bwhat do you remember\\b|\\brecall\\b").containsMatchIn(normalized) -> return IntentResult(IntentType.SAVE_MEMORY,.99)
            Regex("\\b(what do you remember|what did you remember|what did i tell you|recall|search my memory|show my memories)\\b").containsMatchIn(normalized) -> return IntentResult(IntentType.SEARCH_MEMORY,.98)
            Regex("\\b(show|list|display)\\b.*\\b(tasks|reminders|to[- ]dos)\\b|\\bwhat do i have to do\\b").containsMatchIn(normalized) -> return IntentResult(IntentType.SHOW_TASKS,.98)
            Regex("\\b(delete|remove)\\b.*\\b(task|reminder|todo|to-do)\\b").containsMatchIn(normalized) -> return IntentResult(IntentType.DELETE_TASK,.98)
            Regex("\\b(what happened|what did you do|show my action history|show recent actions|recent actions|action history|status of my last task)\\b").containsMatchIn(normalized) -> return IntentResult(IntentType.SHOW_HISTORY,.98)
            Regex("\\b(retry|try again|repeat the last action|do that again)\\b").containsMatchIn(normalized) -> return IntentResult(IntentType.RETRY_ACTION,.98)
            Regex("\\b(help|what can you do|commands|capabilities)\\b").containsMatchIn(normalized) -> return IntentResult(IntentType.HELP,.98)
            Regex("\\b(calendar|schedule|appointments?|events?)\\b").containsMatchIn(normalized) -> return IntentResult(IntentType.CALENDAR_QUERY,.97)
            Regex("\\b(call|dial)\\b").containsMatchIn(normalized) -> return IntentResult(IntentType.CONTACT_LOOKUP,.97)
            Regex("\\b(contact|phone number|number of)\\b").containsMatchIn(normalized) -> return IntentResult(IntentType.CONTACT_LOOKUP,.95)
            Regex("\\b(create|make|save)\\s+(?:a\\s+)?(?:routine|automation)\\b|\\b(run|start)\\s+(?:my\\s+)?routine\\b").containsMatchIn(normalized) -> return IntentResult(IntentType.CREATE_ROUTINE,.96)
            Regex("\\b(who am i|my profile|my settings|what is my name)\\b").containsMatchIn(normalized) -> return IntentResult(IntentType.SHOW_PROFILE,.98)
            Regex("\\b(my name is|call me)\\b").containsMatchIn(normalized) -> return IntentResult(IntentType.SET_PROFILE,.98)
            Regex("\\b(hi|hello|hey|good morning|good evening|thanks|thank you)\\b").containsMatchIn(normalized) -> return IntentResult(IntentType.CONVERSATION,.95)
            normalized.contains("what should i do now") || normalized.contains("next best action") || normalized.contains("what do i do now") || normalized.contains("how should i use my free time") -> return IntentResult(IntentType.NEXT_BEST_ACTION,.99)
            normalized.contains("plan my day") -> return IntentResult(IntentType.PLAN_DAY,.99)
            normalized.contains("what's important today") || normalized.contains("what is important today") || normalized.contains("morning briefing") -> return IntentResult(IntentType.TODAY_BRIEFING,.99)
            normalized.contains("deadlines") || normalized.contains("deadline") -> return IntentResult(IntentType.SHOW_DEADLINES,.98)
            normalized.contains("what am i missing") || normalized.contains("missing") -> return IntentResult(IntentType.SHOW_MISSING,.98)
            normalized.contains("did not finish yesterday") || normalized.contains("didn't finish yesterday") || normalized.contains("unfinished yesterday") -> return IntentResult(IntentType.SHOW_YESTERDAY_INCOMPLETE,.98)
            normalized.contains("don't disturb") || normalized.contains("do not disturb") || normalized.contains("protect me") -> return IntentResult(IntentType.SET_AVAILABILITY,.99)
            normalized.contains("remind me when i'm free") || normalized.contains("remind me when im free") -> return IntentResult(IntentType.REMIND_WHEN_FREE,.99)
            normalized.contains("snooze this") || normalized == "snooze" -> return IntentResult(IntentType.SNOOZE_PROACTIVE,.99)
            normalized.contains("mark it complete") || normalized.contains("complete this") -> return IntentResult(IntentType.COMPLETE_PROACTIVE,.99)
            normalized.contains("break down") || normalized.contains("breakdown") || normalized.contains("split this task") -> return IntentResult(IntentType.BREAK_DOWN_TASK,.98)
            normalized.contains("evening review") || normalized.contains("what did i complete today") -> return IntentResult(IntentType.EVENING_REVIEW,.98)
        }
        val tokens=tokenize(normalized)
        if(tokens.isEmpty()) return IntentResult(IntentType.UNKNOWN,0.0)
        val scores=labels.associateWith { label ->
            var score=ln(priors.getValue(label)); val denom=totals.getValue(label)
            for(word in tokens) score += ln(((counts[label]?.get(word) ?: 0)+1.0)/denom)
            score
        }
        val sorted=scores.entries.sortedByDescending{it.value}
        val best=sorted.first(); val second=sorted.getOrNull(1)?.value ?: best.value-1
        val confidence=(1.0/(1.0+exp(-(best.value-second)))).coerceIn(0.0,1.0)
        return IntentResult(if(confidence<.58) IntentType.UNKNOWN else best.key,confidence)
    }

    private fun tokenize(text:String)=text.lowercase().replace(Regex("[^a-z0-9 ]")," ").split(Regex("\\s+")).filter{it.length>1}
}

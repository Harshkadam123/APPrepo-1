package com.harsh.jarvis.ai

import android.content.Context

/** On-demand model holder. Closing drops the reference so GC can reclaim memory. */
class ModelSession(private val context: Context) : AutoCloseable {
    private var intentModel: PersonalIntentModel? = null

    @Synchronized
    fun intent(): PersonalIntentModel = intentModel ?: PersonalIntentModel(context).also { intentModel = it }

    @Synchronized
    override fun close() {
        intentModel = null
    }
}

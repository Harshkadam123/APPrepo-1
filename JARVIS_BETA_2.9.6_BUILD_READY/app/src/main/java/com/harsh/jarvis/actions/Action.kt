package com.harsh.jarvis.actions

import com.harsh.jarvis.security.ActionLevel

data class JarvisAction(
    val name: String,
    val description: String,
    val level: ActionLevel,
    val execute: suspend () -> ActionResult,
    val allowed: (() -> Boolean)? = null,
    /** Stable structured parameters used for history/retry; never reconstruct actions from English. */
    val payload: Map<String, String> = emptyMap()
)

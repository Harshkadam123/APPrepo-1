package com.harsh.jarvis.security

import com.harsh.jarvis.actions.JarvisAction

/** Keeps exactly one pending confirmation; a new action replaces an old unanswered request. */
class ConfirmationManager {
    private var pendingAction: JarvisAction? = null

    @Synchronized
    fun request(action: JarvisAction) { pendingAction = action }

    @Synchronized
    fun takePending(): JarvisAction? = pendingAction.also { pendingAction = null }

    @Synchronized
    fun cancel() { pendingAction = null }

    @Synchronized
    fun hasPendingAction(): Boolean = pendingAction != null

    @Synchronized
    fun pendingDescription(): String? = pendingAction?.description
}

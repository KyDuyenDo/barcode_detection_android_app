package com.example.barcode_detection

import android.util.Log

/**
 * State machine controller with strict transition rules and locking mechanism. Ensures
 * single-decode guarantee and prevents double counting.
 */
class StateController(private val config: SystemConfig) {

    companion object {
        private const val TAG = "StateController"
    }

    private var currentState: AppState = AppState.IDLE
    private var stateChangeCallback: ((AppState, AppState) -> Unit)? = null

    /** Get current state */
    fun getState(): AppState = currentState

    /** Set callback for state changes */
    fun setStateChangeCallback(callback: (oldState: AppState, newState: AppState) -> Unit) {
        stateChangeCallback = callback
    }

    /**
     * Attempt to transition to a new state. Returns true if transition was allowed, false
     * otherwise.
     */
    fun transitionTo(newState: AppState, reason: String = ""): Boolean {
        if (!isTransitionAllowed(currentState, newState)) {
            Log.w(TAG, "Transition blocked: $currentState -> $newState (reason: $reason)")
            return false
        }

        val oldState = currentState
        currentState = newState

        Log.i(TAG, "State transition: $oldState -> $newState (reason: $reason)")
        stateChangeCallback?.invoke(oldState, newState)

        return true
    }

    /** Check if a state transition is allowed based on state machine rules. */
    private fun isTransitionAllowed(from: AppState, to: AppState): Boolean {
        return when (from) {
            AppState.IDLE -> {
                to == AppState.DETECTING
            }
            AppState.DETECTING -> {
                to == AppState.READY_TO_DECODE || to == AppState.IDLE
            }
            AppState.READY_TO_DECODE -> {
                to == AppState.DECODED || to == AppState.DETECTING
            }
            AppState.DECODED -> {
                // CRITICAL: Once decoded, can ONLY go to RESETTING
                // This is the anti-double-count guarantee
                to == AppState.RESETTING
            }
            AppState.RESETTING -> {
                to == AppState.IDLE
            }
        }
    }

    /** Check if detection should run in current state */
    fun shouldRunDetection(): Boolean {
        return currentState == AppState.DETECTING
    }

    /** Check if barcode decoding should run in current state */
    fun shouldRunDecoding(): Boolean {
        return currentState == AppState.READY_TO_DECODE
    }

    /** Check if state is locked (DECODED state prevents all processing) */
    fun isLocked(): Boolean {
        return currentState == AppState.DECODED
    }

    /** Reset to IDLE state (used during initialization or error recovery) */
    fun reset() {
        Log.i(TAG, "State controller reset to IDLE")
        currentState = AppState.IDLE
    }
}

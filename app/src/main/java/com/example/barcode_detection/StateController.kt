package com.example.barcode_detection

import android.util.Log

class StateController {

    private var state: AppState = AppState.IDLE

    fun getState(): AppState = state

    fun transitionTo(newState: AppState, reason: String = "") {
        if (state == newState) return
        Log.i("STATE", "$state → $newState ($reason)")
        state = newState
    }
}

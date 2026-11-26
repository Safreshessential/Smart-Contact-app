package com.safroshessential.smartcontact

import android.content.Intent
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import android.util.Log

class SmartInCallService : InCallService() {

    companion object {
        private const val TAG = "SmartInCallService"

        @Volatile
        var activeCall: Call? = null
            private set

        @Volatile
        var serviceInstance: SmartInCallService? = null
            private set

        fun answerCall() {
            activeCall?.answer(
                android.telecom.VideoProfile.STATE_AUDIO_ONLY
            )
        }

        fun rejectCall() {
            activeCall?.reject(false, null)
        }

        fun endCall() {
            activeCall?.disconnect()
        }

        fun muteCall(muted: Boolean) {
            serviceInstance?.setMuted(muted)
        }

        fun setSpeaker(enabled: Boolean) {
            serviceInstance?.let { service ->
                val route =
                    if (enabled) {
                        CallAudioState.ROUTE_SPEAKER
                    } else {
                        CallAudioState.ROUTE_EARPIECE
                    }

                service.setAudioRoute(route)
            }
        }

        fun isCallActive(): Boolean {
            return activeCall?.state == Call.STATE_ACTIVE
        }

        fun getCallNumber(): String {
            return activeCall?.details?.handle
                ?.schemeSpecificPart ?: ""
        }
    }

    private val callCallback = object : Call.Callback() {

        override fun onStateChanged(
            call: Call,
            state: Int
        ) {
            super.onStateChanged(call, state)

            Log.d(TAG, "Call state changed: $state")

            if (state == Call.STATE_DISCONNECTED) {
                if (activeCall == call) {
                    activeCall = null
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        serviceInstance = this
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)

        activeCall = call
        call.registerCallback(callCallback)

        Log.d(TAG, "Call added")

        openCallingScreen(call)
    }

    override fun onCallRemoved(call: Call) {
        call.unregisterCallback(callCallback)

        if (activeCall == call) {
            activeCall = null
        }

        Log.d(TAG, "Call removed")

        super.onCallRemoved(call)
    }

    private fun openCallingScreen(call: Call) {
        try {
            val phoneNumber =
                call.details.handle?.schemeSpecificPart ?: ""

            val intent = Intent(
                this,
                SmartCallActivity::class.java
            ).apply {
                flags =
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP

                putExtra("phone_number", phoneNumber)
                putExtra("contact_name", "Phone Call")
            }

            startActivity(intent)

        } catch (e: Exception) {
            Log.e(TAG, "Unable to open calling screen", e)
        }
    }

    override fun onDestroy() {
        if (serviceInstance === this) {
            serviceInstance = null
        }

        super.onDestroy()
    }
}
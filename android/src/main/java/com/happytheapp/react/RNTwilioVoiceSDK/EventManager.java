package com.happytheapp.react.RNTwilioVoiceSDK;

import android.support.annotation.Nullable;
import android.util.Log;

import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;

import static com.happytheapp.react.RNTwilioVoiceSDK.TwilioVoiceSDKModule.TAG;

public class EventManager {

    private ReactApplicationContext mContext;

    public static final String EVENT_CONNECTED = "connect";
    public static final String EVENT_CONNECT_FAILURE = "connectFailure";
    public static final String EVENT_RECONNECTING = "reconnecting";
    public static final String EVENT_RECONNECTED = "reconnect";
    public static final String EVENT_DISCONNECTED = "disconnect";
    public static final String EVENT_RINGING = "ringing";

    public EventManager(ReactApplicationContext context) {
        mContext = context;
    }

    public void sendEvent(String eventName, @Nullable WritableMap params) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "sendEvent " + eventName + " params " + params);
        }
        if (mContext.hasActiveCatalystInstance()) {
            mContext.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class).emit(eventName, params);
        } else {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "failed Catalyst instance not active");
            }
        }
    }
}

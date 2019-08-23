package com.happytheapp.react.RNTwilioVoiceSDK;

import android.media.AudioManager;

import android.util.Log;

import android.support.annotation.NonNull;

import com.facebook.react.bridge.LifecycleEventListener;
import com.facebook.react.bridge.ReadableMap;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ReadableMapKeySetIterator;
import com.facebook.react.bridge.ReadableType;
import com.facebook.react.bridge.WritableMap;

import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;

import com.twilio.voice.Call;
import com.twilio.voice.CallException;
import com.twilio.voice.ConnectOptions;
import com.twilio.voice.LogLevel;
import com.twilio.voice.Voice;

import java.util.HashMap;

import static com.happytheapp.react.RNTwilioVoiceSDK.EventManager.EVENT_RINGING;
import static com.happytheapp.react.RNTwilioVoiceSDK.EventManager.EVENT_CONNECTED;
import static com.happytheapp.react.RNTwilioVoiceSDK.EventManager.EVENT_CONNECT_FAILURE;
import static com.happytheapp.react.RNTwilioVoiceSDK.EventManager.EVENT_RECONNECTING;
import static com.happytheapp.react.RNTwilioVoiceSDK.EventManager.EVENT_RECONNECTED;
import static com.happytheapp.react.RNTwilioVoiceSDK.EventManager.EVENT_DISCONNECTED;

public class TwilioVoiceSDKModule extends ReactContextBaseJavaModule implements LifecycleEventListener {

    public static String TAG = "RNTwilioVoiceSDK";

    private Call.Listener callListener = callListener();
    private Call activeCall;
    private AudioFocusManager audioFocusManager;
    private EventManager eventManager;

    public TwilioVoiceSDKModule(ReactApplicationContext reactContext) {
        super(reactContext);
        if (BuildConfig.DEBUG) {
            Voice.setLogLevel(LogLevel.DEBUG);
        } else {
            Voice.setLogLevel(LogLevel.ERROR);
        }
        reactContext.addLifecycleEventListener(this);

        eventManager = new EventManager(reactContext);
        audioFocusManager = new AudioFocusManager(reactContext);

    }

    // region Lifecycle Event Listener
    @Override
    public void onHostResume() {
        /*
         * Enable changing the volume using the up/down keys during a conversation
         */
        if (getCurrentActivity() != null) {
            getCurrentActivity().setVolumeControlStream(AudioManager.STREAM_VOICE_CALL);
        }
    }

    @Override
    public void onHostPause() {
        // the library needs to listen for events even when the app is paused
        // unregisterReceiver();
    }

    @Override
    public void onHostDestroy() {
        disconnect();
        audioFocusManager.unsetAudioFocus();
    }
    // endregion

    @Override
    public String getName() {
        return TAG;
    }

    private Call.Listener callListener() {
        return new Call.Listener() {
            @Override
            public void onConnected(@NonNull Call call) {
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "CALL CONNECTED callListener().onConnected call state = " + call.getState());
                }
                activeCall = call;
                eventManager.sendEvent(EVENT_CONNECTED, paramsFromCall(call));
            }

            @Override
            public void onReconnecting(@NonNull Call call, @NonNull CallException error) {
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "reconnecting");
                }
                activeCall = call;
                eventManager.sendEvent(EVENT_RECONNECTING, paramsWithError(call, error));
            }

            @Override
            public void onReconnected(@NonNull Call call) {
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "reconnected");
                }
                activeCall = call;
                eventManager.sendEvent(EVENT_RECONNECTED, paramsFromCall(call));
            }

            @Override
            public void onDisconnected(@NonNull Call call, CallException error) {
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "call disconnected");
                }
                activeCall = call;
                audioFocusManager.unsetAudioFocus();
                eventManager.sendEvent(EVENT_DISCONNECTED, paramsWithError(call, error));
                call.disconnect();
                activeCall = null;
            }

            @Override
            public void onConnectFailure(@NonNull Call call, @NonNull CallException error) {
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "connect failure");
                }
                activeCall = call;
                audioFocusManager.unsetAudioFocus();
                WritableMap params = paramsWithError(call, error);
                call.disconnect();
                activeCall = null;
                eventManager.sendEvent(EVENT_CONNECT_FAILURE, params);
            }

            @Override
            public void onRinging(@NonNull Call call) {
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "ringing");
                }
                activeCall = call;
                audioFocusManager.setAudioFocus();
                eventManager.sendEvent(EVENT_RINGING, paramsFromCall(call));
            }
        };
    }

    @ReactMethod
    public void connect(final String accessToken, ReadableMap params, Promise promise) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "connect params: " + params);
        }

        if (activeCall != null) {
            promise.reject("already_connected", "Calling connect while a call is connected");
        }

        // create parameters for call
        HashMap<String, String> twiMLParams = new HashMap<>();
        ReadableMapKeySetIterator iterator = params.keySetIterator();
        while (iterator.hasNextKey()) {
            String key = iterator.nextKey();
            ReadableType readableType = params.getType(key);
            switch (readableType) {
            case Null:
                twiMLParams.put(key, "");
                break;
            case Boolean:
                twiMLParams.put(key, String.valueOf(params.getBoolean(key)));
                break;
            case Number:
                // Can be int or double.
                twiMLParams.put(key, String.valueOf(params.getDouble(key)));
                break;
            case String:
                twiMLParams.put(key, params.getString(key));
                break;
            default:
                Log.d(TAG, "Could not convert with key: " + key + ".");
                break;
            }
        }

        ConnectOptions connectOptions = new ConnectOptions.Builder(accessToken).params(twiMLParams)
                // .iceOptions()
                // .preferAudioCodecs()
                // .enableInsights()
                // .region()
                .build();
        activeCall = Voice.connect(getReactApplicationContext(), connectOptions, callListener);
        promise.resolve(paramsFromCall(activeCall));
    }

    @ReactMethod
    public void disconnect() {
        if (activeCall != null) {
            activeCall.disconnect();
            activeCall = null;
        }
    }

    @ReactMethod
    public void setMuted(Boolean muteValue) {
        if (activeCall != null) {
            activeCall.mute(muteValue);
        }
    }

    @ReactMethod
    public void sendDigits(String digits) {
        if (activeCall != null) {
            activeCall.sendDigits(digits);
        }
    }

    @ReactMethod
    public void getVersion(Promise promise) {
        promise.resolve(Voice.getVersion());
    }

    @ReactMethod
    public void getActiveCall(Promise promise) {
        if (activeCall != null) {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "Active call found state = " + activeCall.getState());
            }
            promise.resolve(paramsFromCall(activeCall));
            return;
        }
        promise.reject("no_call", "There was no active call");
    }

    @ReactMethod
    public void setSpeakerPhone(Boolean value) {
        audioFocusManager.setSpeakerPhone(value);
    }

    // region create JSObjects helpers
    private WritableMap paramsFromCall(Call call) {
        WritableMap params = Arguments.createMap();
        if (call != null) {
            if (call.getSid() != null) {
                params.putString("sid", call.getSid());
            }
            if (call.getFrom() != null) {
                params.putString("from", activeCall.getFrom());
            }
            if (call.getTo() != null) {
                params.putString("to", activeCall.getTo());
            }
            params.putString("state", call.getState().name());
        }
        return params;
    }

    private WritableMap paramsWithError(Call call, CallException error) {
        WritableMap params = paramsFromCall(call);
        if (error != null) {
            Log.e(TAG, String.format("CallListener onDisconnected error: %d, %s", error.getErrorCode(),
                    error.getMessage()));
            WritableMap errorParams = Arguments.createMap();
            errorParams.putInt("code", error.getErrorCode());
            errorParams.putString("message", error.getLocalizedMessage());
            params.putMap("error", errorParams);
        }
        return params;
    }
    // endregion
}

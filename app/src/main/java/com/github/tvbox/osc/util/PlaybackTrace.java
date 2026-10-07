package com.github.tvbox.osc.util;

import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;

import com.github.tvbox.osc.BuildConfig;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;

public final class PlaybackTrace {
    private static final String TAG = "PlaybackTrace";
    private static final AtomicLong NEXT_ID = new AtomicLong(1);

    private PlaybackTrace() {
    }

    public static String begin(String sourceKey, String playFlag, String url) {
        String sessionId = String.format(Locale.US, "%d-%d", SystemClock.elapsedRealtime(), NEXT_ID.getAndIncrement());
        if (BuildConfig.PLAYBACK_TRACE_ENABLED) {
            event(sessionId, "PLAY_INIT", "source=" + safe(sourceKey) + " flag=" + safe(playFlag) + " url=" + type(url));
        }
        return sessionId;
    }

    public static void event(String sessionId, String event) {
        event(sessionId, event, "");
    }

    public static void event(String sessionId, String event, String detail) {
        if (!BuildConfig.PLAYBACK_TRACE_ENABLED) return;
        Log.i(TAG, "session=" + safeSession(sessionId) + " event=" + event + (TextUtils.isEmpty(detail) ? "" : " " + detail));
    }

    public static void error(String sessionId, String event, Throwable error) {
        if (!BuildConfig.PLAYBACK_TRACE_ENABLED) return;
        String message = error == null ? "" : error.getClass().getSimpleName() + ":" + safe(error.getMessage());
        Log.e(TAG, "session=" + safeSession(sessionId) + " event=" + event + " error=" + message, error);
    }

    public static String type(String url) {
        if (TextUtils.isEmpty(url)) return "empty";
        if (url.startsWith("http://127.0.0.1") || url.startsWith("http://localhost")) return "local-proxy";
        String lower = url.toLowerCase(Locale.US);
        if (lower.contains(".m3u8") || lower.contains("type=m3u8") || lower.contains("format=m3u8")) return "m3u8";
        if (lower.contains(".mpd") || lower.contains("type=mpd")) return "mpd";
        if (lower.startsWith("data:")) return "data";
        if (lower.contains("/share/")) return "share";
        if (lower.startsWith("http")) return "http";
        return "other";
    }

    private static String safeSession(String sessionId) {
        return TextUtils.isEmpty(sessionId) ? "none" : sessionId;
    }

    private static String safe(String value) {
        return value == null ? "null" : value.replace('\n', ' ').replace('\r', ' ');
    }
}

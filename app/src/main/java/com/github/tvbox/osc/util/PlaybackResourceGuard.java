package com.github.tvbox.osc.util;

import com.bumptech.glide.Glide;
import com.github.tvbox.osc.base.App;

public final class PlaybackResourceGuard {
    private static boolean imagesPaused;

    private PlaybackResourceGuard() {
    }

    public static synchronized void enterPlayback(String sessionId) {
        OkGoHelper.tuneForPlayback(true);
        if (imagesPaused) return;
        try {
            Glide.with(App.getInstance()).pauseRequestsRecursive();
            imagesPaused = true;
            PlaybackTrace.event(sessionId, "RESOURCE_GUARD", "images=paused");
        } catch (Throwable th) {
            PlaybackTrace.error(sessionId, "RESOURCE_GUARD_PAUSE", th);
        }
    }

    public static synchronized void onFirstFrame(String sessionId) {
        resumeImages(sessionId, "first-frame");
    }

    public static synchronized void leavePlayback(String sessionId, String reason) {
        resumeImages(sessionId, reason);
    }

    private static void resumeImages(String sessionId, String reason) {
        OkGoHelper.tuneForPlayback(false);
        if (!imagesPaused) return;
        try {
            Glide.with(App.getInstance()).resumeRequestsRecursive();
            imagesPaused = false;
            PlaybackTrace.event(sessionId, "RESOURCE_GUARD", "images=resumed reason=" + reason);
        } catch (Throwable th) {
            PlaybackTrace.error(sessionId, "RESOURCE_GUARD_RESUME", th);
        }
    }
}

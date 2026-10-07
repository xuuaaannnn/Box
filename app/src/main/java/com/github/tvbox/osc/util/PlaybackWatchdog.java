package com.github.tvbox.osc.util;

import android.os.Handler;
import android.os.SystemClock;

import com.github.tvbox.osc.player.MyVideoView;
import com.github.tvbox.osc.event.PlaybackFirstFrameEvent;

import org.greenrobot.eventbus.EventBus;

import xyz.doikki.videoplayer.player.VideoView;

public final class PlaybackWatchdog {
    private static final long FIRST_FRAME_TIMEOUT_MS = 15000;
    private static final long STALL_TIMEOUT_MS = 25000;
    private static final long CHECK_INTERVAL_MS = 5000;
    private static final long MIN_PROGRESS_MS = 1000;

    private final Handler handler;
    private final PlayerProvider playerProvider;
    private final Callback callback;

    private String sessionId;
    private boolean active;
    private boolean paused;
    private boolean recovered;
    private boolean firstFrameSeen;
    private long lastPosition;
    private long lastProgressAt;

    private final Runnable firstFrameCheck = new Runnable() {
        @Override
        public void run() {
            checkFirstFrame();
        }
    };

    private final Runnable stallCheck = new Runnable() {
        @Override
        public void run() {
            checkStall();
        }
    };

    public PlaybackWatchdog(Handler handler, PlayerProvider playerProvider, Callback callback) {
        this.handler = handler;
        this.playerProvider = playerProvider;
        this.callback = callback;
    }

    public void start(String sessionId, String url) {
        boolean sameSession = sessionId != null && sessionId.equals(this.sessionId);
        stop("restart");
        this.sessionId = sessionId;
        active = true;
        paused = false;
        if (!sameSession) {
            recovered = false;
        }
        firstFrameSeen = false;
        lastPosition = readPosition();
        lastProgressAt = SystemClock.elapsedRealtime();

        PlaybackTrace.event(sessionId, "WATCHDOG_START", "url=" + PlaybackTrace.type(url));
        handler.postDelayed(firstFrameCheck, FIRST_FRAME_TIMEOUT_MS);
        handler.postDelayed(stallCheck, CHECK_INTERVAL_MS);
    }

    public void stop(String reason) {
        handler.removeCallbacks(firstFrameCheck);
        handler.removeCallbacks(stallCheck);
        if (active) {
            PlaybackTrace.event(sessionId, "WATCHDOG_STOP", "reason=" + reason);
        }
        active = false;
        paused = false;
    }

    public void pause() {
        if (!active) return;
        paused = true;
        handler.removeCallbacks(firstFrameCheck);
        handler.removeCallbacks(stallCheck);
    }

    public void resume() {
        if (!active) return;
        paused = false;
        lastProgressAt = SystemClock.elapsedRealtime();
        lastPosition = readPosition();
        if (!firstFrameSeen) {
            handler.postDelayed(firstFrameCheck, FIRST_FRAME_TIMEOUT_MS);
        }
        handler.postDelayed(stallCheck, CHECK_INTERVAL_MS);
    }

    public void onPlayStateChanged(int playState) {
        if (!active) return;
        if (playState == VideoView.STATE_PLAYING) {
            markFirstFrame();
            touchProgress();
            return;
        }
        if (playState == VideoView.STATE_BUFFERED) {
            touchProgress();
            return;
        }
        if (playState == VideoView.STATE_ERROR
                || playState == VideoView.STATE_IDLE
                || playState == VideoView.STATE_PLAYBACK_COMPLETED) {
            stop("state-" + playState);
        }
    }

    private void checkFirstFrame() {
        if (!active || paused || firstFrameSeen) return;
        MyVideoView player = playerProvider.get();
        if (player == null) return;

        int state = player.getCurrentPlayState();
        if (state == VideoView.STATE_PLAYING || player.getCurrentPosition() > 0) {
            markFirstFrame();
            return;
        }
        if (state == VideoView.STATE_ERROR
                || state == VideoView.STATE_IDLE
                || state == VideoView.STATE_PLAYBACK_COMPLETED) {
            stop("state-" + state);
            return;
        }

        recover("first-frame-timeout", state, player.getTcpSpeed());
    }

    private void checkStall() {
        if (!active || paused) return;
        MyVideoView player = playerProvider.get();
        if (player == null) return;

        int state = player.getCurrentPlayState();
        long position = player.getCurrentPosition();
        if (position > lastPosition + MIN_PROGRESS_MS) {
            touchProgress(position);
        }

        boolean waitingForData = state == VideoView.STATE_BUFFERING || player.getTcpSpeed() > 0;
        long stalledFor = SystemClock.elapsedRealtime() - lastProgressAt;
        if (firstFrameSeen && waitingForData && stalledFor >= STALL_TIMEOUT_MS) {
            recover("position-stalled", state, player.getTcpSpeed());
            return;
        }

        handler.postDelayed(stallCheck, CHECK_INTERVAL_MS);
    }

    private void recover(String reason, int state, long tcpSpeed) {
        if (recovered) {
            PlaybackTrace.event(sessionId, "WATCHDOG_SKIP", "reason=" + reason + " state=" + state);
            stop("already-recovered");
            return;
        }
        recovered = true;
        active = false;
        handler.removeCallbacks(firstFrameCheck);
        handler.removeCallbacks(stallCheck);

        long position = readPosition();
        PlaybackTrace.event(sessionId, "WATCHDOG_RECOVER", "reason=" + reason + " state=" + state + " position=" + position + " speed=" + tcpSpeed);
        callback.recover(reason, position, state, tcpSpeed);
    }

    private void markFirstFrame() {
        if (firstFrameSeen) return;
        firstFrameSeen = true;
        handler.removeCallbacks(firstFrameCheck);
        PlaybackTrace.event(sessionId, "PLAYER_FIRST_FRAME");
        PlaybackResourceGuard.onFirstFrame(sessionId);
        EventBus.getDefault().post(new PlaybackFirstFrameEvent(sessionId));
    }

    private void touchProgress() {
        touchProgress(readPosition());
    }

    private void touchProgress(long position) {
        lastPosition = Math.max(position, 0);
        lastProgressAt = SystemClock.elapsedRealtime();
    }

    private long readPosition() {
        MyVideoView player = playerProvider.get();
        return player == null ? 0 : Math.max(player.getCurrentPosition(), 0);
    }

    public interface PlayerProvider {
        MyVideoView get();
    }

    public interface Callback {
        void recover(String reason, long position, int playState, long tcpSpeed);
    }
}

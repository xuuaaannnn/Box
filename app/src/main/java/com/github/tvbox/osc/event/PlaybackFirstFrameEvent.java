package com.github.tvbox.osc.event;

public class PlaybackFirstFrameEvent {
    public final String sessionId;

    public PlaybackFirstFrameEvent(String sessionId) {
        this.sessionId = sessionId;
    }
}

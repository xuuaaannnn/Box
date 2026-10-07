package master.flame.danmaku.controller;

import master.flame.danmaku.danmaku.model.BaseDanmaku;
import master.flame.danmaku.danmaku.model.DanmakuTimer;

public class DrawHandler {
    public interface Callback {
        void prepared();
        void updateTimer(DanmakuTimer timer);
        void danmakuShown(BaseDanmaku danmaku);
        void drawingFinished();
    }
}

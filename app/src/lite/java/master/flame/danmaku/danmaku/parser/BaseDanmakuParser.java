package master.flame.danmaku.danmaku.parser;

import master.flame.danmaku.danmaku.model.DanmakuTimer;
import master.flame.danmaku.danmaku.model.IDisplayer;
import master.flame.danmaku.danmaku.model.android.DanmakuContext;
import master.flame.danmaku.danmaku.model.android.Danmakus;

public abstract class BaseDanmakuParser {
    protected int mDispWidth = 1;
    protected int mDispHeight = 1;
    protected float mDispDensity = 1f;
    protected DanmakuContext mContext = DanmakuContext.create();
    protected DanmakuTimer mTimer = new DanmakuTimer();

    protected abstract Danmakus parse();

    public BaseDanmakuParser setDisplayer(IDisplayer display) {
        return this;
    }
}

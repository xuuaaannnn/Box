package master.flame.danmaku.danmaku.model.android;

import java.util.HashMap;
import java.util.Map;

import master.flame.danmaku.danmaku.model.IDisplayer;

public class DanmakuContext {
    public final DanmakuFactory mDanmakuFactory = new DanmakuFactory();
    public final Object mGlobalFlagValues = new Object();

    public static DanmakuContext create() {
        return new DanmakuContext();
    }

    public DanmakuContext setMaximumLines(HashMap<Integer, Integer> maxLines) { return this; }
    public DanmakuContext setMaximumLines(Map<Integer, Integer> maxLines) { return this; }
    public DanmakuContext setScrollSpeedFactor(float factor) { return this; }
    public DanmakuContext setDanmakuTransparency(float alpha) { return this; }
    public DanmakuContext setScaleTextSize(float scale) { return this; }
    public DanmakuContext setDanmakuStyle(int style, float strokeWidth) { return this; }
    public DanmakuContext setDanmakuMargin(int margin) { return this; }
}

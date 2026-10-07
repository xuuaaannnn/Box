package master.flame.danmaku.ui.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;

import master.flame.danmaku.controller.DrawHandler;
import master.flame.danmaku.danmaku.model.android.DanmakuContext;
import master.flame.danmaku.danmaku.parser.BaseDanmakuParser;

public class DanmakuView extends View {
    private boolean prepared;
    private DrawHandler.Callback callback;

    public DanmakuView(Context context) {
        super(context);
    }

    public DanmakuView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public DanmakuView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void setCallback(DrawHandler.Callback callback) {
        this.callback = callback;
    }

    public void prepare(BaseDanmakuParser parser, DanmakuContext context) {
        prepared = false;
    }

    public boolean isPrepared() {
        return prepared;
    }

    public void start(long position) {}
    public void seekTo(long position) {}
    public void resume() {}
    public void pause() {}
    public void stop() { prepared = false; }
    public void release() { prepared = false; }
    public void show() { setVisibility(VISIBLE); }
    public void hide() { setVisibility(GONE); }
}

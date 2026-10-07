package master.flame.danmaku.danmaku.model;

public class BaseDanmaku {
    public static final int TYPE_SPECIAL = 7;
    public static final int TYPE_FIX_TOP = 5;
    public static final int TYPE_SCROLL_RL = 1;
    public static final int TYPE_SCROLL_LR = 6;
    public static final int TYPE_FIX_BOTTOM = 4;

    public CharSequence text;
    public float textSize;
    public int textColor;
    public int textShadowColor;
    public Object flags;
    public int index;
    public Duration duration;
    public float rotationZ;
    public float rotationY;

    private int type;

    public void setTime(long time) {
    }

    public void setTimer(DanmakuTimer timer) {
    }

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }
}

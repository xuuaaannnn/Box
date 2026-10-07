package master.flame.danmaku.danmaku.model.android;

import master.flame.danmaku.danmaku.model.BaseDanmaku;
import master.flame.danmaku.danmaku.model.SpecialDanmaku;

public class DanmakuFactory {
    public static final float BILI_PLAYER_WIDTH = 682f;
    public static final float BILI_PLAYER_HEIGHT = 438f;

    public BaseDanmaku createDanmaku(int type, DanmakuContext context) {
        BaseDanmaku danmaku = type == BaseDanmaku.TYPE_SPECIAL ? new SpecialDanmaku() : new BaseDanmaku();
        danmaku.setType(type);
        return danmaku;
    }

    public void fillTranslationData(BaseDanmaku item, float beginX, float beginY, float endX, float endY, long translationDuration, long translationStartDelay, float scaleX, float scaleY) {
    }

    public void fillAlphaData(BaseDanmaku item, int beginAlpha, int endAlpha, long alphaDuration) {
    }

    public static void fillLinePathData(BaseDanmaku item, float[][] points, float scaleX, float scaleY) {
    }
}

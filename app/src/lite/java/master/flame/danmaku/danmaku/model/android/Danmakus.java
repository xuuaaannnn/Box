package master.flame.danmaku.danmaku.model.android;

public class Danmakus {
    private final Object synchronizer = new Object();

    public Danmakus(int sortType) {
    }

    public Object obtainSynchronizer() {
        return synchronizer;
    }

    public void addItem(Object item) {
    }
}

package org.xwalk.core;

import android.content.Context;

public class XWalkInitializer {
    public interface XWalkInitListener {
        void onXWalkInitStarted();
        void onXWalkInitCancelled();
        void onXWalkInitFailed();
        void onXWalkInitCompleted();
    }

    public XWalkInitializer(XWalkInitListener listener, Context context) {
    }

    public boolean isXWalkReady() {
        return false;
    }

    public boolean initAsync() {
        return false;
    }
}

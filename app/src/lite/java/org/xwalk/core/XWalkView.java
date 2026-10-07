package org.xwalk.core;

import android.content.Context;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import java.util.Map;

public class XWalkView extends FrameLayout {
    private final XWalkSettings settings = new XWalkSettings();

    public XWalkView(Context context) {
        super(context);
    }

    public XWalkSettings getSettings() {
        return settings;
    }

    public void setUIClient(XWalkUIClient client) {
    }

    public void setResourceClient(XWalkResourceClient client) {
    }

    public void loadUrl(String url) {
    }

    public void loadUrl(String url, Map<String, String> headers) {
    }

    public void stopLoading() {
    }

    public void evaluateJavascript(String script, android.webkit.ValueCallback<String> callback) {
        if (callback != null) callback.onReceiveValue(null);
    }

    public void onDestroy() {
    }

    @Override
    public void removeAllViews() {
        super.removeAllViews();
    }
}

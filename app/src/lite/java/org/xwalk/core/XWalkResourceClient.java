package org.xwalk.core;

import android.net.http.SslError;
import android.webkit.ValueCallback;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public class XWalkResourceClient {
    public XWalkResourceClient(XWalkView view) {
    }

    public void onDocumentLoadedInFrame(XWalkView view, long frameId) {}
    public void onLoadStarted(XWalkView view, String url) {}
    public void onLoadFinished(XWalkView view, String url) {}
    public void onProgressChanged(XWalkView view, int progressInPercent) {}

    public XWalkWebResourceResponse shouldInterceptLoadRequest(XWalkView view, XWalkWebResourceRequest request) {
        return null;
    }

    public boolean shouldOverrideUrlLoading(XWalkView view, String url) {
        return false;
    }

    public void onReceivedSslError(XWalkView view, ValueCallback<Boolean> callback, SslError error) {
        if (callback != null) callback.onReceiveValue(false);
    }

    public XWalkWebResourceResponse createXWalkWebResourceResponse(String mimeType, String encoding, InputStream data) {
        return new XWalkWebResourceResponse(mimeType, encoding, data);
    }

    public XWalkWebResourceResponse createXWalkWebResourceResponse(String mimeType, String encoding, InputStream data, int statusCode, String reasonPhrase, Map<String, String> responseHeaders) {
        return new XWalkWebResourceResponse(mimeType, encoding, data, statusCode, reasonPhrase, responseHeaders == null ? new HashMap<>() : responseHeaders);
    }
}

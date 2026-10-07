package org.xwalk.core;

import android.net.Uri;

import java.util.HashMap;
import java.util.Map;

public class XWalkWebResourceRequest {
    public Uri getUrl() {
        return Uri.EMPTY;
    }

    public Map<String, String> getRequestHeaders() {
        return new HashMap<>();
    }
}
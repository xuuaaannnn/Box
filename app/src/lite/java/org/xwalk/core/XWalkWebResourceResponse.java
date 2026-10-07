package org.xwalk.core;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public class XWalkWebResourceResponse {
    public XWalkWebResourceResponse(String mimeType, String encoding, InputStream data) {
    }

    public XWalkWebResourceResponse(String mimeType, String encoding, InputStream data, int statusCode, String reasonPhrase, Map<String, String> responseHeaders) {
    }
}

package org.xwalk.core;

public class XWalkUIClient {
    public enum ConsoleMessageType { DEBUG, ERROR, LOG, INFO, WARNING }

    public XWalkUIClient(XWalkView view) {
    }

    public boolean onConsoleMessage(XWalkView view, String message, int lineNumber, String sourceId, ConsoleMessageType messageType) {
        return false;
    }

    public boolean onJsAlert(XWalkView view, String url, String message, XWalkJavascriptResult result) {
        if (result != null) result.cancel();
        return true;
    }

    public boolean onJsConfirm(XWalkView view, String url, String message, XWalkJavascriptResult result) {
        if (result != null) result.cancel();
        return true;
    }

    public boolean onJsPrompt(XWalkView view, String url, String message, String defaultValue, XWalkJavascriptResult result) {
        if (result != null) result.cancel();
        return true;
    }
}

package org.xwalk.core;

public class XWalkSettings {
    public enum LayoutAlgorithm { NORMAL }

    private String userAgent = "";

    public void setAllowFileAccess(boolean value) {}
    public void setAllowContentAccess(boolean value) {}
    public void setAllowUniversalAccessFromFileURLs(boolean value) {}
    public void setAllowFileAccessFromFileURLs(boolean value) {}
    public void setJavaScriptEnabled(boolean value) {}
    public void setDomStorageEnabled(boolean value) {}
    public void setDatabaseEnabled(boolean value) {}
    public void setUseWideViewPort(boolean value) {}
    public void setLoadWithOverviewMode(boolean value) {}
    public void setSupportZoom(boolean value) {}
    public void setBuiltInZoomControls(boolean value) {}
    public void setDisplayZoomControls(boolean value) {}
    public void setJavaScriptCanOpenWindowsAutomatically(boolean value) {}
    public void setSupportMultipleWindows(boolean value) {}
    public void setCacheMode(int mode) {}
    public void setMixedContentMode(int mode) {}
    public void setBlockNetworkImage(boolean value) {}
    public void setMediaPlaybackRequiresUserGesture(boolean value) {}
    public void setLayoutAlgorithm(LayoutAlgorithm algorithm) {}

    public void setUserAgentString(String userAgent) {
        this.userAgent = userAgent == null ? "" : userAgent;
    }

    public String getUserAgentString() {
        return userAgent;
    }
}

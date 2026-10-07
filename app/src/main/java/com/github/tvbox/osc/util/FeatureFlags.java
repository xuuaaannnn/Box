package com.github.tvbox.osc.util;

import com.github.tvbox.osc.BuildConfig;

public final class FeatureFlags {
    private FeatureFlags() {
    }

    public static boolean isAliPlayerEnabled() {
        return BuildConfig.FEATURE_ALI_PLAYER;
    }

    public static boolean isThunderEnabled() {
        return BuildConfig.FEATURE_THUNDER;
    }

    public static boolean isExoFfmpegEnabled() {
        return BuildConfig.FEATURE_EXO_FFMPEG;
    }

    public static boolean isXWalkEnabled() {
        return BuildConfig.FEATURE_XWALK;
    }

    public static boolean isDanmuEnabled() {
        return BuildConfig.FEATURE_DANMU;
    }
}

package com.github.tvbox.osc.util;

import android.os.Process;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public final class AppExecutors {
    private static final int SEARCH_THREADS = 3;
    private static final int PARSE_THREADS = 2;

    private AppExecutors() {
    }

    public static ExecutorService newSearchPool(String owner) {
        return Executors.newFixedThreadPool(SEARCH_THREADS, factory(owner + "-search"));
    }

    public static ExecutorService newParsePool(String owner) {
        return Executors.newFixedThreadPool(PARSE_THREADS, factory(owner + "-parse"));
    }

    public static ExecutorService newSingle(String owner) {
        return Executors.newSingleThreadExecutor(factory(owner));
    }

    private static ThreadFactory factory(String prefix) {
        AtomicInteger seq = new AtomicInteger(1);
        return runnable -> new Thread(() -> {
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
            runnable.run();
        }, prefix + "-" + seq.getAndIncrement());
    }
}

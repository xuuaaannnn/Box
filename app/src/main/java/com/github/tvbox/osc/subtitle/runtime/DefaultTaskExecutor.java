package com.github.tvbox.osc.subtitle.runtime;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Nullable;

import com.github.tvbox.osc.util.AppExecutors;

import java.util.concurrent.ExecutorService;

/**
 * @author AveryZhong.
 */

public class DefaultTaskExecutor extends TaskExecutor {

    @Nullable
    private Handler mMainHandler;
    private final Object mLock = new Object();
    private final ExecutorService mDeskIO = AppExecutors.newSearchPool("subtitle");

    @Override
    public void executeOnDeskIO(final Runnable task) {
        mDeskIO.execute(task);
    }

    @Override
    public void postToMainThread(final Runnable task) {
        if (mMainHandler == null) {
            synchronized (mLock) {
                mMainHandler = new Handler(Looper.getMainLooper());
            }
        }
        mMainHandler.post(task);
    }

    @Override
    public boolean isMainThread() {
        return Thread.currentThread() == Looper.getMainLooper().getThread();
    }
}

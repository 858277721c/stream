package com.sd.lib.stream;

import android.app.Activity;
import android.content.Context;
import android.os.Build;
import android.view.View;

/**
 * 根据{@link View#isAttachedToWindow()}自动注册和取消注册流对象
 * <p>
 * 注意：不要在以下两个地方绑定，否则有可能导致流对象没办法被自动取消注册<br>
 * 1.目标View对象的{@link View#onDetachedFromWindow()}方法<br>
 * 2.监听目标View对象的{@link View.OnAttachStateChangeListener#onViewDetachedFromWindow(View)}方法
 */
class ViewStreamBinder extends StreamBinder<View>
{
    protected ViewStreamBinder(FStream stream, View target)
    {
        super(stream, target);

        final Context context = target.getContext();
        if (context instanceof Activity)
        {
            if (((Activity) context).isFinishing())
                throw new IllegalArgumentException("Bind stream failed because view's host activity is isFinishing");
        }

        target.addOnAttachStateChangeListener(mOnAttachStateChangeListener);
        registerStream();
    }

    private final View.OnAttachStateChangeListener mOnAttachStateChangeListener = new View.OnAttachStateChangeListener()
    {
        @Override
        public void onViewAttachedToWindow(View v)
        {
            registerStream();
        }

        @Override
        public void onViewDetachedFromWindow(View v)
        {
            unregisterStream();
        }
    };

    @Override
    protected void registerStream()
    {
        if (isAttached(getTarget()))
            super.registerStream();
    }

    @Override
    public final void destroy()
    {
        super.destroy();
        final View target = getTarget();
        if (target != null)
            target.removeOnAttachStateChangeListener(mOnAttachStateChangeListener);
    }

    private static boolean isAttached(View view)
    {
        if (Build.VERSION.SDK_INT >= 19)
            return view.isAttachedToWindow();
        else
            return view.getWindowToken() != null;
    }
}

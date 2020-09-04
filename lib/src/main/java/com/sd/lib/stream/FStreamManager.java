package com.sd.lib.stream;

import android.app.Activity;
import android.util.Log;
import android.view.View;

import com.sd.lib.stream.factory.DefaultStreamFactory;
import com.sd.lib.stream.factory.WeakCacheDefaultStreamFactory;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 流管理类
 */
public class FStreamManager
{
    private static final FStreamManager INSTANCE = new FStreamManager();

    private FStreamManager()
    {
    }

    public static FStreamManager getInstance()
    {
        return INSTANCE;
    }

    private final Map<Class<? extends FStream>, Collection<FStream>> mMapStream = new ConcurrentHashMap<>();
    private final Map<FStream, StreamBinder> mMapStreamBinder = new WeakHashMap<>();
    private final Map<FStream, InternalStreamConnection> mMapStreamConnection = new ConcurrentHashMap<>();

    private final Collection<Class<? extends FStream>> mDirtyHolder = new HashSet<>();

    private boolean mIsDebug;

    public boolean isDebug()
    {
        return mIsDebug;
    }

    public void setDebug(boolean debug)
    {
        mIsDebug = debug;
    }

    /**
     * {@link #bindStream(FStream, Activity)}
     */
    @Deprecated
    public void bindActivity(FStream stream, Activity target)
    {
        bindStream(stream, target);
    }

    /**
     * {@link #bindStream(FStream, View)}
     */
    @Deprecated
    public void bindView(FStream stream, View target)
    {
        bindStream(stream, target);
    }

    /**
     * {@link ActivityStreamBinder}
     *
     * @param stream
     * @param target
     * @return true-绑定成功或者已绑定；false-绑定失败
     */
    public synchronized boolean bindStream(FStream stream, Activity target)
    {
        if (target == null)
            return false;

        if (!checkBindStream(stream))
            return false;

        final StreamBinder oldBinder = mMapStreamBinder.get(stream);
        if (oldBinder != null)
        {
            if (oldBinder.getTarget() == target)
            {
                //  已经绑定过了
                return true;
            } else
            {
                unbindStream(stream);
            }
        }

        final ActivityStreamBinder binder = new ActivityStreamBinder(stream, target);
        if (binder.bind())
        {
            mMapStreamBinder.put(stream, binder);

            if (mIsDebug)
            {
                Log.i(FStream.class.getSimpleName(), "bind activity"
                        + " stream:" + stream
                        + " target:" + target
                        + " size:" + mMapStreamBinder.size());
            }

            return true;
        }
        return false;
    }

    /**
     * {@link ViewStreamBinder}
     *
     * @param stream
     * @param target
     * @return true-绑定成功或者已绑定；false-绑定失败
     */
    public synchronized boolean bindStream(FStream stream, View target)
    {
        if (target == null)
            return false;

        if (!checkBindStream(stream))
            return false;

        final StreamBinder oldBinder = mMapStreamBinder.get(stream);
        if (oldBinder != null)
        {
            if (oldBinder.getTarget() == target)
            {
                //  已经绑定过了
                return true;
            } else
            {
                unbindStream(stream);
            }
        }

        final ViewStreamBinder binder = new ViewStreamBinder(stream, target);
        if (binder.bind())
        {
            mMapStreamBinder.put(stream, binder);

            if (mIsDebug)
            {
                Log.i(FStream.class.getSimpleName(), "bind view"
                        + " stream:" + stream
                        + " target:" + target
                        + " size:" + mMapStreamBinder.size());
            }

            return true;
        }
        return false;
    }

    /**
     * 解绑并取消注册
     *
     * @param stream
     * @return
     */
    public synchronized boolean unbindStream(FStream stream)
    {
        final StreamBinder binder = mMapStreamBinder.remove(stream);
        if (binder != null)
        {
            binder.destroy();

            if (mIsDebug)
            {
                Log.i(FStream.class.getSimpleName(), "unbind"
                        + " stream:" + stream
                        + " target:" + binder.getTarget()
                        + " size:" + mMapStreamBinder.size());
            }

            return true;
        }
        return false;
    }

    private void checkHasBound(FStream stream)
    {
        final StreamBinder binder = mMapStreamBinder.get(stream);
        if (binder != null)
            throw new IllegalArgumentException("stream has bound. stream: " + stream + " target:" + binder.getTarget());
    }

    /**
     * 注册流对象
     *
     * @param stream
     * @return null-注册失败
     */
    public synchronized StreamConnection register(FStream stream)
    {
        checkHasBound(stream);
        return registerInternal(stream);
    }

    /**
     * 取消注册流对象
     *
     * @param stream
     */
    public synchronized void unregister(FStream stream)
    {
        checkHasBound(stream);
        unregisterInternal(stream);
    }

    synchronized StreamConnection registerInternal(FStream stream)
    {
        final Class<? extends FStream>[] classes = getStreamClass(stream);
        if (classes == null || classes.length <= 0)
            return null;

        InternalStreamConnection streamConnection = mMapStreamConnection.get(stream);
        if (streamConnection == null)
        {
            streamConnection = new InternalStreamConnection(stream, classes);
            mMapStreamConnection.put(stream, streamConnection);
        }

        for (Class<? extends FStream> item : classes)
        {
            Collection<FStream> holder = mMapStream.get(item);
            if (holder == null)
            {
                holder = new HashSet<>();
                mMapStream.put(item, holder);
            }

            if (holder.add(stream))
            {
                if (mIsDebug)
                {
                    Log.i(FStream.class.getSimpleName(), "+++++ register"
                            + " stream:" + stream
                            + " class:" + item.getName()
                            + " count:" + (holder.size()));
                }
            }
        }
        return streamConnection;
    }

    synchronized void unregisterInternal(FStream stream)
    {
        final Class<? extends FStream>[] classes = getStreamClass(stream);
        if (classes == null || classes.length <= 0)
            return;

        mMapStreamConnection.remove(stream);

        for (Class<? extends FStream> item : classes)
        {
            final Collection<FStream> holder = mMapStream.get(item);
            if (holder == null)
                continue;

            if (holder.remove(stream))
            {
                if (holder.isEmpty())
                    mMapStream.remove(item);

                if (mIsDebug)
                {
                    Log.i(FStream.class.getSimpleName(), "----- unregister"
                            + " stream:" + stream
                            + " class:" + item.getName()
                            + " count:" + (holder.size()));
                }
            }
        }
    }

    public StreamConnection getConnection(FStream stream)
    {
        return mMapStreamConnection.get(stream);
    }

    private Comparator<FStream> newStreamComparator(Class<? extends FStream> clazz)
    {
        return new InternalStreamComparator(clazz);
    }

    private final class InternalStreamConnection extends StreamConnection
    {
        InternalStreamConnection(FStream stream, Class<? extends FStream>[] classes)
        {
            super(stream, classes);
        }

        @Override
        protected void onPriorityChanged(int priority, FStream stream, Class<? extends FStream> clazz)
        {
            synchronized (FStreamManager.this)
            {
                mDirtyHolder.add(clazz);
                if (isDebug())
                {
                    Log.i(FStream.class.getSimpleName(), "onPriorityChanged"
                            + " priority:" + priority
                            + " clazz:" + clazz.getName()
                            + " stream:" + stream);
                }
            }
        }
    }

    private final class InternalStreamComparator implements Comparator<FStream>
    {
        private final Class<? extends FStream> nClass;

        public InternalStreamComparator(Class<? extends FStream> clazz)
        {
            nClass = clazz;
        }

        @Override
        public int compare(FStream o1, FStream o2)
        {
            final StreamConnection o1Connection = getConnection(o1);
            final StreamConnection o2Connection = getConnection(o2);
            if (o1Connection != null && o2Connection != null)
            {
                return o2Connection.getPriority(nClass) - o1Connection.getPriority(nClass);
            }
            return 0;
        }
    }

    private static boolean checkBindStream(FStream stream)
    {
        final Class<? extends FStream>[] classes = getStreamClass(stream, true);
        return classes.length > 0;
    }

    private static Class<? extends FStream>[] getStreamClass(FStream stream)
    {
        return getStreamClass(stream, false);
    }

    private static Class<? extends FStream>[] getStreamClass(FStream stream, boolean getOne)
    {
        final Class<?> sourceClass = stream.getClass();

        final Set<Class<? extends FStream>> set = findAllStreamClass(sourceClass, getOne);
        return set.toArray(new Class[set.size()]);
    }

    private static Set<Class<? extends FStream>> findAllStreamClass(Class<?> clazz, boolean getOne)
    {
        checkProxyClass(clazz);
        final Set<Class<? extends FStream>> set = new HashSet<>();

        while (true)
        {
            if (clazz == null)
                break;
            if (!FStream.class.isAssignableFrom(clazz))
                break;
            if (clazz.isInterface())
                throw new RuntimeException("clazz must not be an interface");

            for (Class<?> item : clazz.getInterfaces())
            {
                if (FStream.class.isAssignableFrom(item) && FStream.class != item)
                {
                    set.add((Class<? extends FStream>) item);

                    if (getOne && set.size() > 0)
                        return set;
                }
            }

            clazz = clazz.getSuperclass();
        }

        return set;
    }

    /**
     * 生成代理对象
     *
     * @param builder
     * @return
     */
    FStream newProxyInstance(FStream.ProxyBuilder builder)
    {
        final Class<?> clazz = builder.mClass;
        final InvocationHandler handler = new FStreamManager.ProxyInvocationHandler(this, builder);
        return (FStream) Proxy.newProxyInstance(clazz.getClassLoader(), new Class<?>[]{clazz}, handler);
    }

    private static final class ProxyInvocationHandler implements InvocationHandler
    {
        private final FStreamManager mManager;

        private final Class<? extends FStream> mClass;
        private final Object mTag;
        private final FStream.DispatchCallback mDispatchCallback;
        private final FStream.ResultFilter mResultFilter;

        public ProxyInvocationHandler(FStreamManager manager, FStream.ProxyBuilder builder)
        {
            mManager = manager;

            mClass = builder.mClass;
            mTag = builder.mTag;
            mDispatchCallback = builder.mDispatchCallback;
            mResultFilter = builder.mResultFilter;
        }

        private boolean checkTag(FStream stream)
        {
            final Object tag = stream.getTagForStream(mClass);
            if (mTag == tag)
                return true;

            return mTag != null && mTag.equals(tag);
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable
        {
            final String methodName = method.getName();
            final Class<?> returnType = method.getReturnType();

            final Class<?>[] parameterTypes = method.getParameterTypes();
            if ("getTagForStream".equals(methodName)
                    && parameterTypes.length == 1 && parameterTypes[0] == Class.class)
            {
                throw new RuntimeException(methodName + " method can not be called on proxy instance");
            }


            final boolean isVoid = returnType == void.class || returnType == Void.class;
            Object result = processMainLogic(isVoid, method, args);


            if (isVoid)
            {
                result = null;
            } else if (returnType.isPrimitive() && result == null)
            {
                if (boolean.class == returnType)
                    result = false;
                else
                    result = 0;

                if (mManager.isDebug())
                    Log.e(FStream.class.getSimpleName(), "return type:" + returnType + " but method result is null, so set to " + result);
            }

            if (mManager.isDebug() && !isVoid)
                Log.i(FStream.class.getSimpleName(), "notify final return:" + result);

            return result;
        }

        private Object processMainLogic(final boolean isVoid, final Method method, final Object[] args) throws Throwable
        {
            final Collection<FStream> holder = mManager.mMapStream.get(mClass);
            List<FStream> listStream = null;

            if (mManager.isDebug())
            {
                Log.i(FStream.class.getSimpleName(), "notify -----> " + method + " " + (args == null ? "" : Arrays.toString(args))
                        + " tag:" + mTag
                        + " count:" + (holder == null ? 0 : holder.size()));
            }

            if (holder == null || holder.isEmpty())
            {
                final FStream stream = mManager.getDefaultStream(mClass);
                if (stream == null)
                    return null;

                listStream = new ArrayList<>(1);
                listStream.add(stream);

                if (mManager.isDebug())
                    Log.i(FStream.class.getSimpleName(), "create default stream:" + stream + " for class:" + mClass.getName());
            } else
            {
                synchronized (mManager)
                {
                    if (mManager.mDirtyHolder.remove(mClass))
                    {
                        final List<FStream> listEntry = new ArrayList<>(holder);
                        Collections.sort(listEntry, mManager.newStreamComparator(mClass));

                        if (mManager.isDebug())
                            Log.i(FStream.class.getSimpleName(), "sort stream");
                    }
                }
                listStream = new ArrayList<>(holder);
            }

            final boolean filterResult = mResultFilter != null && !isVoid;
            final List<Object> listResult = filterResult ? new LinkedList<>() : null;

            Object result = null;
            int index = 0;
            for (FStream item : listStream)
            {
                if (!checkTag(item))
                    continue;

                if (mDispatchCallback != null)
                {
                    if (mDispatchCallback.beforeDispatch(item, method, args))
                    {
                        if (mManager.isDebug())
                            Log.i(FStream.class.getSimpleName(), "notify broken before dispatch");
                        break;
                    }
                }

                Object itemResult = null;
                boolean shouldBreakDispatch = false;

                synchronized (mClass)
                {
                    final StreamConnection connection = mManager.mMapStreamConnection.get(item);
                    connection.enableBreakDispatch(mClass);

                    itemResult = method.invoke(item, args);

                    shouldBreakDispatch = connection.shouldBreakDispatch(mClass);
                    connection.resetBreakDispatch(mClass);
                }

                if (mManager.isDebug())
                {
                    Log.i(FStream.class.getSimpleName(), "notify"
                            + " index:" + index
                            + " stream:" + item
                            + " return:" + (isVoid ? "" : itemResult)
                            + " shouldBreakDispatch:" + shouldBreakDispatch);
                }

                result = itemResult;

                if (filterResult)
                    listResult.add(itemResult);

                if (mDispatchCallback != null)
                {
                    if (mDispatchCallback.afterDispatch(item, method, args, itemResult))
                    {
                        if (mManager.isDebug())
                            Log.i(FStream.class.getSimpleName(), "notify broken after dispatch");
                        break;
                    }
                }

                if (shouldBreakDispatch)
                    break;

                index++;
            }

            if (filterResult && !listResult.isEmpty())
            {
                result = mResultFilter.filter(method, args, listResult);

                if (mManager.isDebug())
                    Log.i(FStream.class.getSimpleName(), "filter result: " + result);
            }

            return result;
        }
    }

    //---------- default stream start ----------

    private final Map<Class<? extends FStream>, Class<? extends FStream>> mMapDefaultStreamClass = new ConcurrentHashMap<>();
    private DefaultStreamFactory mDefaultStreamFactory;

    /**
     * 注册默认的流接口实现类
     * <p>
     * {@link DefaultStreamFactory}
     *
     * @param clazz
     */
    public synchronized void registerDefaultStream(Class<? extends FStream> clazz)
    {
        checkFStreamClass(clazz);

        final Set<Class<? extends FStream>> set = findAllStreamClass(clazz, false);
        if (set.isEmpty())
            throw new IllegalArgumentException("stream class was not found in " + clazz);

        for (Class<? extends FStream> item : set)
        {
            mMapDefaultStreamClass.put(item, clazz);
        }
    }

    /**
     * 取消注册默认的流接口实现类
     *
     * @param clazz
     */
    public synchronized void unregisterDefaultStream(Class<? extends FStream> clazz)
    {
        checkFStreamClass(clazz);

        final Set<Class<? extends FStream>> set = findAllStreamClass(clazz, false);
        if (set.isEmpty())
            return;

        for (Class<? extends FStream> item : set)
        {
            mMapDefaultStreamClass.remove(item);
        }
    }

    /**
     * 设置{@link DefaultStreamFactory}
     *
     * @param defaultStreamFactory
     */
    public synchronized void setDefaultStreamFactory(DefaultStreamFactory defaultStreamFactory)
    {
        mDefaultStreamFactory = defaultStreamFactory;
    }

    private synchronized FStream getDefaultStream(Class<? extends FStream> clazz)
    {
        final Class<? extends FStream> defaultClass = mMapDefaultStreamClass.get(clazz);
        if (defaultClass == null)
            return null;

        if (mDefaultStreamFactory == null)
            mDefaultStreamFactory = new WeakCacheDefaultStreamFactory();

        final DefaultStreamFactory.CreateParam param = new DefaultStreamFactory.CreateParam(clazz, defaultClass);
        final FStream stream = mDefaultStreamFactory.create(param);
        if (stream == null)
            throw new RuntimeException(mDefaultStreamFactory + " create null for param:" + param);

        return stream;
    }

    //---------- default stream end ----------

    private static void checkProxyClass(Class<?> clazz)
    {
        if (Proxy.isProxyClass(clazz))
            throw new IllegalArgumentException("proxy class is not supported");
    }

    private static void checkFStreamClass(Class<?> clazz)
    {
        if (clazz == FStream.class)
            throw new IllegalArgumentException("class must not be " + FStream.class);
    }
}

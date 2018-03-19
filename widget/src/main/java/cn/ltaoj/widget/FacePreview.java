package cn.ltaoj.widget;

import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.animation.AccelerateDecelerateInterpolator;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Created by ltaoj on 2018/3/18 1:23.
 * 用于显示人脸区域的SurfaceView
 */

public class FacePreview extends SurfaceView implements SurfaceHolder.Callback, Runnable {
    private static final String TAG = "FacePreview";

    // 默认矩形宽度
    private static final float DEFAULT_PREVIEW_WIDTH = 240;

    // 默认矩形高度
    private static final float DEFAULT_PREVIEW_HEIGHT = 240;

    // 默认边角颜色
    private static final int DEFAULT_CORNER_COLOR = Color.GREEN;

    // 默认边角线宽度
    private static final float DEFAULT_CORNER_WIDTH = 20F;

    // 设置矩形边框颜色
    private static final int DEFAULT_BORDER_COLOR = Color.WHITE;

    // 默认提示文字大小
    private static final float DEFAULT_TIP_TEXT_SIZE = 16F;

    // 默认提示文字颜色
    private static final int DEFAULT_TIP_TEXT_COLOR = Color.GRAY;

    // 默认边角线长度
    private static final float DEFAULT_CORNER_LENGTH = 50F;

    // 矩形框形状
    private RectF mArea;
    // 矩形框在屏幕实际位置
    private RectF mRect;
    // 矩形框与屏幕放缩比例
    private float maxScale = 0.6f;

    // 边角颜色
    private int mCrnColor;
    // 边角宽度
    private float mCrnWidth;
    // 矩形边框颜色
    private int mBdColor;

    // 屏幕宽高密度
    private int screenWidth;
    private int screenHeight;
    private float density;

    // 提示文字
    private String mTipText;
    // 文字大小
    private float mTipTextSize;
    // 提示文字颜色
    private int mTipTextColor;
    // 提示文字顶部与矩形区域底部的距离
    private int textMarginRect = 50;
    // 文字实际大小
    private Rect textBounds;

    // 是否显示提示文字
    private boolean mShowTip;

    // 界面配置类集合
    private List<PreviewConfig> mConfigs;
    // 当前界面配置
    private int curConfig;

    // 界面状态
    private PreviewState mPreviewState;

    // 界面状态变化监听接口
    private OnPreviewChangeListener mChangeListener;

    private SurfaceHolder mHolder;

    // 画布
    private Canvas mCanvas;
    // 矩形框绘制画笔
    private Paint mRectPaint;
    // 边角绘制画笔
    private Paint mCrnPaint;
    // 扫描线绘制画笔
    private Paint mScanPaint;
    // 提示文字绘制画笔
    private Paint mTextPaint;

    // 绘制线程
    private Thread mDrawTread;
    // 表示线程运行状态
    private boolean isDrawRun;

    // 表示第一次绘制背景
    private boolean firstDrawBg;
    // 表示是否需要重新绘制背景
    private boolean shouldRedrawBg;
    // 表示上次提示文字时候擦除
    private boolean hasSweepTip;

    // 扫描动画
    private ValueAnimator mScanAnimator;
    // 动画执行状态
    private boolean isAnimatorRun;
    // 表示是否更新Animator的ProperHolder
    private boolean needChangeAnimator;

    private static final int MSG_CREATE = 0;
    private static final int MSG_PAUSE = 1;
    private static final int MSG_RESTART = 2;
    private static final int MSG_DESTROY = 3;

    // 用于处理暂停，重启动画的消息
    private Handler mHandler = new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_CREATE:
                    show();
                    break;
                case MSG_PAUSE:
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                        mScanAnimator.pause();
                    }
                    break;
                case MSG_RESTART:
                    mScanAnimator.start();
                    break;
                case MSG_DESTROY:
                    stop();
                    break;
            }
            return true;
        }
    });
    /**
     * 就绪状态：预览区域显示完毕，但是没有连接图像
     * 检测状态：预览区域出现状态，并且伴随检测一些动画提示
     * 暂停状态：预览区域图像停止
     * 完成状态：预览区域图像停止，回收资源，销毁
     */
    public enum PreviewState {
        READY,
        DETECTING,
        PAUSE,
        COMPLETE
    }

    /**
     * 监听预览组件状态变化
     */
    public interface OnPreviewChangeListener {

        /**
         * 当初始化完成，预览窗口的状态
         */
        void onReady();

        /**
         * 检测窗口处于暂停状态
         */
        void onPause();

        /**
         * 窗口处于检测状态
         */
        void onDetecting();

        /**
         * 检测完成状态
         */
        void onComplete();
    }

    /**
     * 显示区域属性配置类,静态内部类
     */
    public static class PreviewConfig {
        // 区域形状，宽高
        float previewWidth;
        float previewHeight;
        // 提示文字
        String tipText;
        // 提示文字开关
        boolean showTip;
        // 窗口监听器
        OnPreviewChangeListener listener;

        public PreviewConfig(float previewWidth, float previewHeight, String tipText, boolean showTip, OnPreviewChangeListener listener) {
            this.previewWidth = previewWidth;
            this.previewHeight = previewHeight;
            this.tipText = tipText;
            this.showTip = showTip;
            this.listener = listener;
        }
    }

    public FacePreview(Context context) {
        this(context, null);
    }

    public FacePreview(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public FacePreview(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initAttrs(context, attrs, defStyleAttr);
        initPreview();
    }

    /**
     * 从组件所在的布局文件读取设置的属性，并初始化相关成员变量
     * @param context
     * @param attrs
     * @param defStyleAttr
     */
    private void initAttrs(Context context, AttributeSet attrs, int defStyleAttr) {
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.FacePreview, defStyleAttr, 0);
        DisplayMetrics dm = context.getResources().getDisplayMetrics();
        screenWidth = dm.widthPixels;
        screenHeight = dm.heightPixels;
        density = dm.density;

        mArea = new RectF(0, 0, 0, 0);
        float pWidth = a.getDimensionPixelSize(R.styleable.FacePreview_preview_width, -1);
        if (pWidth < 0) {
            mArea.right = DEFAULT_PREVIEW_WIDTH * density;
        } else {
            mArea.right = Math.min(pWidth, screenWidth * maxScale);
        }

        float pHeight = a.getDimensionPixelSize(R.styleable.FacePreview_preview_height, -1);
        if (pHeight < 0) {
            mArea.bottom = DEFAULT_PREVIEW_HEIGHT * density;
        } else {
            mArea.bottom = Math.min(pWidth, screenHeight * maxScale);
        }

        mCrnColor = a.getInt(R.styleable.FacePreview_corner_color, -1);
        if (mCrnColor < 0) {
            mCrnColor = DEFAULT_CORNER_COLOR;
        }

        mCrnWidth = a.getFloat(R.styleable.FacePreview_corner_width, -1);
        if (mCrnWidth < 0) {
            mCrnWidth = DEFAULT_CORNER_WIDTH;
        }
        // 计算矩形框在屏幕的实际位置
        mRect = new RectF();
        computeRect(mArea.width(), mArea.height());

        mBdColor = a.getColor(R.styleable.FacePreview_border_color, -1);
        if (mBdColor < 0) {
            mBdColor = DEFAULT_BORDER_COLOR;
        }

        mTipTextSize = a.getDimension(R.styleable.FacePreview_tip_text_size, -1);
        if (mTipTextSize < 0) {
            mTipTextSize = DEFAULT_TIP_TEXT_SIZE;
        }

        mTipTextColor = a.getColor(R.styleable.FacePreview_tip_text_color, -1);
        if (mTipTextColor < 0) {
            mTipTextColor = DEFAULT_TIP_TEXT_COLOR;
        }

        a.recycle();
    }

    /**
     * 初始化组件相关成员配置
     * 初始化画笔
     * 默认组件配置
     */
    private void initPreview() {
        if (mConfigs == null) {
            mConfigs = new ArrayList<PreviewConfig>();
            mConfigs.add(new PreviewConfig(0f, 0f, "将方框对准人脸，即可自动识别", true, null));
            applyConfig(0);
        }

        mHolder = getHolder();
        mHolder.addCallback(this);
        // 设置透明度
        mHolder.setFormat(PixelFormat.TRANSPARENT);
        setZOrderOnTop(true);

        mRectPaint  = new Paint();
        mRectPaint.setAntiAlias(true);
        mRectPaint.setDither(true);
        mRectPaint.setColor(mBdColor);
        mRectPaint.setStyle(Paint.Style.FILL_AND_STROKE);
        mRectPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));

        mCrnPaint = new Paint();
        mCrnPaint.setAntiAlias(true);
        mCrnPaint.setDither(true);
        mCrnPaint.setColor(mCrnColor);
        mCrnPaint.setStrokeWidth(mCrnWidth);

        mScanPaint = new Paint();
        mScanPaint.setAntiAlias(true);
        mScanPaint.setDither(true);
        // to do

        mTextPaint = new Paint();
        mTextPaint.setAntiAlias(true);
        mTextPaint.setDither(true);
        mTextPaint.setTextSize(mTipTextSize * density);
        mTextPaint.setColor(mTipTextColor);
        mTextPaint.setTextAlign(Paint.Align.CENTER);

        mPreviewState = PreviewState.READY;
        mShowTip = true;
        hasSweepTip = true;
        setKeepScreenOn(true);
    }

    /**
     * 通过设置的组件大小，计算在屏幕中的位置
     * @param previewWidth
     * @param previewHeight
     */
    private void computeRect(float previewWidth, float previewHeight) {
        mRect.left = (screenWidth - previewWidth) / 2;
        mRect.right = (screenWidth + previewWidth) / 2;
        mRect.top = (screenHeight - previewHeight) / 2;
        mRect.bottom = (screenHeight + previewHeight) / 2;
    }

    /**
     * 组件提供的外部接口
     * 设置界面配置集合
     * @param configs
     */
    public void setPreviewConfigs(List<PreviewConfig> configs) {
        if (configs != null && configs.size() > 0) {
            mConfigs = configs;
            applyConfig(0);
        }
    }

    /**
     * 组件提供的外部接口
     * 应用界面设置的方法
     * 设置内容包括矩形框大小
     * 提示文字内容、是否显示
     * 状态监听
     * @param index
     */
    public void applyConfig(int index) {
        if (index < 0 || index >= mConfigs.size()) {
            return;
        }

        PreviewConfig config = mConfigs.get(index);
        float pWidth = config.previewWidth, pHeight = config.previewHeight;
        if (pWidth <= 0) {
            pWidth = DEFAULT_PREVIEW_WIDTH * density;
        } else {
            pWidth = Math.min(pWidth * density, screenWidth * maxScale);
        }

        if (pHeight <= 0) {
            pHeight = DEFAULT_PREVIEW_HEIGHT * density;
        } else {
            pHeight = Math.min(pHeight * density, screenHeight * maxScale);
        }
        computeRect(pWidth, pHeight);

        if (config.listener != null) {
            mChangeListener = config.listener;
        }

        mTipText = config.tipText;
        curConfig = index;

        // 通知改变
        if (mDrawTread != null) {
            // 如果不是初始化，那么才会通知改变
            // 重新绘制背景
            // 重新设置动画属性
            // 顺序为1、绘制背景 2、绘制提示文字 3、更新扫描动画属性
            shouldRedrawBg = true;
            mShowTip = config.showTip;
            needChangeAnimator = true;
        }
    }

    @Override
    public final void run() {
        Timer timer = null;
        while (isDrawRun) {
            if (!firstDrawBg || shouldRedrawBg) { // 当SurfaceView创建成功时或者收到重新绘制指令时
                // 只负责绘制双缓冲背景
                drawBackground();
            }

            // 绘制提示文字,如果扫描动画没有加载，那么即使设置提示文字也无效
            if (mShowTip && mScanAnimator != null) {
                // 擦除上次绘制的提示文字
                if (!hasSweepTip) {
                    sweepTipText();
                }

                drawTipText();
                if (timer != null) {
                    timer.cancel(); // 取消之前的定时任务
                }
                // 启动定时任务，10s后将提示文字擦除
                timer = new Timer();
                timer.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        sweepTipText();
                    }
                }, 10000);
            }

            if (needChangeAnimator) {
                changeAnimatorProperty();
            }
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        // 创建并开启绘制线程
        if (mDrawTread == null) {
            isDrawRun = true;
            mDrawTread = new Thread(this);
            mDrawTread.start();
        }

        // 发送创建消息，开始扫描动画
        show();
    }

    @Override
    public final void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

    }

    @Override
    public final void surfaceDestroyed(SurfaceHolder holder) {
        Message msg = new Message();
        msg.what = MSG_DESTROY;
        mHandler.sendMessage(msg);
    }

    /**
     * 初始化扫描动画
     */
    private void initScanAnimator() {
        float left = mRect.left + DEFAULT_CORNER_LENGTH / 2;
        float right = mRect.right - DEFAULT_CORNER_LENGTH / 2;
        final RectF oval = new RectF(left, 0, right, 0);
        final int shortAxis = 5;

        PropertyValuesHolder lineValues = PropertyValuesHolder.ofFloat("down", mRect.top + 5, mRect.bottom - 5);
        mScanAnimator = ValueAnimator.ofPropertyValuesHolder(lineValues);
        mScanAnimator.setDuration(3000);
        mScanAnimator.setRepeatMode(ValueAnimator.RESTART);
        mScanAnimator.setRepeatCount(ValueAnimator.INFINITE);
        mScanAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
        mScanAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                float centerY = obj2Float(animation.getAnimatedValue("down"));
                oval.top = centerY - shortAxis;
                oval.bottom = centerY + shortAxis;
                // 此处只锁定矩形框的绘制区域即可，否则会有轻微闪烁
                try {
                    mCanvas = mHolder.lockCanvas(new Rect((int) mRect.left, (int) mRect.top, (int) mRect.right,(int) mRect.bottom));
//                mCanvas = mHolder.lockCanvas();
                    if (mCanvas != null) {
                        drawRectACrn();
                        mScanPaint.setShader(new RadialGradient(oval.centerX(), oval.centerY(), oval.width() / 2,
                                Color.argb(200, 0, 255, 0), Color.argb(0, 0, 255, 0),
                                Shader.TileMode.REPEAT));
                        mCanvas.drawOval(oval, mScanPaint);
                        mHolder.unlockCanvasAndPost(mCanvas);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }

    /**
     * 创建显示扫描动画
     */
    private void show() {
        // 创建并开启扫描动画
        if (mScanAnimator == null) {
            initScanAnimator();
            isAnimatorRun = true;
            updatePreviewState();
            mScanAnimator.start();
        }
    }

    /**
     * 停止绘制线程以及动画
     */
    private void stop() {
        try {
            // 停止背景绘制线程
            isDrawRun = false;
            mDrawTread.interrupt();
            mDrawTread = null;

            // 停止扫描动画
            isAnimatorRun = false;
            updatePreviewState();
            mScanAnimator.end();
            mScanAnimator = null;

            // 设置下次启动时，重新绘制背景
            shouldRedrawBg = true;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 负责更新扫描动画属性
     */
    private void changeAnimatorProperty() {
        PropertyValuesHolder values = PropertyValuesHolder.ofFloat("down", mRect.top + 5, mRect.bottom - 5);
        mScanAnimator.setValues(values);

        // 更新标志位
        needChangeAnimator = false;
    }

    /**
     * 负责绘制背景
     */
    private void drawBackground() {
        // 绘制矩形以及边角,因为双缓冲，所以绘制两次背景
        for (int i = 0;i < 2;i++) {
            try {
                mCanvas = mHolder.lockCanvas();
                if (mCanvas != null) {
//                    clearCanvas(mCanvas);
                    // 绘制整个Canvas的背景色
                    mCanvas.drawARGB(200, 0, 0, 0);
                    mHolder.unlockCanvasAndPost(mCanvas);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // 绘制成功后将重置相关标志位
        firstDrawBg = true;
        shouldRedrawBg = false;
    }

    // 清空画布操作,参数为锁定的画布
    private void clearCanvas(Canvas canvas) {
        Paint paint = new Paint();
        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        canvas.drawPaint(paint);
    }

    /**
     * 负责绘制提示文字
     * 在绘制文字之前确保mRect已经更新
     */
    private void drawTipText() {
        textBounds = new Rect();
        mTextPaint.getTextBounds(mTipText, 0, mTipText.length(), textBounds);
        for (int i = 0;i < 2;i++) {
            try {
                mCanvas = mHolder.lockCanvas();
                // 绘制时bug，y为baseline坐标
                if (mCanvas != null) {
                    mCanvas.drawText(mTipText, mRect.centerX(), mRect.bottom + textMarginRect + textBounds.height(), mTextPaint);
                    mHolder.unlockCanvasAndPost(mCanvas);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // 更新相关标志位
        mShowTip = false;
        hasSweepTip = false;
    }

    /**
     * 负责擦除文字
     * 擦出文字的区域是根据mRect以及旧的文字算出来，所以在重新设置新的提示文字之前，应该先擦除提示文字
     */
    private void sweepTipText() {
        // 由于精度转换，可能边框显示未擦除
        Rect rect = new Rect((int)(mRect.centerX() - textBounds.width() / 2.0f),(int)(mRect.bottom + textMarginRect),
                (int)(mRect.centerX() + textBounds.width() / 2.0f),(int)(mRect.bottom + textMarginRect + textBounds.height() + 5));
        for (int i = 0;i < 2;i++) {
            try {
                mCanvas = mHolder.lockCanvas(rect);
                if (mCanvas != null) {
                    mCanvas.drawPaint(mRectPaint);
                    mCanvas.drawARGB(200, 0, 0, 0);
                    mHolder.unlockCanvasAndPost(mCanvas);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // 更新标志位
        hasSweepTip = true;
    }

    /**
     * 负责绘制矩形区域以及边角
     * 需要确保已经获取锁定的Canvas
     * 此方法用在lockCanvas和UnlockCanvas之间
     */
    private void drawRectACrn() {
        if (mCanvas != null) {
            // 绘制矩形框
            mCanvas.drawRect(mRect, mRectPaint);

            /**
             * |----左上角
             * |
             */
            mCanvas.drawLine(mRect.left, mRect.top + DEFAULT_CORNER_LENGTH, mRect.left, mRect.top, mCrnPaint);
            mCanvas.drawLine(mRect.left, mRect.top, mRect.left + DEFAULT_CORNER_LENGTH, mRect.top, mCrnPaint);

            /**
             * -----| 右上角
             *      |
             */
            mCanvas.drawLine(mRect.right - DEFAULT_CORNER_LENGTH, mRect.top, mRect.right, mRect.top, mCrnPaint);
            mCanvas.drawLine(mRect.right, mRect.top, mRect.right, mRect.top + DEFAULT_CORNER_LENGTH, mCrnPaint);

            /**
             *      | 右下角
             * -----|
             */
            mCanvas.drawLine(mRect.right, mRect.bottom - DEFAULT_CORNER_LENGTH, mRect.right, mRect.bottom, mCrnPaint);
            mCanvas.drawLine(mRect.right, mRect.bottom, mRect.right - DEFAULT_CORNER_LENGTH, mRect.bottom, mCrnPaint);

            /**
             * |      左下角
             * |-----
             */
            mCanvas.drawLine(mRect.left + DEFAULT_CORNER_LENGTH, mRect.bottom, mRect.left, mRect.bottom, mCrnPaint);
            mCanvas.drawLine(mRect.left, mRect.bottom, mRect.left, mRect.bottom - DEFAULT_CORNER_LENGTH, mCrnPaint);
        }
    }

//    private void drawScan() {
//        while (isDrawRun) {
//            // 每次前进的距离
//            float delta = 3;
//            float left = mRect.left + DEFAULT_CORNER_LENGTH / 2;
//            float right = mRect.right - DEFAULT_CORNER_LENGTH / 2;
//            float top = mRect.top;
//            float bottom = mRect.top + 8;
//            RectF oval = new RectF(left, top, right, bottom);
//
//            while (mPreviewState == PreviewState.DETECTING) {
//                oval.top = top;
//                oval.bottom = bottom;
//                mCanvas = mHolder.lockCanvas(new Rect((int) mRect.left, (int) mRect.top, (int) mRect.right,(int) mRect.bottom));
//                if (mCanvas != null) {
//                    draw();
//                    mScanPaint.setShader(new RadialGradient(oval.centerX(), oval.centerY(), oval.width() / 2,
//                            Color.argb(255, 0, 255, 0), Color.argb(0, 0, 255, 0),
//                            Shader.TileMode.REPEAT));
//                    mCanvas.drawOval(oval, mScanPaint);
//                    mHolder.unlockCanvasAndPost(mCanvas);
//                }
//                try {
//                    if (!mDrawTread.isInterrupted())
//                        Thread.sleep(10);
//                } catch (InterruptedException e) {
//                    e.printStackTrace();
//                }
//                if (bottom >= mRect.bottom - 5) {
//                    top = mRect.top;
//                    bottom = top + 8;
//                } else {
//                    top += delta;
//                    bottom += delta;
//                }
//            }
//        }
//    }

    private float obj2Float(Object o) {
        return ((Number) o).floatValue();
    }

    public void setChangeListener(OnPreviewChangeListener changeListener) {
        this.mChangeListener = changeListener;
    }

    private void setPreviewState(PreviewState previewState) {
        if (mPreviewState != previewState) {
            mPreviewState = previewState;
            Message msg = null;
            switch (previewState) {
                case READY:
//                    mChangeListener.onReady();
                    break;
                case DETECTING:
//                    mChangeListener.onDetecting();
                    msg = new Message();
                    if (mScanAnimator == null) {
                        msg.what = MSG_CREATE;
                    } else {
                        msg.what = MSG_RESTART;
                    }
                    break;
                case PAUSE:
                    msg = new Message();
                    msg.what = MSG_PAUSE;
//                    mChangeListener.onPause();
                    break;
                case COMPLETE:
//                    mChangeListener.onComplete();
                    break;
            }

            if (msg != null) {
                mHandler.sendMessage(msg);
            }
        }
    }

    /**
     * 恢复检测
     */
    public void resumeDetect() {
        setPreviewState(PreviewState.DETECTING);
    }

    /**
     * 暂停检测
     */
    public void pauseDetect() {
        setPreviewState(PreviewState.PAUSE);
    }

    /**
     * 返回矩形框在屏幕中的位置
     * @return
     */
    public RectF getRect() {
        return mRect;
    }

    private void updatePreviewState() {
        if (isAnimatorRun) {
            switch (mPreviewState) {
                case READY:
                case PAUSE:
                    mPreviewState = PreviewState.DETECTING;
                    if (mChangeListener != null)
                        mChangeListener.onDetecting();
                    break;
            }
        } else {
            switch (mPreviewState) {
                case DETECTING:
                    mPreviewState = PreviewState.PAUSE;
                    if (mChangeListener != null)
                        mChangeListener.onPause();
            }
        }
    }
}

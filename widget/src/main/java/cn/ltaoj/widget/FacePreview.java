package cn.ltaoj.widget;

import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
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
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
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
    private static final String TAG = "CameraPreview";

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

    // 扫描动画
    private ValueAnimator mScanAnimator;
    // 动画执行状态
    private boolean isAnimatorRun;

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
        setKeepScreenOn(true);
    }

    private void computeRect(float previewWidth, float previewHeight) {
        mRect.left = (screenWidth - previewWidth) / 2;
        mRect.right = (screenWidth + previewWidth) / 2;
        mRect.top = (screenHeight - previewHeight) / 2;
        mRect.bottom = (screenHeight + previewHeight) / 2;
    }

    public void setPreviewConfigs(List<PreviewConfig> configs) {
        if (configs != null && configs.size() > 0) {
            mConfigs = configs;
            applyConfig(0);
        }
    }

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
        mShowTip = config.showTip;

        curConfig = index;
        // 通知改变
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        // 创建并开启绘制线程
        if (mDrawTread == null) {
            mDrawTread = new Thread(this);
            mDrawTread.start();
        }

        // 创建并开启扫描动画
        if (mScanAnimator == null) {
            initScanAnimator();
            isAnimatorRun = true;
            updatePreviewState();
            mScanAnimator.start();
        }
    }

    @Override
    public final void run() {
        // 只负责绘制双缓冲背景
        drawBackground();
        if (mShowTip) {
            new Timer().schedule(new TimerTask() {
                @Override
                public void run() {
                    mShowTip = false;
                    sweepTipText();
                }
            }, 10000);
        }
        isDrawRun = false;
    }

    @Override
    public final void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

    }

    @Override
    public final void surfaceDestroyed(SurfaceHolder holder) {
        try {
            // 停止背景绘制线程
            mDrawTread = null;

            // 停止扫描动画
            isAnimatorRun = false;
            updatePreviewState();
            mScanAnimator.end();
            mScanAnimator = null;
        } catch (Exception e) {
            e.printStackTrace();
        }
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
                mCanvas = mHolder.lockCanvas(new Rect((int) mRect.left, (int) mRect.top, (int) mRect.right,(int) mRect.bottom));
//                mCanvas = mHolder.lockCanvas();
                if (mCanvas != null) {
                    draw();
                    mScanPaint.setShader(new RadialGradient(oval.centerX(), oval.centerY(), oval.width() / 2,
                            Color.argb(200, 0, 255, 0), Color.argb(0, 0, 255, 0),
                            Shader.TileMode.REPEAT));
                    mCanvas.drawOval(oval, mScanPaint);
                    mHolder.unlockCanvasAndPost(mCanvas);
                }
            }
        });
    }

    /**
     * 绘制背景
     */
    private void drawBackground() {
        // 绘制矩形以及边角,因为双缓冲，所以绘制两次背景
        for (int i = 0;i < 2;i++) {
            mCanvas = mHolder.lockCanvas();
            clearCanvas(mCanvas);
            // 绘制整个Canvas的背景色
            mCanvas.drawARGB(150, 0, 0, 0);
            if (mShowTip) {
                drawTipText();
            }
            mHolder.unlockCanvasAndPost(mCanvas);
        }
    }

    private void drawTipText() {
        textBounds = new Rect();
        mTextPaint.getTextBounds(mTipText, 0, mTipText.length(), textBounds);
        // 绘制时bug，y为baseline坐标
        mCanvas.drawText(mTipText, mRect.centerX() ,mRect.bottom + textMarginRect + textBounds.height(), mTextPaint);
    }

    private void sweepTipText() {
        // 由于精度转换，可能边框显示未擦除
        Rect rect = new Rect((int)(mRect.centerX() - textBounds.width() / 2.0f),(int)(mRect.bottom + textMarginRect),
                (int)(mRect.centerX() + textBounds.width() / 2.0f),(int)(mRect.bottom + textMarginRect + textBounds.height() + 5));
        for (int i = 0;i < 2;i++) {
            mCanvas = mHolder.lockCanvas(rect);
            mCanvas.drawPaint(mRectPaint);
            mCanvas.drawARGB(150, 0, 0, 0);
            mHolder.unlockCanvasAndPost(mCanvas);
        }
    }

    // 清空画布操作,参数为锁定的画布
    private void clearCanvas(Canvas canvas) {
        Paint paint = new Paint();
        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        canvas.drawPaint(paint);
    }

    private void draw() {
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

    public void setPreviewState(PreviewState previewState) {
        if (mPreviewState != previewState) {
            mPreviewState = previewState;
            switch (previewState) {
                case READY:
                    mChangeListener.onReady();
                    break;
                case DETECTING:
                    mChangeListener.onDetecting();
                    break;
                case PAUSE:
                    mChangeListener.onPause();
                    break;
                case COMPLETE:
                    mChangeListener.onComplete();
                    break;
            }
        }
    }

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

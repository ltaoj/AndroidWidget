package cn.ltaoj.widget;

import android.content.Context;
import android.graphics.Rect;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.view.ViewCompat;
import android.support.v4.widget.ViewDragHelper;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

/**
 * Created by ltaoj on 2018/3/5 0:15.
 */

public class SwipeLayout extends FrameLayout {
    private static final String TAG = "SwipeLayout";

    /**
     * CLOSE状态: 前边的布局完全显示
     * SWIPING状态: 前后布局都有显示，但后边布局没有显示完全
     * OPEN状态: 后边布局完全显示
     */
    public enum SwipeState {
        CLOSE,
        SWIPING,
        OPEN
    }

    /**
     * 监听SwipeLayout状态改变接口
     */
    public interface OnSwipeChangeLintener{
        /**
         * SwipeState.CLOSE to SwipeState.SWIPING, but has not SwipeState.OPEN
         * @param swipeLayout
         */
        void onStartOpen(SwipeLayout swipeLayout);

        /**
         * SwipeState.Swiping to SwipeState.OPEN
         * @param swipeLayout
         */
        void onOpen(SwipeLayout swipeLayout);

        /**
         * SwipeState.OPEN to SwipeState.SWIPING, but has not SwipeState.CLOSE
         * @param swipeLayout
         */
        void onStartClose(SwipeLayout swipeLayout);

        /**
         * SwipeState.SWIPING to SwipeState.CLOSE
         * @param swipeLayout
         */
        void onClose(SwipeLayout swipeLayout);

        /**
         * in SwipeState.SWIPING
         * @param swipeLayout
         */
        void onSwiping(SwipeLayout swipeLayout);
    }

    private SwipeState mSwipState = SwipeState.CLOSE;
    private OnSwipeChangeLintener onSwipeChangeLintener;
    private ViewDragHelper mViewDragHelper;
    private ViewGroup mFrontLayout;
    private ViewGroup mBackLayout;
    private int mWidth;
    private int mHeight;
    private int mRange;

    private ViewDragHelper.Callback callback = new ViewDragHelper.Callback() {

        @Override
        public void onViewPositionChanged(View changedView, int left, int top, int dx, int dy) {
            if (changedView == mFrontLayout) {
                mBackLayout.offsetLeftAndRight(dx);
            } else if (changedView == mBackLayout) {
                mFrontLayout.offsetLeftAndRight(dx);
            }

            dispatchEvent();

            invalidate();
        }

        @Override
        public void onViewCaptured(View capturedChild, int activePointerId) {
            super.onViewCaptured(capturedChild, activePointerId);
        }

        @Override
        public int getViewHorizontalDragRange(View child) {
            return mRange;
        }

        /**
         * 重载父类方法，限制垂直拖动区域为0
         * @param child
         * @return
         */
        @Override
        public int getViewVerticalDragRange(View child) {
            return 0;
        }

        @Override
        public boolean tryCaptureView(View child, int pointerId) {
            return true;
        }

        // 表示每个子View可移动的固定边界
        @Override
        public int clampViewPositionHorizontal(View child, int left, int dx) {
            if (child == mFrontLayout) {
                left = clampFrontLeft(left);
            } else if (child == mBackLayout){
                left = clampBackLeft(left);
            }
            return left;
        }

        /**
         * 重载父类方法, 垂直固定边界为mFrontLayout的上边界
         * @param child
         * @param top
         * @param dy
         * @return
         */
        @Override
        public int clampViewPositionVertical(View child, int top, int dy) {
            return mFrontLayout.getTop();
        }

        @Override
        public void onViewReleased(View releasedChild, float xvel, float yvel) {
            if (mFrontLayout.getLeft() > -(mRange / (mBackLayout.getChildCount() + 1))) {
                close();
            } else if (mFrontLayout.getLeft() < -(mRange / (mBackLayout.getChildCount() + 1))) {
                open();
            }
        }

        private int clampFrontLeft(int left) {
            if (left < -mRange) {
                left = -mRange;
            } else if (left > 0){
                left = 0;
            }
            return left;
        }

        private int clampBackLeft(int left) {
            if (left < mWidth - mRange) {
                left = mWidth - mRange;
            } else if (left > mWidth){
                left = mWidth;
            }
            return left;
        }
    };

    public SwipeLayout(@NonNull Context context) {
        this(context, null);
    }

    public SwipeLayout(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SwipeLayout(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public SwipeLayout(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        // SwipeLayout沒有另外添加Attrs
        mViewDragHelper = ViewDragHelper.create(this, 1.0f, callback);
    }

    /**
     * I guess this method should execute next constructor
     */
    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        int childCount = getChildCount();
        // 必須有兩個及以上子View
        if (childCount < 2) {
            throw new IllegalStateException("You must have 2 children at least!");
        }

        if (getChildAt(0) == null || !(getChildAt(0) instanceof ViewGroup) ||
                getChildAt(1) == null || !(getChildAt(1) instanceof ViewGroup)) {
            throw new IllegalArgumentException("your children must be instance of ViewGroup!");
        }

        // 后边菜单
        mBackLayout = (ViewGroup) getChildAt(0);
        // 前置条目
        mFrontLayout = (ViewGroup) getChildAt(1);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        mWidth = w;
        mHeight = h;
        // 可拖动范围
        mRange = mBackLayout.getMeasuredWidth();
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);

        layoutInit(false);
    }

    private void layoutInit(boolean isOpen) {
        Rect frontRect = computeFrontRect(isOpen);

        mFrontLayout.layout(frontRect.left, frontRect.top, frontRect.right, frontRect.bottom);

        Rect backRect = computeBackRect(frontRect);
        mBackLayout.layout(backRect.left, backRect.top, backRect.right, backRect.bottom);

        // 將控件前置
        bringChildToFront(mFrontLayout);
    }

    private Rect computeFrontRect(boolean isOpen) {
        int left = 0;

        if (isOpen) {
            left = -mRange;
        } else {
            left = 0;
        }

        return new Rect(left, 0, left + mWidth, mHeight);
    }

    private Rect computeBackRect(Rect frontRect) {
        return new Rect(frontRect.right, frontRect.top, frontRect.right + mRange, frontRect.bottom);
    }

    @Override
    public boolean onInterceptHoverEvent(MotionEvent event) {
        return false;
    }

    // 是否截断触摸事件
    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        return mViewDragHelper.shouldInterceptTouchEvent(ev);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
//        int action = event.getAction();
//        switch (action) {
//            case MotionEvent.ACTION_UP:
//                if (mSwipState == SwipeState.CLOSE || mSwipState == SwipeState.OPEN) {
//                    return false;
//                }
//                break;
//        }
        mViewDragHelper.processTouchEvent(event);
        return true;
    }

    @Override
    public void computeScroll() {
        super.computeScroll();
        if (mViewDragHelper.continueSettling(true)) {
            ViewCompat.postInvalidateOnAnimation(this);
        }
    }

    // 当View的位置改变后，View会有状态变化，根据状态变化来分发事件，回调接口
    private void dispatchEvent() {
        SwipeState preState = mSwipState;
        mSwipState = updateState();

        if (onSwipeChangeLintener != null) {
            onSwipeChangeLintener.onSwiping(this);
            if (mSwipState != preState) {
                if (mSwipState == SwipeState.CLOSE) {
                    onSwipeChangeLintener.onClose(this);
                } else if (mSwipState == SwipeState.OPEN) {
                    onSwipeChangeLintener.onOpen(this);
                } else if (preState == SwipeState.CLOSE) {
                    onSwipeChangeLintener.onStartOpen(this);
                } else if (preState == SwipeState.OPEN) {
                    onSwipeChangeLintener.onStartClose(this);
                }
            }
        }
    }

    private SwipeState updateState() {
        if (mFrontLayout.getLeft() == -mRange) {
            return SwipeState.OPEN;
        } else if (mFrontLayout.getLeft() == 0) {
            return SwipeState.CLOSE;
        }
        return SwipeState.SWIPING;
    }

    public void open(boolean isSmooth) {
        if (isSmooth) {
            int finalLeft = -mRange;
            // 使用smoothSlideViewTo方式时注意第一个参数传入的应该为子View，如果传入this指针那么不会起作用！！！
            boolean b = mViewDragHelper.smoothSlideViewTo(mFrontLayout, finalLeft, mFrontLayout.getTop());
            if (b) {
                ViewCompat.postInvalidateOnAnimation(this);
            }
        } else {
            layoutInit(true);
        }
    }

    public void open() {
        open(true);
    }

    public void close(boolean isSmooth) {
        if (isSmooth) {
            int finalLeft = 0;
            boolean b = mViewDragHelper.smoothSlideViewTo(mFrontLayout, finalLeft, mFrontLayout.getTop());
            if (b) {
                ViewCompat.postInvalidateOnAnimation(this);
            }
        } else {
            layoutInit(false);
        }
    }

    public void close() {
        close(true);
    }

    public void setOnSwipeChangeLintener(OnSwipeChangeLintener onSwipeChangeLintener) {
        this.onSwipeChangeLintener = onSwipeChangeLintener;
    }

    public SwipeState getSwipState() {
        return mSwipState;
    }
}

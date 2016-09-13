package com.scrollablelayout;


import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.support.v4.view.ViewPager;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.LinearLayout;
import android.widget.Scroller;

/**
 * Created by cpoopc(303727604@qq.com) on 2015-02-10.
 */
public class ScrollableLayout extends LinearLayout {

    private Context context;
    private Scroller mScroller;
    private float mDownX;
    private float mDownY;
    private float mLastX;
    private float mLastY;
    private VelocityTracker mVelocityTracker;
    private int mTouchSlop;
    private int mMinimumVelocity;
    private int mMaximumVelocity;

    private boolean mIsHorizontalScrolling;
    private float x_down;
    private float y_down;
    private float x_move;
    private float y_move;
    private float moveDistanceX;
    private float moveDistanceY;

    private View mHeadView;
    private ViewPager childViewPager;

    private DIRECTION mDirection;
    private int mHeadHeight;
    private int mScrollY;
    private int sysVersion;
    private boolean flag1, flag2;
    private int mLastScrollerY;
    private boolean mDisallowIntercept;

    private int minY = 0;
    private int maxY = 0;

    private int mCurY;
    private boolean isClickHead;
    private int mScrollMinY = 10;

    enum DIRECTION {
        UP,
        DOWN
    }

    public interface OnScrollListener {
        void onScroll(int currentY, int maxY);
    }

    private OnScrollListener onScrollListener;

    public void setOnScrollListener(OnScrollListener onScrollListener) {
        this.onScrollListener = onScrollListener;
    }

    private ScrollableHelper mHelper;

    public ScrollableHelper getHelper() {
        return mHelper;
    }

    public ScrollableLayout(Context context) { this(context, null); }

    public ScrollableLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ScrollableLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        this.context = context;
        //帮助类
        // TODO: 2016/9/13
        mHelper = new ScrollableHelper();
        //滑动辅助类，用于顺滑滑动
        mScroller = new Scroller(context);
        //view配置类，获取一些view基本属性
        final ViewConfiguration configuration = ViewConfiguration.get(context);
        //获取view最小滑动
        mTouchSlop = configuration.getScaledTouchSlop();
        //滑动最小速度值
        mMinimumVelocity = configuration.getScaledMinimumFlingVelocity();
        //滑动最大速度值
        mMaximumVelocity = configuration.getScaledMaximumFlingVelocity();
        sysVersion = Build.VERSION.SDK_INT;
        setOrientation(VERTICAL);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        //获取第一个childview 为headview
        mHeadView = getChildAt(0);
        if(mHeadView != null){//headview存在
            //测量mHeadView 宽度为this能给的最大，高度为mHeadView的高度
            measureChildWithMargins(mHeadView, widthMeasureSpec, 0, MeasureSpec.UNSPECIFIED, 0);
            //获取mHeadView的测量高度,存于maxY
            maxY = mHeadView.getMeasuredHeight();
            //获取mHeadView的测量高度  存于mHeadHeight
            mHeadHeight = mHeadView.getMeasuredHeight();
        }
        // TODO: 2016/9/13  目的？？？ 
        //测量this的大小，宽为该view的宽，高为this的测量高度+mHeadView测量高度
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(heightMeasureSpec) + maxY, MeasureSpec.EXACTLY));
    }

    @Override
    protected void onFinishInflate() {
        //mHeadView满足条件设置可点击
        if (mHeadView != null && !mHeadView.isClickable()) {
            mHeadView.setClickable(true);
        }
        int childCount = getChildCount();
        //遍历子view，判断是否存在viewpager
        for (int i = 0; i < childCount; i++) {
            View childAt = getChildAt(i);
            if (childAt != null && childAt instanceof ViewPager) {
                childViewPager = (ViewPager) childAt;
            }
        }
        super.onFinishInflate();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        //存储当前点击坐标X
        float currentX = ev.getX();
        //存储当前点击坐标Y
        float currentY = ev.getY();
        //增量Y
        float deltaY;
        //移动X
        int shiftX;
        //移动Y
        int shiftY;
        //mDownX 首次为0
        shiftX = (int) Math.abs(currentX - mDownX);
        shiftY = (int) Math.abs(currentY - mDownY);
        switch (ev.getAction()) {
            case MotionEvent.ACTION_DOWN:
                //不允许事件拦截
                mDisallowIntercept = false;
                //不允许横向滑动
                mIsHorizontalScrolling = false;
                //获取屏幕坐标系X
                x_down = ev.getRawX();
                //获取屏幕坐标系Y
                y_down = ev.getRawY();
                flag1 = true;
                flag2 = true;
                //记录本次按下坐标位置，用于计算shift值
                mDownX = currentX;
                //记录本次按下坐标位置，用于计算shift值
                mDownY = currentY;

                mLastX = currentX;
                mLastY = currentY;
                //获取view（this）的滑动距离
                mScrollY = getScrollY();
                //判断当前按下位置是否在mHeadView范围内
                checkIsClickHead((int) currentY, mHeadHeight, getScrollY());
                //初始化速度获取器
                initOrResetVelocityTracker();
                //给速度计算器添加一个事件，用于计算
                mVelocityTracker.addMovement(ev);
                //强制结束当前滑动
                mScroller.forceFinished(true);
                break;
            case MotionEvent.ACTION_MOVE:
                if (mDisallowIntercept) {
                    break;
                }
               //初始化速度计算器如果未初始化过
                initVelocityTrackerIfNotExists();
                //给速度计算器添加n个事件，用于计算
                mVelocityTracker.addMovement(ev);
                //计算Y偏移量，按下位置-当前位置
                deltaY = mLastY - currentY;
                if (flag1) {
                    if (shiftX > mTouchSlop && shiftX > shiftY) {
                        //如果是横向滑动
                        flag1 = false;
                        flag2 = false;
                    } else if (shiftY > mTouchSlop && shiftY > shiftX) {
                        //如果是竖向滑动
                        flag1 = false;
                        flag2 = true;
                    }
                }

                //如果当前滑动距离不等于mheadview的测量高度或者可滑动的子view在顶部,isSticked()足够
                if (flag2 && shiftY > mTouchSlop && shiftY > shiftX && (!isSticked() || mHelper.isTop())) {
                    if (childViewPager != null) {
                        childViewPager.requestDisallowInterceptTouchEvent(true);
                    }
                    scrollBy(0, (int) (deltaY + 0.5));
                }
                mLastX = currentX;
                mLastY = currentY;
                x_move = ev.getRawX();
                y_move = ev.getRawY();
                moveDistanceX = (int) (x_move - x_down);
                moveDistanceY = (int) (y_move - y_down);
                if (Math.abs(moveDistanceY) > mScrollMinY && (Math.abs(moveDistanceY) * 0.1 > Math.abs(moveDistanceX))) {
                    mIsHorizontalScrolling = false;
                } else {
                    mIsHorizontalScrolling = true;
                }
                break;
            case MotionEvent.ACTION_UP:
                if (flag2 && shiftY > shiftX && shiftY > mTouchSlop) {
                    mVelocityTracker.computeCurrentVelocity(1000, mMaximumVelocity);
                    float yVelocity = -mVelocityTracker.getYVelocity();
                    if (Math.abs(yVelocity) > mMinimumVelocity) {
                        mDirection = yVelocity > 0 ? DIRECTION.UP : DIRECTION.DOWN;
                        if (mDirection == DIRECTION.UP && isSticked()) {
                        } else {
                            mScroller.fling(0, getScrollY(), 0, (int) yVelocity, 0, 0, -Integer.MAX_VALUE, Integer.MAX_VALUE);
                            mScroller.computeScrollOffset();
                            mLastScrollerY = getScrollY();
                            invalidate();
                        }
                    }
                    if (isClickHead || !isSticked()) {
                        int action = ev.getAction();
                        ev.setAction(MotionEvent.ACTION_CANCEL);
                        boolean dd = super.dispatchTouchEvent(ev);
                        ev.setAction(action);
                        return dd;
                    }
                }
                break;
            case MotionEvent.ACTION_CANCEL:
                if (flag2 && isClickHead && (shiftX > mTouchSlop || shiftY > mTouchSlop)) {
                    int action = ev.getAction();
                    ev.setAction(MotionEvent.ACTION_CANCEL);
                    boolean dd = super.dispatchTouchEvent(ev);
                    ev.setAction(action);
                    return dd;
                }
                break;
            default:
                break;
        }
        super.dispatchTouchEvent(ev);
        return true;
    }

    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
    private int getScrollerVelocity(int distance, int duration) {
        if (mScroller == null) {
            return 0;
        } else if (sysVersion >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            return (int) mScroller.getCurrVelocity();
        } else {
            return distance / duration;
        }
    }

    @Override
    public void computeScroll() {
        if (mScroller.computeScrollOffset()) {
            final int currY = mScroller.getCurrY();
            if (mDirection == DIRECTION.UP) {
                if (isSticked()) {
                    int distance = mScroller.getFinalY() - currY;
                    int duration = calcDuration(mScroller.getDuration(), mScroller.timePassed());
                    mHelper.smoothScrollBy(getScrollerVelocity(distance, duration), distance, duration);
                    mScroller.forceFinished(true);
                    return;
                } else {
                    scrollTo(0, currY);
                }
            } else {
                if (mHelper.isTop()) {
                    int deltaY = (currY - mLastScrollerY);
                    int toY = getScrollY() + deltaY;
                    scrollTo(0, toY);
                    if (mCurY <= minY) {
                        mScroller.forceFinished(true);
                        return;
                    }
                }
                invalidate();
            }
            mLastScrollerY = currY;
        }
    }

    @Override
    public void scrollBy(int x, int y) {
        int scrollY = getScrollY();
        int toY = scrollY + y;
        if (toY >= maxY) {
            toY = maxY;
        } else if (toY <= minY) {
            toY = minY;
        }
        y = toY - scrollY;
        super.scrollBy(x, y);
    }

    @Override
    public void scrollTo(int x, int y) {
        if (y >= maxY) {
            y = maxY;
        } else if (y <= minY) {
            y = minY;
        }
        mCurY = y;
        if (onScrollListener != null) {
            onScrollListener.onScroll(y, maxY);
        }
        super.scrollTo(x, y);
    }

    private void initOrResetVelocityTracker() {
        if (mVelocityTracker == null) {
            mVelocityTracker = VelocityTracker.obtain();
        } else {
            mVelocityTracker.clear();
        }
    }

    private void initVelocityTrackerIfNotExists() {
        if (mVelocityTracker == null) {
            mVelocityTracker = VelocityTracker.obtain();
        }
    }

    private void recycleVelocityTracker() {
        if (mVelocityTracker != null) {
            mVelocityTracker.recycle();
            mVelocityTracker = null;
        }
    }

    private void checkIsClickHead(int downY, int headHeight, int scrollY) {
        isClickHead = downY + scrollY <= headHeight;
    }

    private int calcDuration(int duration, int timepass) {
        return duration - timepass;
    }

    public void requestScrollableLayoutDisallowInterceptTouchEvent(boolean disallowIntercept) {
        super.requestDisallowInterceptTouchEvent(disallowIntercept);
        mDisallowIntercept = disallowIntercept;
    }

    public boolean isSticked() {
        return mCurY == maxY;
    }

    public int getMaxY() {
        return maxY;
    }

    public void setScrollMinY(int y) {
        mScrollMinY = y;
    }

    public boolean isCanPullToRefresh() {
        if (getScrollY() <= 0 && mHelper.isTop() && !mIsHorizontalScrolling) {
            return true;
        }
        return false;
    }
}

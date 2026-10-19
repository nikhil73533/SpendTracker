package com.example.spendtracker.ui.charts;

import android.content.Context;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.view.ViewParent;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.coordinatorlayout.widget.CoordinatorLayout;

public class SwipeableCoordinatorLayout extends CoordinatorLayout {

    public interface OnSwipeListener {
        void onSwipeLeft();
        void onSwipeRight();
    }

    private OnSwipeListener swipeListener;
    private float downX;
    private float downY;
    private boolean isHorizontalSwipe;

    private int touchSlop;
    private int minimumSwipeDistance;

    public SwipeableCoordinatorLayout(@NonNull Context context) {
        super(context);
        init(context);
    }

    public SwipeableCoordinatorLayout(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public SwipeableCoordinatorLayout(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        ViewConfiguration vc = ViewConfiguration.get(context);
        touchSlop = vc.getScaledTouchSlop();
        minimumSwipeDistance = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 40, context.getResources().getDisplayMetrics());
    }

    public void setOnSwipeListener(OnSwipeListener listener) {
        this.swipeListener = listener;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        int action = ev.getActionMasked();
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                downX = ev.getX();
                downY = ev.getY();
                isHorizontalSwipe = false;
                break;

            case MotionEvent.ACTION_MOVE:
                if (!isHorizontalSwipe) {
                    float dx = ev.getX() - downX;
                    float dy = ev.getY() - downY;
                    float absDx = Math.abs(dx);
                    float absDy = Math.abs(dy);

                    if (absDx > touchSlop && absDx > absDy * 1.2f) {
                        isHorizontalSwipe = true;
                        ViewParent parent = getParent();
                        if (parent != null) {
                            parent.requestDisallowInterceptTouchEvent(true);
                        }
                        return true;
                    }
                }
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                isHorizontalSwipe = false;
                break;
        }

        return isHorizontalSwipe || super.onInterceptTouchEvent(ev);
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        if (isHorizontalSwipe) {
            if (action == MotionEvent.ACTION_UP) {
                performClick();
                float dx = event.getX() - downX;
                float absDx = Math.abs(dx);
                if (absDx >= minimumSwipeDistance && swipeListener != null) {
                    if (dx < 0) {
                        swipeListener.onSwipeLeft();
                    } else {
                        swipeListener.onSwipeRight();
                    }
                }
                isHorizontalSwipe = false;
                return true;
            } else if (action == MotionEvent.ACTION_CANCEL) {
                isHorizontalSwipe = false;
                return true;
            } else if (action == MotionEvent.ACTION_MOVE) {
                return true;
            }
        }
        return super.onTouchEvent(event);
    }
}

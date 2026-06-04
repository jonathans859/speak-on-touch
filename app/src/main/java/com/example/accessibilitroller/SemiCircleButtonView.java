package com.example.accessibilitroller;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;

public class SemiCircleButtonView extends View {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean snapLeft = false;

    public SemiCircleButtonView(Context context) {
        super(context);
        init(context);
    }

    public SemiCircleButtonView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        TypedValue tv = new TypedValue();
        boolean resolved = context.getTheme().resolveAttribute(
                com.google.android.material.R.attr.colorPrimary, tv, true);
        if (resolved) {
            paint.setColor(tv.data);
        } else {
            paint.setColor(context.getColor(R.color.colorPrimary));
        }
        paint.setStyle(Paint.Style.FILL);
    }

    public void setSnapLeft(boolean left) {
        snapLeft = left;
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int w = dp(28);
        int h = dp(56);
        setMeasuredDimension(w, h);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth();
        float h = getHeight();
        // snapLeft: flat side on the left, arc on the right → right half of a circle
        // snapRight: flat side on the right, arc on the left → left half of a circle
        RectF oval = snapLeft
                ? new RectF(-w, 0, w, h)       // center of circle is at left edge
                : new RectF(0, 0, w * 2, h);   // center of circle is at right edge
        float startAngle = snapLeft ? -90 : 90;
        canvas.drawArc(oval, startAngle, 180, true, paint);
    }

    private int dp(float dp) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp,
                getResources().getDisplayMetrics());
    }
}

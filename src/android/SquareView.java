package com.lnsoft.cordovaPlugins;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;

/**
 * Created by wyy on 2018/10/11.
 */

public class SquareView extends View {

    private int width;
    private int height;
    private int top;
    private int left;
    private int right;
    private int bottom;

    public SquareView(Context context) {
        super(context);
    }

    public SquareView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public SquareView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }


    public void setWidth(int width) {
        this.width = width;
        invalidate();
    }


    public void setHeight(int height) {
        this.height = height;
        invalidate();
    }

    public Rect getRect() {
        return new Rect(left, top, right, bottom);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (width == 0 || height == 0) {
            return;
        }
        Paint paint = new Paint();
        paint.setStrokeWidth(5);
        paint.setColor(Color.BLUE);
        paint.setStyle(Paint.Style.STROKE);

        top = height / 4;
        bottom = height * 3 / 4;
        left = width / 4;
        right = width * 3 / 4;

        Rect rect = new Rect(left, top, right, bottom);
        canvas.drawRect(rect, paint);
    }
}

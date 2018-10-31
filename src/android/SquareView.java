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
        int root = Math.min(width, height);
        int sizeLength = root *5/8;
        Paint paint = new Paint();
        paint.setStrokeWidth(5);
        paint.setColor(Color.WHITE);
        paint.setStyle(Paint.Style.STROKE);

        top = height / 2 - sizeLength / 2;
        bottom = height / 2 + sizeLength / 2;
        left = width / 2 - sizeLength / 2;
        right = width / 2 + sizeLength / 2;

        int lineLength = sizeLength / 10;
        //四个角，八条短线
        //pts数组每四个数代表一条线段
        float[] pts = new float[]{
                left, top, left + lineLength, top,//上边左线段
                right - lineLength, top, right, top,//上边右线段
                right, top, right, top + lineLength,//右边上线段
                right, bottom - lineLength, right, bottom,//右边下线段
                right, bottom, right - lineLength, bottom,//下边右线段
                left + lineLength, bottom, left, bottom,//下边左线段
                left, bottom, left, bottom - lineLength,//左边下线段
                left, top + lineLength, left, top};//左边上线段
        canvas.drawLines(pts, paint);
    }
}

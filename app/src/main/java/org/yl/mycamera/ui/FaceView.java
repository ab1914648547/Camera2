package org.yl.mycamera.ui;

import static androidx.constraintlayout.motion.utils.Oscillator.TAG;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.hardware.camera2.params.Face;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;

import androidx.annotation.Nullable;

import java.util.ArrayList;

/**
 * @author yl
 */
public class FaceView extends View {

    private Paint mPaint;

    private String mColor = "#42ed45";

    private ArrayList<RectF> mFaces = null;

    public FaceView(Context context) {
        super(context);
        init();
    }

    public FaceView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public FaceView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

   private void init(){
        mPaint = new Paint();
        mPaint.setColor(Color.parseColor(mColor));
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeWidth(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1f, getContext().getResources().getDisplayMetrics()));
        mPaint.setAntiAlias(true);
   }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (mFaces != null){
            for (RectF face: mFaces) {
                canvas.drawRect(face, mPaint);
            }
        }
    }

    public void setFaces(ArrayList<RectF> faces){
        if (faces != null){
            this.mFaces = faces;
            postInvalidate();
            invalidate();
        }
    }
}

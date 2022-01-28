package org.yl.mycamera.util;

import android.graphics.RectF;
import android.hardware.camera2.params.Face;

import java.util.ArrayList;

/**
 * @author yl
 */
public interface FaceDetectListener {
    void onFaceDetect(Face[] faces, ArrayList<RectF> faceRect);
}

package org.yl.mycamera.fragment;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.graphics.drawable.Drawable;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.Face;
import android.hardware.camera2.params.MeteringRectangle;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.util.ArraySet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.PopupWindow;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import com.bumptech.glide.Glide;

import org.yl.mycamera.R;
import org.yl.mycamera.functions.FlashLight;
import org.yl.mycamera.parameters.CameraParameter;
import org.yl.mycamera.store.ImageSaver;
import org.yl.mycamera.store.UriInfo;
import org.yl.mycamera.ui.AutoFitTextureView;
import org.yl.mycamera.ui.CreatePopWin;
import org.yl.mycamera.ui.FaceView;
import org.yl.mycamera.util.FaceDetectListener;
import org.yl.mycamera.util.FontDisplayUtil;

import java.lang.reflect.Array;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;


public class CameraFragment extends AppCompatActivity implements View.OnClickListener, FaceDetectListener {

    private static final String TAG = "CameraTwo";

    private static final int MSG = 0x123;

    /**
     * 摄像头状态:显示摄像头预览。
     */
    private static final int STATE_PREVIEW = 0;

    /**
     * 相机状态:等待锁定焦点。
     */
    private static final int STATE_WAITING_LOCK = 1;

    /**
     * 相机状态:等待曝光为预捕捉状态。
     */
    private static final int STATE_WAITING_PRECAPTURE = 2;

    /**
     * 相机状态:等待曝光状态不再是预捕捉状态。
     */
    private static final int STATE_WAITING_NON_PRECAPTURE = 3;

    /**
     * 相机状态:拍照。
     */
    private static final int STATE_PICTURE_TAKEN = 4;

    /**
     * 我们需要旋转确定的方向来匹配摄像机。
     */
    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();

    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    /**
     * 实际的相机定位。
     */
    private int mSensorOrientation;
    private AutoFitTextureView mTextureView;
    private CameraDevice mCameraDevice;

    /**
     * 标记预览的大小。
     */
    private Size mPreviewSize, mCaptureSize;
    private ImageView mPreviewImageView;

    /**
     * The {@link CaptureRequest} for controlling.
     */
    private CaptureRequest mPreviewRequest;

    /**
     * Values to operate camera.
     */
    private String mCameraId = "0";
    private CameraManager mCameraManager;
    private CameraCaptureSession mCaptureSession;
    private CaptureRequest.Builder mPreviewRequestBuilder;

    /**
     * 来标记flash的状态。
     */
    private static int mFlashStatus = CameraParameter.FLASHON;
    private FlashLight mFlash;

    /**
     * 标记当前比率。
     */
    private static double mCurrentRatio = CameraParameter.FOURTOTHREE;

    /**
     * 标记目标的uri。
     */
    private Uri mImageUri;
    private ImageReader mImageReader;

    /**
     * 获取Uri的接口。
     */
    private UriInfo mUriCallBack = new UriInfo() {
        @Override
        public void UriInfo(Uri uri) {
            mImageUri = uri;
        }
    };

    /**
     * 显示缩略图的线程。
     */
    private Handler mUiHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(@NonNull Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case MSG:
                    Bitmap bitmap = (Bitmap) msg.obj;
                    Log.e(TAG, "可以显示位图。");
                    Glide.with(CameraFragment.this).load(bitmap).error(R.drawable.ic_none).circleCrop().into(mPreviewImageView);
                    mPreviewImageView.setVisibility(View.VISIBLE);
                    break;
            }
        }
    };

    /**
     * Some parameters of {@link PopupWindow}.
     */
    private CreatePopWin mCreatePopWin;
    private ImageView mFlashlight;
    private TextView mScale;
    private ImageView mSettings;
    private PopupWindow mFlashPop;
    private PopupWindow mScalePop;

    /**
     * 用于预览的线程。
     */
    private Handler mHandler;
    private HandlerThread mHandlerThread;

    /**
     * 当前用于拍照的相机状态。
     */
    private int mCurrentState = STATE_PREVIEW;

    /**
     *保存图片的线程。
     */
    private Handler mBackgroundHandler;
    private HandlerThread mBackgroundHandlerThread;



    private RelativeLayout mRelArea;

    private ArrayList<RectF> mFacesRect = new ArrayList<>();//保存人脸坐标信息

    private FaceView faceView = null;



    /**
     * 将此图片缩放为缩略图。
     */
    private final ImageReader.OnImageAvailableListener mOnImageAvailableListener = new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader reader) {

            Image image = reader.acquireNextImage();
            if (image != null) {
                ByteBuffer byteBuffer = image.getPlanes()[0].getBuffer();//获取该图像的像素平面数组
                final byte[] data = new byte[byteBuffer.remaining()];
                byteBuffer.get(data);
                Activity activity = CameraFragment.this;
                mBackgroundHandler.post(new ImageSaver(activity, data, "image/jpeg", image.getWidth(), image.getHeight(), mUriCallBack));
                Bitmap picture = BitmapFactory.decodeByteArray(data, 0, data.length);
                int width = picture.getWidth();
                int height = picture.getHeight();
                Matrix matrix = new Matrix();
                float scaleWidth = ((float) width / image.getWidth());
                float scaleHeight = ((float) height / image.getHeight());
                matrix.postScale(scaleWidth, scaleHeight);
                if (mCameraId == cameraIdList[0]) {
                    matrix.postRotate(90);
                } else {
                    matrix.postRotate(270);
                }
                Bitmap bitmap = Bitmap.createBitmap(picture, 0, 0, width, height, matrix, true);
                mUiHandler.obtainMessage(MSG, bitmap).sendToTarget();
                image.close();
            }
        }
    };

    /**
     * Monitor the status of {@link TextureView}.
     */
    private final TextureView.SurfaceTextureListener mSurfaceTextureListener
            = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surface, int width, int height) {
            try {
                openCamera(mCurrentRatio);
                Log.e(TAG, "相机准备好");
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surface, int width, int height) {
            Log.e(TAG, "表面尺寸改变了");
        }

        @Override
        public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surface) {
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surface) {

        }
    };

    /**
     * 接收相机的状态。
     */
    private final CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            Log.e(TAG, "Camera can open.");
            mCameraDevice = camera;
            mHandlerThread = new HandlerThread("Camera2");
            mHandlerThread.start();
            mHandler = new Handler(mHandlerThread.getLooper());
            mBackgroundHandlerThread = new HandlerThread("Capture");
            mBackgroundHandlerThread.start();
            mBackgroundHandler = new Handler(mBackgroundHandlerThread.getLooper());
            createCameraPreviewSession();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            Log.e(TAG, "相机已经断开连接。");
            if (null != mCameraDevice) {
                mCameraDevice.close();
                mCameraDevice = null;
            }
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            Log.e(TAG, "摄像机出现了一些错误");
            if (null != mCameraDevice) {
                mCameraDevice.close();
                mCameraDevice = null;
            }
            Activity activity = CameraFragment.this;
            if (activity != null) {
                activity.finish();
            }
        }
    };

    private Rect focusRect;

    private boolean faceSwitch = false;

    private ImageView mFaceSwitch;

    private FaceDetectListener mFaceDetectListener = null;

    private int mFaceDetectMode = CaptureResult.STATISTICS_FACE_DETECT_MODE_OFF;//人脸检测模式

    private Matrix mFaceDetectMatrix = new Matrix();

    int mCameraSensorOrientation = 0;

    private void initFaceDetect(){
        CameraCharacteristics characteristics = null;
        try {
            characteristics = mCameraManager.getCameraCharacteristics(String.valueOf(mCameraId));
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }


        mCameraSensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);

        Integer faceCount = characteristics.get(CameraCharacteristics.STATISTICS_INFO_MAX_FACE_COUNT);

        int[] faceModes = characteristics.get(CameraCharacteristics.STATISTICS_INFO_AVAILABLE_FACE_DETECT_MODES);

        for (int i: faceModes) {
            if (i == CaptureRequest.STATISTICS_FACE_DETECT_MODE_FULL){
                mFaceDetectMode = CaptureRequest.STATISTICS_FACE_DETECT_MODE_FULL;
            }else if (i == (CaptureRequest.STATISTICS_FACE_DETECT_MODE_SIMPLE)){
                mFaceDetectMode = CaptureRequest.STATISTICS_FACE_DETECT_MODE_SIMPLE;
            }else mFaceDetectMode = CaptureRequest.STATISTICS_FACE_DETECT_MODE_OFF;
        }

        if (mFaceDetectMode == CaptureRequest.STATISTICS_FACE_DETECT_MODE_OFF){
            Log.e(TAG, "initFaceDetect: 相机硬件不支持人脸检测 " + mFaceDetectMode);

            return;
        }

        Rect activeArraySizeRect = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);

        float scaledWidth = mPreviewSize.getWidth() / (float) activeArraySizeRect.width();

        float scaledHeight = mPreviewSize.getHeight() / (float) activeArraySizeRect.height();

        boolean mirror = mCameraId == String.valueOf(CameraCharacteristics.LENS_FACING_FRONT) ;

        mFaceDetectMatrix.setRotate((float)mCameraSensorOrientation);

        mFaceDetectMatrix.postScale(mirror ? scaledWidth : scaledHeight, scaledHeight);

        if (exchangeWidthAndHeight(CameraFragment.this.getWindowManager().getDefaultDisplay().getRotation(),
                characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION))){
            mFaceDetectMatrix.postTranslate((float) mPreviewSize.getHeight(), (float) mPreviewSize.getWidth());
        }




    }
    private boolean exchangeWidthAndHeight(int displayRotation, int sensorOrientation){
        boolean exchange = false;

        switch (displayRotation){
            case Surface.ROTATION_0:
            case Surface.ROTATION_180:
                if (sensorOrientation == 90 || sensorOrientation == 270){
                    exchange = true;
                }
                break;
            case Surface.ROTATION_90:
            case Surface.ROTATION_270:
                if (sensorOrientation == 0 || sensorOrientation == 180){
                    exchange = true;
                }
                break;
        }

        return exchange;
    }

    @RequiresApi(api = Build.VERSION_CODES.R)
    private void focusOnTouch(MotionEvent event) {
        focusRect = getFocusRect((int) event.getX(), (int) event.getY());

        mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_REGIONS,
                new MeteringRectangle[]{new MeteringRectangle(focusRect, 1000)});

        mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_REGIONS,
                new MeteringRectangle[]{new MeteringRectangle(focusRect, 1000)});

        mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO);

        mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_START);

        mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER, CameraMetadata.CONTROL_AE_PRECAPTURE_TRIGGER_START);

        mPreviewRequest = mPreviewRequestBuilder.build();

        try {
            mCaptureSession.capture(mPreviewRequest, mCaptureCallback, null);
        } catch (CameraAccessException e) {
            Log.e(TAG, "setRepeatingRequest failed, " + e.getMessage());
        }
    }

    private TextView for_video;

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.fragment_camera);
        mTextureView = (AutoFitTextureView) this.findViewById(R.id.texture);
        mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);
        ImageView imageView = this.findViewById(R.id.capture);
        mFlashlight = this.findViewById(R.id.flash_light);
        mScale = this.findViewById(R.id.scale);
        mSettings = this.findViewById(R.id.settings);
        mPreviewImageView = this.findViewById(R.id.image_save);
        mFaceSwitch = this.findViewById(R.id.face_switch);
        for_video = this.findViewById(R.id.for_video);
        for_video.setOnClickListener(this);
        ImageView cameraRotation = this.findViewById(R.id.camera_rotation);
        cameraRotation.setOnClickListener(this);
        imageView.setOnClickListener(this);
        mPreviewImageView.setOnClickListener(this);
        mFlashlight.setOnClickListener(this);
        mScale.setOnClickListener(this);
        mSettings.setOnClickListener(this);
        mFaceSwitch.setOnClickListener(this);

        faceView = this.findViewById(R.id.faceView);

        mPreviewSize = new Size(1080,1440);

        this.setFaceDetectListener(this);

//        CameraCharacteristics.Key<Size> sensorInfoPixelArraySize = CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE;
        mRelArea = this.findViewById(R.id.relArea);

        mTextureView.setOnTouchListener(new View.OnTouchListener() {

            @SuppressLint("ClickableViewAccessibility")
            @RequiresApi(api = Build.VERSION_CODES.R)
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                int action = event.getAction();
                switch (action){
                    case (MotionEvent.ACTION_DOWN):
                        break;
                    case MotionEvent.ACTION_UP:

                        mRelArea.setX(event.getX()-45);
                        mRelArea.setY(event.getY()+100);

                        mRelArea.setVisibility(View.VISIBLE);
                        new Handler().postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                mRelArea.setVisibility(View.GONE);
                            }
                        }, 1000);
                        focusOnTouch(event);
                        break;
                }
                return true;
            }
        });
    }

    /**
     * 获取点击区域
     * @param x：手指触摸点x坐标
     * @param y: 手指触摸点y坐标
     */
    private Rect getFocusRect(int x, int y){

        int screenW = FontDisplayUtil.getScreenWidth(this);//获取屏幕长度
        int screenH = FontDisplayUtil.getScreenHeight(this);//获取屏幕宽度

        //因为获取的SCALER_CROP_REGION是宽大于高的，也就是默认横屏模式，竖屏模式需要对调width和height
        int realPreviewWidth = mPreviewSize.getWidth();
        int realPreviewHeight = mPreviewSize.getHeight();

        //根据预览像素与拍照最大像素的比例，调整手指点击的对焦区域的位置
        float focusX = (float) realPreviewWidth / screenW * x;
        float focusY = (float) realPreviewHeight / screenH * y;

        //获取SCALER_CROP_REGION，也就是拍照最大像素的Rect
        Rect rect = mPreviewRequestBuilder.get(CaptureRequest.SCALER_CROP_REGION);

        //计算出摄像头剪裁区域偏移量
        int cutDx = (rect.height() - mPreviewSize.getHeight()) / 2;

        //我们默认使用10dp的大小，也就是默认的对焦区域长宽是10dp，这个数值可以根据需要调节
        int width = FontDisplayUtil.dip2px(this, 10f);
        int height = FontDisplayUtil.dip2px(this, 10f);

        Rect rect1 = new Rect((int) focusY, (int) focusX + cutDx, (int) focusY + height, (int) focusX + cutDx + width);

        if (rect1.top < 0){
            rect1.top*=-1;
        }
        if (rect1.right < 0){
            rect1.right *= -1;
        }
        if (rect1.bottom < 0){
            rect1.bottom *= -1;
        }

        if (rect1.height() < 0){
            int t = rect1.bottom;
            rect1.bottom = rect1.top;
            rect1.top = t;
        }

        //返回最终对焦区域Rect
        return rect1;

    }


    @Override
    public void onPause() {
        closeCamera();
        stopHandlerThread();
        closeSession();
        super.onPause();
    }
    @Override
    public void onStop() {
        closeCamera();
        stopHandlerThread();
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        closeCamera();
        stopHandlerThread();
    }

    @Override
    public void onResume() {
        super.onResume();

        new VideoFragment().closeCamera();
        try {
            startHandlerThread();
            if (mTextureView.isAvailable()) {
                openCamera(mCurrentRatio);
            } else {
                mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

    }

    public static CameraFragment newInstance(){
        return new CameraFragment();
    }
    /**
     * Open camera.
     *
     * @param ratio Aspect ratio.
     * @throws CameraAccessException
     */
    private String[] cameraIdList;
    private void openCamera(double ratio) throws CameraAccessException {
        setUpCameraOutputs(ratio);
        forOpenCamera();
    }

    /**
     * The callback of {@link CameraCaptureSession}.
     *
     */
    private CameraCaptureSession.CaptureCallback mCaptureCallback
            = new CameraCaptureSession.CaptureCallback() {
        @RequiresApi(api = Build.VERSION_CODES.R)
        private void process(CaptureResult result) {
            switch (mCurrentState) {
                case STATE_PREVIEW: {
                    // 当相机预览正常工作时，我们没有做什么。
                    break;
                }
                case STATE_WAITING_LOCK: {
                    Integer afState = result.get(CaptureResult.CONTROL_AF_STATE);
                    Log.e(TAG, "process: 722");
                    if (afState == null) {
                        Log.e(TAG, "TATE_WAITING_LOCK");
                        captureStillPicture();
                    } else if (CaptureResult.CONTROL_AF_STATE_INACTIVE == afState ||
                            CaptureRequest.CONTROL_AF_STATE_PASSIVE_SCAN == afState ||
                            CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED == afState ||
                            CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED == afState) {
                        // 在某些设备上，CONTROL_AE_STATE可以为空
                        Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                        if (aeState == null ||
                                aeState == CaptureResult.CONTROL_AE_STATE_CONVERGED) {
                            mCurrentState = STATE_PICTURE_TAKEN;
                            captureStillPicture();
                        } else {
                            runPrecaptureSequence();
                        }
                    }
                    break;
                }
                case STATE_WAITING_PRECAPTURE: {
                    // 在某些设备上，CONTROL_AE_STATE可以为空
                    Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                    if (aeState == null ||
                            aeState == CaptureResult.CONTROL_AE_STATE_PRECAPTURE ||
                            aeState == CaptureRequest.CONTROL_AE_STATE_FLASH_REQUIRED) {
                        mCurrentState = STATE_WAITING_NON_PRECAPTURE;
                    }
                    break;
                }
                case STATE_WAITING_NON_PRECAPTURE: {
                    // 在某些设备上，CONTROL_AE_STATE可以为空
                    Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                    if (aeState == null || aeState != CaptureResult.CONTROL_AE_STATE_PRECAPTURE) {
                        mCurrentState = STATE_PICTURE_TAKEN;
                        captureStillPicture();
                    }
                    break;
                }
            }
        }

        @RequiresApi(api = Build.VERSION_CODES.R)
        @Override
        public void onCaptureProgressed(@NonNull CameraCaptureSession session,
                                        @NonNull CaptureRequest request,
                                        @NonNull CaptureResult partialResult) {
            process(partialResult);
        }

        @RequiresApi(api = Build.VERSION_CODES.R)
        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                       @NonNull CaptureRequest request,
                                       @NonNull TotalCaptureResult result) {

            /**
             * 处理人脸信息
             */
            if (faceSwitch && (mFaceDetectMode != CaptureRequest.STATISTICS_FACE_DETECT_MODE_OFF)) {
                Log.e(TAG, "onCaptureCompleted: 处理人脸信息");
                handleFaces(result);
            }
            process(result);
        }
    };

    /**
     * 运行预捕获序列以捕获静态图像。当我们从{@link #lockFocus()}得到{@link #mCaptureCallback}中的响应时，应该调用这个方法。
     */
    private void runPrecaptureSequence() {
        try {
            // 这是如何告诉相机触发。
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
                    CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START);
            // 告诉#mCaptureCallback等待预捕获序列被设置。
            mCurrentState = STATE_WAITING_PRECAPTURE;



            mCaptureSession.capture(mPreviewRequestBuilder.build(), mCaptureCallback,
                    mHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * Create preview {@link CameraCaptureSession}.
     */
    private void createCameraPreviewSession() {
        try {
            SurfaceTexture mSurfaceTexture = mTextureView.getSurfaceTexture();

            /**
             * 初始化人脸检测相关信息
             */
            if (faceSwitch){

                initFaceDetect();
                Log.e(TAG, "setUpCameraOutputs: 初始化人脸检测相关信息");
            }


            //调整纹理视图的长宽比
            mTextureView.setAspectRatio(mPreviewSize.getWidth(), mPreviewSize.getHeight());

            mSurfaceTexture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());

            //设置一个CaptureRequest。生成器与输出表面。
            mPreviewRequestBuilder
                    = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            Surface surface = new Surface(mSurfaceTexture);
            mPreviewRequestBuilder.addTarget(surface);

            //创建捕获会话
            mCameraDevice.createCaptureSession(Arrays.asList(surface, mImageReader.getSurface()),
                    new CameraCaptureSession.StateCallback() {

                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession session) {
                            if (mCameraDevice == null) {
                                return;
                            } else {
                                mCaptureSession = session;
                                try {
                                    mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                                            CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                                    Log.e(TAG, "onConfigured: "+faceSwitch +" 人脸检测" );

                                    if (faceSwitch && (mFaceDetectMode != CaptureRequest.STATISTICS_FACE_DETECT_MODE_OFF)){//人脸检测
//                                    if (faceSwitch){//人脸检测
                                        mPreviewRequestBuilder.set(CaptureRequest.STATISTICS_FACE_DETECT_MODE,
                                                CameraCharacteristics.STATISTICS_FACE_DETECT_MODE_SIMPLE);
                                        Log.e(TAG, "onConfigured: 人脸检测开始");
                                    }else Log.e(TAG, "onConfigured: 无法开始人脸检测");


                                    //配置AE模式
                                    mFlash = new FlashLight(mPreviewRequestBuilder);
                                    mFlash.startFlashLight(mFlashStatus);
                                    mPreviewRequest = mPreviewRequestBuilder.build();
                                    if (mCaptureSession != null)
                                        mCaptureSession.setRepeatingRequest(mPreviewRequest,
                                                mCaptureCallback, mHandler);
                                } catch (CameraAccessException e) {
                                    e.printStackTrace();
                                }
                            }
                        }

                        @Override
                        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                            Activity activity = CameraFragment.this;
                            Toast.makeText(activity, "Configuration failed!", Toast.LENGTH_SHORT).show();

                        }
                    }, null);

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * Adjust preview size.
     *
     * @param ratio Aspect ratio.
     */
    private void setUpCameraOutputs(double ratio) {
        Activity activity = CameraFragment.this;
        assert activity != null;
        mCameraManager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
        try {
            CameraCharacteristics cameraCharacteristics
                    = mCameraManager.getCameraCharacteristics(String.valueOf(mCameraId));

            //我们不能直接从camerachartraits获得尺寸列表。我们应该首先从SCALER_STREAM_CONFIGURATION_MAP获取StreamConfigurationMap，
            //然后通过StreamConfigurationMap.getOutputSizes()获取大小列表，它要求参数类型为Class。
            //你可以从参数中获得列表。如果它不支持它，它会给你一个null值，你可以通过StreamConfigurationMap.isOutputSupportedFor()估计它
            StreamConfigurationMap streamConfigurationMap
                    = cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            Size[] sizes = streamConfigurationMap.getOutputSizes(ImageFormat.YUV_420_888);

            Log.e(TAG, "setUpCameraOutputs: " + sizes[0]);

            //确定我们是否应该调整方向以匹配实际的方向
            mSensorOrientation = cameraCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);

            //窗口的实际宽度
            DisplayMetrics displayMetrics = getResources().getDisplayMetrics();

            int widthPixels = displayMetrics.widthPixels;
//            mPreviewSize = chooseOptimalSize(sizes, ratio, widthPixels);


            faceView.setLayoutParams(new RelativeLayout.LayoutParams(mTextureView.getWidth(), 540));
            RelativeLayout.LayoutParams layoutParams =
                    new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
            layoutParams.addRule(RelativeLayout.CENTER_IN_PARENT);
            faceView.setLayoutParams(layoutParams);



            //调整宽度和高度匹配实际的宽度和高度
            int orientation = getResources().getConfiguration().orientation;
            if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                mTextureView.setAspectRatio(
                        mPreviewSize.getWidth(), mPreviewSize.getHeight());
            } else {
                mTextureView.setAspectRatio(
                        mPreviewSize.getHeight(), mPreviewSize.getWidth());
            }
            Size[] pictureSizes = streamConfigurationMap.getOutputSizes(ImageFormat.JPEG);
            mCaptureSize = chooseOptimalSize(pictureSizes, ratio, widthPixels);

            //我们通过ImageReader获取图片数据
            mImageReader = ImageReader.newInstance(mCaptureSize.getWidth(), mCaptureSize.getHeight(),
                    ImageFormat.JPEG, 2);
            mImageReader.setOnImageAvailableListener(mOnImageAvailableListener, mHandler);

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * 获得最佳的预览大小。
     *
     * @param choices The list of previewing size.
     * @param ratio   Aspect ratio.
     * @return The optimal size.
     */
    private Size chooseOptimalSize(Size[] choices, double ratio, int windowWidth) {
        List<Size> optimalSize = new ArrayList<>();
        for (int i = 0; i < choices.length; i++) {
            double r = (double) choices[i].getWidth() / (double) choices[i].getHeight();
            if (Math.abs(r - ratio) <= 0.001) {
                optimalSize.add(choices[i]);
            }
        }
        if (optimalSize.size() != 0) {
            return minSize(optimalSize, windowWidth);
        } else {
            return null;
        }
    }

    /**
     * 选择最小尺寸和宽度必须接近窗口的宽度。
     *
     * @param sizes    这些大小与aspect ratio相对应。
     * @param winWidth Window width.
     * @return Minimum size.
     */
    private Size minSize(List<Size> sizes, int winWidth) {
        int minWidth = sizes.get(0).getWidth();
        Size minSize = sizes.get(0);
        for (Size size : sizes) {
            if (size.getWidth() < minWidth && size.getWidth() >= winWidth) {
                minWidth = size.getWidth();
                minSize = size;
            }
        }
        return minSize;
    }

    /**
     * 当我们选择确定的比率时，我们需要调整长宽比。
     *
     * @param ratio The aspect ratio.
     * @throws CameraAccessException
     */
    public void adjustAspectRatio(double ratio) throws CameraAccessException {
        closeSession();
        closeCamera();
        openCamera(ratio);
    }

    /**
     * Open camera.
     */
    private void forOpenCamera() throws CameraAccessException {
        Activity activity = CameraFragment.this;
        mCameraManager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
        cameraIdList = mCameraManager.getCameraIdList();
        try {
            if (ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
            mCameraManager.openCamera(String.valueOf(mCameraId), mStateCallback, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.capture:
                takePicture();
                break;
            case R.id.image_save:
                Intent startGallery = new Intent(Intent.ACTION_VIEW);
                startGallery.setData(mImageUri);
                startGallery.putExtra("camera_album", true);
                startGallery.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(startGallery);
                break;
            case R.id.camera_rotation:
                closeSession();
                closeCamera();
                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            if (mCameraId == cameraIdList[0]){
                                mCameraId = cameraIdList[1];
                            }else {
                                mCameraId = cameraIdList[0];
                            }
                            forOpenCamera();

                        } catch (CameraAccessException e) {
                            e.printStackTrace();
                        }
                    }
                }, 500);


                break;
            case R.id.flash_light:
                if (null != CameraFragment.this.getBaseContext()) {
                    mCreatePopWin = new CreatePopWin(CameraFragment.this.getBaseContext(), mFlashlight);
                    View flashLayout = mCreatePopWin.createPopupWindow(R.layout.flash_popupwindow);
                    mFlashPop = mCreatePopWin.getPopupWindow();
                    flashLayout.setOnClickListener(this);
                    ImageView flashOn = flashLayout.findViewById(R.id.flash_light_select);
                    ImageView flashAuto = flashLayout.findViewById(R.id.flash_auto);
                    ImageView flashOff = flashLayout.findViewById(R.id.flash_close);
                    ImageView flashHigh = flashLayout.findViewById(R.id.flash_highlight);
                    flashOn.setOnClickListener(this);
                    flashAuto.setOnClickListener(this);
                    flashOff.setOnClickListener(this);
                    flashHigh.setOnClickListener(this);
                }
                break;
            case R.id.scale:
                mCreatePopWin = new CreatePopWin(CameraFragment.this.getBaseContext(), mScale);
                View scaleLayout = mCreatePopWin.createPopupWindow(R.layout.scale_popupwindow);
                mScalePop = mCreatePopWin.getPopupWindow();
                TextView tvFourThree = scaleLayout.findViewById(R.id.tv_scale_four_three);
                TextView tvOneOne = scaleLayout.findViewById(R.id.tv_scale_one_one);
                tvFourThree.setOnClickListener(this);
                tvOneOne.setOnClickListener(this);
                break;
            case R.id.settings:
//                mCreatePopWin = new CreatePopWin(CameraFragment.this.getBaseContext(), mSettings);
//                View settingsLayout = mCreatePopWin.createPopupWindow(R.layout.settings_popupwindow);
//                ImageView ivLocation = settingsLayout.findViewById(R.id.iv_location);
//                ImageView ivQRcode = settingsLayout.findViewById(R.id.iv_QR_code);
//                ImageView ivDelayedThree = settingsLayout.findViewById(R.id.iv_delayed_three);
//                ImageView ivDelayedSix = settingsLayout.findViewById(R.id.iv_delayed_six);
//                ivLocation.setOnClickListener(this);
//                ivQRcode.setOnClickListener(this);
//                ivDelayedThree.setOnClickListener(this);
//                ivDelayedSix.setOnClickListener(this);
                break;
            case R.id.flash_light_select:
                mFlashPop.dismiss();
                if (mFlashStatus != CameraParameter.FLASHON) {
                    mFlashStatus = CameraParameter.FLASHON;
                    mFlashlight.setImageResource(R.drawable.ic_flash_light);
                    closeSession();
                    createCameraPreviewSession();
                }
                break;
            case R.id.flash_auto:
                mFlashPop.dismiss();
                if (mFlashStatus != CameraParameter.FLASHAUTO) {
                    mFlashStatus = CameraParameter.FLASHAUTO;
                    mFlashlight.setImageResource(R.drawable.ic_flash_auto);
                    closeSession();
                    createCameraPreviewSession();
                }
                break;
            case R.id.flash_close:
                mFlashPop.dismiss();
                if (mFlashStatus != CameraParameter.FLASHOFF) {
                    mFlashStatus = CameraParameter.FLASHOFF;
                    mFlashlight.setImageResource(R.drawable.ic_flash_close);
                    closeSession();
                    createCameraPreviewSession();
                }
                break;
            case R.id.flash_highlight:
                mFlashPop.dismiss();
                if (mFlashStatus != CameraParameter.FLASHTORCH) {
                    mFlashStatus = CameraParameter.FLASHTORCH;
                    mFlashlight.setImageResource(R.drawable.ic_flash_highlight);
                    closeSession();
                    createCameraPreviewSession();
                }
                break;
            case R.id.tv_scale_four_three:
                mScalePop.dismiss();
                if (mCurrentRatio != CameraParameter.FOURTOTHREE) {
                    mScale.setText("4:3");
                    mCurrentRatio = CameraParameter.FOURTOTHREE;
                    try {
                        adjustAspectRatio(mCurrentRatio);
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }
                break;
            case R.id.tv_scale_one_one:
                mScalePop.dismiss();
                if (mCurrentRatio != CameraParameter.ONETOONE) {
                    mScale.setText("1:1");
                    mCurrentRatio = CameraParameter.ONETOONE;
                    try {
                        adjustAspectRatio(CameraParameter.ONETOONE);
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }
                break;
            case R.id.face_switch:
                faceSwitch = !faceSwitch;
                int ic_face_on = R.drawable.ic_face_on;
                int ic_face_off = R.drawable.ic_face_off;
                @SuppressLint("UseCompatLoadingForDrawables") Drawable face_on = Objects.requireNonNull(this).getDrawable(ic_face_on);
                @SuppressLint("UseCompatLoadingForDrawables") Drawable face_off = this.getDrawable(ic_face_off);
                if (faceSwitch){
                    mFaceSwitch.setImageDrawable(face_on);
                    createCameraPreviewSession();
                }else mFaceSwitch.setImageDrawable(face_off);

                break;

            case R.id.for_video:

                closeSession();
                closeCamera();
                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        Intent intent = new Intent(CameraFragment.this, VideoFragment.class);
                        startActivity(intent);
                        finish();
                    }
                }, 1000);
                break;
            case R.id.iv_location:
                break;
            case R.id.iv_QR_code:
                break;
            case R.id.iv_delayed_three:
                break;
            case R.id.iv_delayed_six:
                break;

        }
    }

    /**
     * Lock the focus before taking a picture.
     */
    private void takePicture() {
        lockFocus();
    }

    /**
     * 在拍照前配置相关参数。
     */
    @RequiresApi(api = Build.VERSION_CODES.R)
    private void captureStillPicture() {
        try {
            final Activity activity = CameraFragment.this;
            if (null == activity || null == mCameraDevice) {
                return;
            }
            final CaptureRequest.Builder captureRequestBuilder
                    = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureRequestBuilder.addTarget(mImageReader.getSurface());
            captureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            mFlash = new FlashLight(captureRequestBuilder);
            mFlash.startFlashLight(mFlashStatus);
            int rotation = activity.getDisplay().getRotation();
            captureRequestBuilder.set(CaptureRequest.JPEG_ORIENTATION, getOrientation(rotation));
            mCaptureSession.stopRepeating();
            mCaptureSession.abortCaptures();
            mCaptureSession.capture(captureRequestBuilder.build(),
                    new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                               @NonNull CaptureRequest request,
                                               @NonNull TotalCaptureResult result) {
                    Log.e(TAG, "图片已被保存!");
                    unlockFocus();
                }
            }, null);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }



    /**
     * 处理人脸信息
     */
    private void handleFaces(TotalCaptureResult result) {

        Activity mActivity = CameraFragment.this;

        Log.e(TAG, "handleFaces: start---------------人脸检测实现");

        Face[] faces = result.get(CaptureResult.STATISTICS_FACES);
        Rect cropRegion = result.get(CaptureResult.SCALER_CROP_REGION);



        mFacesRect.clear();

        if (faces != null){
            for (Face face:faces) {
                Rect bounds = face.getBounds();
                float left = (float) (bounds.left * (2/3.0));
                float top = (float) (bounds.top * (2/3.0));
                float right = (float) (bounds.right * (2/3.0));
                float bottom = (float) (bounds.bottom * (2/3.0));
                RectF rawFaceRect = new RectF((float) left + 230 , (float) top + 1250 , (float) right + 130, (float) bottom+1150);
                mFaceDetectMatrix.mapRect(rawFaceRect);
                RectF resultFaceRect = CameraCharacteristics.LENS_FACING_BACK == CaptureRequest.LENS_FACING_FRONT ?
                        rawFaceRect :
                        new RectF(rawFaceRect.left, rawFaceRect.top - mPreviewSize.getWidth(),
                                rawFaceRect.right, rawFaceRect.bottom - mPreviewSize.getWidth());
                mFacesRect.add(resultFaceRect);
            }
        }

        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                if (faces!=null && faces.length < 10){
//                        new FaceView(this).setFaces(mFacesRect);
                    mFaceDetectListener.onFaceDetect(faces, mFacesRect);
                    Log.e(TAG, "run: 人脸检测实现"+mFacesRect);
                }
            }
        }, 500);
        Log.e(TAG, "handleFaces: 人脸is------------------------------------ "+ faces.length);

    }


    /**
     * Get the appropriate orientation.
     *
     * @param rotation The screen needs to be rotated definite angles.
     * @return Rotation
     */
    private int getOrientation(int rotation) {
        if (mCameraId == cameraIdList[0]) {
            return (ORIENTATIONS.get(rotation) + mSensorOrientation + 270) % 360;
        } else {
            return (ORIENTATIONS.get(rotation) + mSensorOrientation + 90) % 360;
        }
    }

    /**
     * 锁定焦点作为静态图像捕捉的第一步。
     */
    private void lockFocus() {
        try {
            // 这是如何告诉相机锁定焦点。
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,
                    CameraMetadata.CONTROL_AF_TRIGGER_START);
            // 告诉#mCaptureCallback等待锁。
            mCurrentState = STATE_WAITING_LOCK;
            mCaptureSession.capture(mPreviewRequestBuilder.build(), mCaptureCallback,
                    mHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * 解锁的焦点。该方法应在静态图像捕获序列完成时调用。
     */
    private void unlockFocus() {
        try {
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,
                    CameraMetadata.CONTROL_AF_TRIGGER_CANCEL);
            mFlash = new FlashLight(mPreviewRequestBuilder);
            mFlash.startFlashLight(mFlashStatus);
            mCaptureSession.capture(mPreviewRequestBuilder.build(), mCaptureCallback, mHandler);
            mCurrentState = STATE_PREVIEW;
            mCaptureSession.setRepeatingRequest(mPreviewRequest, mCaptureCallback,
                    mHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * Start the preview handler.
     *
     * @throws CameraAccessException
     */
    private void startHandlerThread() throws CameraAccessException {
        mHandlerThread = new HandlerThread("CameraTwo");
        mHandlerThread.start();
        mHandler = new Handler(mHandlerThread.getLooper());

    }

    /**
     * Stop the preview handler.
     */
    private void stopHandlerThread() {
        if (mHandlerThread != null){

            mHandlerThread.quitSafely();
            try {
                mHandlerThread.join();
                mHandlerThread = null;
                mHandler = null;
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Close session.
     */
    private void closeSession() {
        if (null != mCaptureSession) {
            try {
                mCaptureSession.abortCaptures();
                mCaptureSession.stopRepeating();
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
            mCaptureSession.close();
            mCaptureSession = null;
        }
    }

    /**
     * Close camera.
     */
    public void closeCamera() {
        if (null != mCaptureSession) {
            mCaptureSession.close();
            mCaptureSession = null;
        }
        if (null != mCameraDevice) {
            mCameraDevice.close();
            mCameraDevice = null;
        }
    }

    @Override
    public void onFaceDetect(Face[] faces, ArrayList<RectF> faceRect) {
        if (faces != null && faceRect != null){
            faceView.setFaces(faceRect);
        }
    }

    private void setFaceDetectListener(FaceDetectListener listener){
        this.mFaceDetectListener = listener;
    }
}
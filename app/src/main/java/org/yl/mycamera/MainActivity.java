package org.yl.mycamera;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import org.yl.mycamera.fragment.CameraFragment;
import org.yl.mycamera.fragment.VideoFragment;
import org.yl.mycamera.util.Permission;
import org.yl.mycamera.util.PermissionDialog;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    private TextView for_camera;

    private TextView for_video;

    private int mDefaultColor = Color.rgb(189, 189,189);

    private int mCurrentColor = Color.rgb(255, 193,7);

    private final static int CAMERA_CURRENT = 0;
    private final static int VIDEO_CURRENT = 1;

    private int CURRENT_FRAGMENT = CAMERA_CURRENT;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        for_camera = findViewById(R.id.for_camera);
        for_video = findViewById(R.id.for_video);

        for_camera.setOnClickListener(this);
        for_video.setOnClickListener(this);
        for_camera.setTextColor(mCurrentColor);
        setWindowFlag();
        Intent intent = new Intent();
        intent.setClass(this, CameraFragment.class);
        startActivity(intent);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == Permission.REQUEST_CODE) {
            for (int grantResult : grantResults) {
                if (grantResult != PackageManager.PERMISSION_GRANTED) {
                    showPermissionDenyDialog();
                    return;
                }
            }
        }

    }
    private void setWindowFlag() {
        Window window = getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        window.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        WindowManager.LayoutParams params = window.getAttributes();
        params.systemUiVisibility = View.SYSTEM_UI_FLAG_LOW_PROFILE;
        window.setAttributes(params);
    }
    private void showPermissionDenyDialog() {
        PermissionDialog dialog = new PermissionDialog();
        dialog.show(getFragmentManager(), "PermissionDeny");
    }

    @Override
    protected void onStart() {
        super.onStart();
        Permission.checkPermission(this);
    }

    @Override
    public void onClick(View view){
        switch (view.getId()){
            case R.id.for_camera:
                if (CURRENT_FRAGMENT != CAMERA_CURRENT){
                    CURRENT_FRAGMENT = CAMERA_CURRENT;
                    for_camera.setTextColor(mCurrentColor);
                    for_video.setTextColor(mDefaultColor);


                }
                break;
            case R.id.for_video:

                if (CURRENT_FRAGMENT != VIDEO_CURRENT){
                    CURRENT_FRAGMENT = VIDEO_CURRENT;
                    for_video.setTextColor(mCurrentColor);
                    for_camera.setTextColor(mDefaultColor);

                }
                break;
            default:
                break;
        }
    }
    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
}

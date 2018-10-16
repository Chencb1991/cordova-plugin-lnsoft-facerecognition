package com.lnsoft.cordovaPlugins;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.os.Environment;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.wangjunjian.dlib_android.FaceRecognition;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaArgs;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.apache.cordova.engine.SystemWebView;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

/**
 * This class echoes a string called from JavaScript.
 */
public class FaceDetectPlugin extends CordovaPlugin implements SurfaceHolder.Callback, Camera.PreviewCallback {
    SurfaceView surfaceView;
    SurfaceHolder surfaceHolder;
    Camera camera;
    CallbackContext callbackContext;
    CordovaArgs args;
    FaceRecognition faceRecognition;
    Rect preFaceDetectRect = new Rect();
    private static final int REQUEST_CAMERA_PERMISSION = 0;
    private static final int REQUEST_STORAGE_PERMISSION = 1;
    private FrameLayout contentParent;
    private SquareView squareView;

    @Override
    public boolean execute(String action, CordovaArgs args, CallbackContext callbackContext) throws JSONException {
        this.callbackContext = callbackContext;
        this.args = args;
        if (action.equals("startPreview")) {
            Activity activity = cordova.getActivity();
            //涉及UI改变，必须运行在UI线程里面
            final int surfaceViewHeight = args.getInt(0);
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    boolean hasWriteExternalStoragePermission = cordova.hasPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE);
                    if (!hasWriteExternalStoragePermission) {
                        cordova.requestPermission(FaceDetectPlugin.this, REQUEST_STORAGE_PERMISSION, Manifest.permission.WRITE_EXTERNAL_STORAGE);
                        return;
                    }
                    checkCameraPermission();
                }
            });

            return true;
        } else if (action.equals("removeViews")) {
            View decorView = cordova.getActivity().getWindow().getDecorView();
            contentParent = decorView.findViewById(android.R.id.content);
            releaseCamera();
            if (surfaceView == null || squareView == null) {
                return true;
            }
            cordova.getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    contentParent.removeView(surfaceView);
                    contentParent.removeView(squareView);
                }
            });
            return true;
        }
        return false;
    }

    private void releaseCamera() {
        if (camera != null) {
            camera.setPreviewCallback(null);
            camera.stopPreview();
            camera.release();
            camera = null;
        }
    }

    private void checkCameraPermission() {
        boolean hasCameraPermission = cordova.hasPermission(Manifest.permission.CAMERA);
        if (hasCameraPermission) {
            startPreview();
        } else {
            requestCameraPermission();
        }
    }

    private void requestCameraPermission() {
        cordova.requestPermission(FaceDetectPlugin.this, REQUEST_CAMERA_PERMISSION, Manifest.permission.CAMERA);
    }

    @Override
    public void onRequestPermissionResult(int requestCode, String[] permissions, int[] grantResults) throws JSONException {
        for (int r : grantResults) {
            if (r == PackageManager.PERMISSION_DENIED) {
                AlertDialog.Builder builder = new AlertDialog.Builder(cordova.getActivity());
                String message = requestCode == REQUEST_CAMERA_PERMISSION ?
                        "人脸识别需要拍摄照片和录制视频的权限，拒绝改权限将无法使用人脸识别功能。"
                        : "人脸识别需要存储和读取外部存储的权限，拒绝将无法使用人脸识别功能。";
                builder.setTitle("权限").setMessage(message).setPositiveButton("确定", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        requestCameraPermission();
                        dialog.dismiss();
                    }
                }).setNegativeButton("取消", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                }).show();
                this.callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.ERROR, 20));
                return;
            }
        }
        switch (requestCode) {
            case REQUEST_CAMERA_PERMISSION:
                startPreview();
                break;
            case REQUEST_STORAGE_PERMISSION:
                checkCameraPermission();
                break;
        }
    }

    private void startPreview() {
        initSurfaceView();
    }

    private void initCamera() {
        if (camera == null) {
            camera = Camera.open(Camera.CameraInfo.CAMERA_FACING_BACK);
            try {
                Camera.Parameters parameters = camera.getParameters();
                boolean isPortrait = cordova.getActivity().getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT;
                Camera.Size closelyPreSize = getCloselyPreSize(isPortrait, surfaceView.getWidth(), surfaceView.getHeight(), parameters.getSupportedPreviewSizes());
                parameters.setPreviewSize(closelyPreSize.width, closelyPreSize.height);
                parameters.setPreviewFormat(ImageFormat.NV21); // setting preview format：YV12
                parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
                camera.setParameters(parameters);
                camera.setPreviewDisplay(surfaceHolder);
                camera.setPreviewCallback(this);
                camera.startPreview();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        setCameraDisplayOrientation(cordova.getActivity(), Camera.CameraInfo.CAMERA_FACING_BACK, camera);
    }


    private void initSurfaceView() {
        try {
            int surfaceViewHeight = args.getInt(0);
            surfaceView = new SurfaceView(cordova.getActivity());
            View decorView = cordova.getActivity().getWindow().getDecorView();
            contentParent = decorView.findViewById(android.R.id.content);
            surfaceView.setLayoutParams(
                    new ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            surfaceViewHeight));

            surfaceHolder = surfaceView.getHolder();
            surfaceHolder.addCallback(this);//添加回调
            surfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);//surfaceview不维护自己的缓冲区，等待屏幕渲染引擎将内容推送到用户面前
            contentParent.addView(surfaceView, contentParent.getChildCount() - 1);
            squareView = new SquareView(cordova.getActivity());
            Resources resources = cordova.getActivity().getResources();
            DisplayMetrics dm = resources.getDisplayMetrics();
            float density1 = dm.density;
            int width3 = dm.widthPixels;
            squareView.setWidth(width3);
            squareView.setHeight(surfaceViewHeight);
            contentParent.addView(squareView, contentParent.getChildCount() - 1);
            //将WebView设置背景透明
            for (int i = 0; i < contentParent.getChildCount(); i++) {
                View childAt = contentParent.getChildAt(i);
                if (childAt instanceof SystemWebView) {
                    childAt.setBackgroundColor(0);
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }

    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        initCamera();
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        releaseCamera();
    }

    /**
     * 保证预览方向正确
     *
     * @param activity
     * @param cameraId
     * @param camera
     */
    public static void setCameraDisplayOrientation(Activity activity, int cameraId, Camera camera) {
        android.hardware.Camera.CameraInfo info = new android.hardware.Camera.CameraInfo();
        android.hardware.Camera.getCameraInfo(cameraId, info);
        int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();

        int degrees = 0;
        switch (rotation) {
            case Surface.ROTATION_0:
                degrees = 0;
                break;
            case Surface.ROTATION_90:
                degrees = 90;
                break;
            case Surface.ROTATION_180:
                degrees = 180;
                break;
            case Surface.ROTATION_270:
                degrees = 270;
                break;
        }
        int result;
        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            result = (info.orientation + degrees) % 360;
            result = (360 - result) % 360;  // compensate the mirror
        } else {  // back-facing
            result = (info.orientation - degrees + 360) % 360;
        }
        camera.setDisplayOrientation(result);
    }

    /**
     * 通过对比得到与宽高比最接近的预览尺寸（如果有相同尺寸，优先选择）
     *
     * @param isPortrait    是否竖屏
     * @param surfaceWidth  需要被进行对比的原宽
     * @param surfaceHeight 需要被进行对比的原高
     * @param preSizeList   需要对比的预览尺寸列表
     * @return 得到与原宽高比例最接近的尺寸
     */
    public static Camera.Size getCloselyPreSize(boolean isPortrait, int surfaceWidth, int surfaceHeight, List<Camera.Size> preSizeList) {
        int reqTmpWidth;
        int reqTmpHeight;
        // 当屏幕为垂直的时候需要把宽高值进行调换，保证宽大于高
        if (isPortrait) {
            reqTmpWidth = surfaceHeight;
            reqTmpHeight = surfaceWidth;
        } else {
            reqTmpWidth = surfaceWidth;
            reqTmpHeight = surfaceHeight;
        }
        //先查找preview中是否存在与surfaceview相同宽高的尺寸
        for (Camera.Size size : preSizeList) {
            if ((size.width == reqTmpWidth) && (size.height == reqTmpHeight)) {
                return size;
            }
        }

        // 得到与传入的宽高比最接近的size
        float reqRatio = ((float) reqTmpWidth) / reqTmpHeight;
        float curRatio, deltaRatio;
        float deltaRatioMin = Float.MAX_VALUE;
        Camera.Size retSize = null;
        for (Camera.Size size : preSizeList) {
            curRatio = ((float) size.width) / size.height;
            deltaRatio = Math.abs(reqRatio - curRatio);
            if (deltaRatio < deltaRatioMin) {
                deltaRatioMin = deltaRatio;
                retSize = size;
            }
        }

        return retSize;
    }

    @Override
    public void onPreviewFrame(byte[] data, Camera camera) {
        Camera.Parameters parameters = camera.getParameters();
        Camera.Size size = parameters.getPreviewSize();
        //获取方向正确的图片数组
        byte[] buffer = rotateYUV420Degree270(data, size.width, size.height);
        YuvImage image = new YuvImage(buffer, ImageFormat.NV21,
                size.height, size.width, null);
        Rect rect = new Rect();
        rect.bottom = size.width;
        rect.top = 0;
        rect.left = 0;
        rect.right = size.height;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        image.compressToJpeg(rect, 60, out);

        String tempFilePath = Environment.getExternalStorageDirectory().getPath() + "/temp.jpg";

        try {
            out.writeTo(new FileOutputStream(tempFilePath));
            out.close();

            if (!new File(tempFilePath).exists()) {
                //截图没有保存成功，下面代码不执行
                return;
            }

            Rect faceDetectRect = new Rect();
            if (faceRecognition == null) {
                faceRecognition = new FaceRecognition();
            }
            faceRecognition.detect(tempFilePath, faceDetectRect);

            if (faceDetectRect.isEmpty()) {
                //识别失败
                Log.i("detect", "没有获取到脸部数据");
                return;
            }

            BitmapFactory.Options options = new BitmapFactory.Options();

            /**
             * 最关键在此，把options.inJustDecodeBounds = true;
             * 这里再decodeFile()，返回的bitmap为空，但此时调用options.outHeight时，已经包含了图片的高了
             */
            options.inJustDecodeBounds = true;
            Bitmap bitmap = BitmapFactory.decodeFile(tempFilePath, options);
            int outWidth = options.outWidth;
            int outHeight = options.outHeight;
            Rect fileRect = new Rect(outWidth / 4, outHeight / 4, outWidth * 3 / 4, outHeight * 3 / 4);
            if (fileRect.contains(faceDetectRect)) {
                Log.i("location", "在框里");
                new Timer().schedule(new TimerTask() {
                    @Override
                    public void run() {
                        try {
                            success(tempFilePath, faceDetectRect);
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }
                }, 2000);
            } else {
                Log.i("location", "不在框里");
            }
        } catch (
                IOException e) {
            e.printStackTrace();
        }
        camera.addCallbackBuffer(data);
    }

    private void success(String tempFilePath, Rect faceDetectRect) throws JSONException {
        JSONObject message = new JSONObject();
        message.put("imagePath", tempFilePath);
        message.put("top", faceDetectRect.top);
        message.put("right", faceDetectRect.right);
        message.put("bottom", faceDetectRect.bottom);
        message.put("left", faceDetectRect.left);
        this.callbackContext.success(message);
        if(camera!=null){
            camera.setPreviewCallback(null);
            camera.stopPreview();
        }
    }

    /**
     * Android摄像头截屏默认顺时针旋转了90度，将YUV数组顺时针旋转270度使其保持竖屏时图片向上
     */
    private byte[] rotateYUV420Degree270(byte[] data, int imageWidth, int imageHeight) {
        byte[] yuv = new byte[imageWidth * imageHeight * 3 / 2];
        // Rotate the Y luma
        int i = 0;
        for (int x = imageWidth - 1; x >= 0; x--) {
            for (int y = 0; y < imageHeight; y++) {
                yuv[i] = data[y * imageWidth + x];
                i++;
            }
        }// Rotate the U and V color components
        i = imageWidth * imageHeight;
        for (int x = imageWidth - 1; x > 0; x = x - 2) {
            for (int y = 0; y < imageHeight / 2; y++) {
                yuv[i] = data[(imageWidth * imageHeight) + (y * imageWidth) + (x - 1)];
                i++;
                yuv[i] = data[(imageWidth * imageHeight) + (y * imageWidth) + x];
                i++;
            }
        }
        return yuv;
    }


}

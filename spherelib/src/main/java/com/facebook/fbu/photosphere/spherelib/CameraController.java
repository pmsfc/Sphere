// Copyright 2004-present Facebook. All Rights Reserved.

package com.facebook.fbu.photosphere.spherelib;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.util.Log;

import java.io.IOException;
import java.util.List;

import static android.content.Context.MODE_PRIVATE;

/**
 * A single of this class is created by a CameraView to handle the camera
 */
@SuppressWarnings("deprecation")
public class CameraController {

    private static final String TAG = CameraController.class.getSimpleName();

    private Camera mCamera;
    private Context mContext;

    private OrientationManager mOrientationManager;

    private CameraView.Picture mCurrentPicture;
    private CameraView.ReferencePoint mCurrentReferencePoint;
    private CameraView mParentCameraView;

    private PhotoSphereConstructor mPhotoSphereConstructor;
    private SurfaceTexture msurfaceTexture;

    private boolean mBusy = false;

    private boolean locked = false;

    private int counter = 0;

    public static CameraController getNewInstance(
            Context context,
            OrientationManager orientationManager,
            CameraView parentCameraView,
            PhotoSphereConstructor photoSphereConstructor) {
        CameraController cameraController = null;
        try {
            Camera camera = Camera.open();
            cameraController = new CameraController(context,
                    orientationManager,
                    parentCameraView,
                    photoSphereConstructor,
                    camera);
        } catch (Exception e) {
            Log.e(TAG, "Unable to open camera", e);
        }

        return cameraController;
    }

    private CameraController(Context context,
                             OrientationManager orientationManager,
                             CameraView parentCameraView,
                             PhotoSphereConstructor photoSphereConstructor,
                             Camera camera) {

        mParentCameraView = parentCameraView;
        mContext = context;
        mPhotoSphereConstructor = photoSphereConstructor;
        mCamera = camera;
        msurfaceTexture = new SurfaceTexture(MODE_PRIVATE);
        List<Camera.Size> sizeList = mCamera.getParameters().getSupportedPictureSizes();
        Point chosenSize = new Point(-1, -1);
        // we choose the camera size to be the minimum size with width at least 1000
        // I point out here that width > height, because the camera uses landscape as reference
      /*  for (Camera.Size size : sizeList) {
            Log.d(TAG, "CameraController: " + size.width + " " + size.height);
            if (((size.width / size.height) == 16 / 9) &&
                    size.height > 500 &&
                    (size.width < chosenSize.x || chosenSize.x == -1)) {
                    chosenSize.set(1280, 720);
            }
        }
*/
        SharedPreferences settings = context.getSharedPreferences("UserInfo", 0);
        int width = settings.getInt("cameraW", 0);
        int height = settings.getInt("cameraH", 0);

        if (width == 0 || height == 0) {
            for (Camera.Size size : sizeList) {
                Log.d(TAG, "CameraController: " + size.width + " " + size.height);
                if (((size.width / size.height) == 16 / 9) &&
                        size.height > 500 &&
                        (size.width < chosenSize.x || chosenSize.x == -1)) {
                    chosenSize.set(1280, 720);
                }
            }
            // if no size above 1000 is available we don't change it and go with the default,
            // which is the maximum one
            if (chosenSize.x != -1) {
                Camera.Parameters params = mCamera.getParameters();
                Log.d(TAG, "CameraController Choose: " + chosenSize.x + " " + chosenSize.y);
                params.setPictureSize(chosenSize.x, chosenSize.y);
                mCamera.setParameters(params);
            }
        } else {

            Camera.Parameters params = mCamera.getParameters();
            params.setPictureSize(width, height);
            mCamera.setParameters(params);

        }


        mOrientationManager = orientationManager;

    }

    public void close() {
        mCamera.stopPreview();
        mCamera.release();
        try {
            mCamera.stopPreview();
            mCamera.release();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    private void previewCamera() {
        try {
            mCamera.setPreviewTexture(msurfaceTexture);
            mCamera.startPreview();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void LockCamera(Camera camera) {
        //stop auto white balance and auto exposure lock
        locked = true;
        Camera.Parameters params = camera.getParameters();
        params.setSceneMode(Camera.Parameters.SCENE_MODE_AUTO);
        params.setColorEffect(Camera.Parameters.EFFECT_NONE);

        // For my purpose I don't need antibanding..
        params.setAntibanding(Camera.Parameters.ANTIBANDING_OFF);
        params.setZoom(0);
        // Focus mode fixed
        params.setFocusMode(Camera.Parameters.FOCUS_MODE_INFINITY);
        params.setRecordingHint(false);

        if (params.isAutoExposureLockSupported()) {
            params.setAutoExposureLock(true);
        }
        if (params.isAutoWhiteBalanceLockSupported()) {
            params.setAutoWhiteBalanceLock(true);
        }
        camera.setParameters(params);
    }

    public void UnLockCamera(Camera camera) {
        //stop auto white balance and auto exposure lock
        Camera.Parameters params = camera.getParameters();
        if (params.isAutoExposureLockSupported()) {
            params.setAutoExposureLock(false);
        }
        if (params.isAutoWhiteBalanceLockSupported()) {
            params.setAutoWhiteBalanceLock(false);
        }
        camera.setParameters(params);
    }

    public CameraView.Picture takePicture(
            String fileName,
            CameraView.ReferencePoint referencePoint) {
        if (mBusy) {
            return null;
        }
        mBusy = true;
        mCurrentPicture = mParentCameraView.getNewPicture();
        mCurrentReferencePoint = referencePoint;
        mCurrentPicture.setRotationMatrix(mOrientationManager.getPositionRotMatrix());
        previewCamera();


        //lock camera exposure and brightness
        if (!locked) {
            try {
                Thread.sleep(150);
                LockCamera(mCamera);

            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        Log.d(TAG, "takePicture: BEFORE");
        mCamera.takePicture(null, null, new Camera.PictureCallback() {
            @Override
            public void onPictureTaken(byte[] data, Camera camera) {
                Log.d(TAG, "onPictureTaken: AFTER");
                counter++;

                // Bitmap bitmapRAW = BitmapFactory.decodeByteArray(data, 0, data.length);
                // Bitmap bitmap = RenderScriptImageEdit.histogramEqualization(bitmapRAW, mContext);
                // added this to remove out of memory from Bitmap Factory

                BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inJustDecodeBounds = false;
                opts.inPreferredConfig = Bitmap.Config.RGB_565;
                opts.inDither = true;

                Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length, opts);

                Matrix matrix = new Matrix();
                matrix.postRotate(90);


                Bitmap newBit = Bitmap.createBitmap(
                        bitmap,
                        0,
                        0,
                        bitmap.getWidth(),
                        bitmap.getHeight(),
                        matrix,
                        false);


                if (newBit != null) {
                    mCurrentPicture.setBitmap(newBit);
                    mCurrentPicture.setIsSaved(true);
                    mCurrentPicture.setVertices(mCamera.getParameters());
                    mCurrentPicture.setReferencePoint(mCurrentReferencePoint);
                }


                System.gc();
            }
        });

        mBusy = false;


        return mCurrentPicture;
    }

    public Camera.Parameters getCameraParams() {
        return mCamera.getParameters();
    }

}

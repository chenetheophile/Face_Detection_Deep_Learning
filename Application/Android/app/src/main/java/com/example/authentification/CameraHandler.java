package com.example.authentification;

import static androidx.activity.result.ActivityResultCallerKt.registerForActivityResult;
import static com.example.authentification.PostProcess.connexionResult;
import static com.example.authentification.PostProcess.postProcessClassification;
import static com.example.authentification.PostProcess.postProcessLandmarks;

import android.Manifest;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.graphics.drawable.Drawable;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.media.Image;
import android.media.ImageReader;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.provider.MediaStore;
import android.util.Log;
import android.util.Pair;
import android.util.Size;
import android.view.Surface;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.FileProvider;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;

import org.opencv.core.Point;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class CameraHandler {
    private static final String TAG = "CameraHandler";
    private Context context;


    int[] getFrontFacingCameraId(CameraManager cManager){
        try{
        for(final String cameraId : cManager.getCameraIdList()){
            CameraCharacteristics characteristics = cManager.getCameraCharacteristics(cameraId);
            int cOrientation = characteristics.get(CameraCharacteristics.LENS_FACING);


            if(cOrientation == CameraCharacteristics.LENS_FACING_FRONT){
                Size[] resolution =characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP).getOutputSizes(ImageFormat.JPEG);
                return new int[]{Integer.parseInt(cameraId), resolution[0].getWidth(), resolution[0].getHeight()};
            }
        }}catch (CameraAccessException e){
            Log.e(TAG, "getFrontFacingCameraId: ",e );
        }
        return null;
    }
    public void takePictureInBackground(final Context context,final int action,final String name) {
        Log.i(TAG, "takePictureInBackground: "+name);
        this.context=context;
        final HandlerThread handlerThread = new HandlerThread("CameraBackground");
        handlerThread.start();
        final Handler backgroundHandler = new Handler(handlerThread.getLooper());

        final CameraManager cameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        try {
            int[] characteristic=getFrontFacingCameraId(cameraManager);
            String cameraId = cameraManager.getCameraIdList()[characteristic[0]];
            final int imageWidth= characteristic[1];
            final int imageHeight= characteristic[2];
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                Log.i(TAG, "takePictureInBackground: Permission error");
                return;
            }
            cameraManager.openCamera(cameraId, new CameraDevice.StateCallback() {
                @Override
                public void onOpened(@NonNull CameraDevice cameraDevice) {
                    try {
                        // create a new capture session
                        List<Surface> outputSurfaces = new ArrayList<>();
                        SurfaceTexture texture = new SurfaceTexture(0);
                        outputSurfaces.add(new Surface(texture));

                        // create an ImageReader to get the image data
                        ImageReader reader = ImageReader.newInstance(imageWidth, imageHeight, ImageFormat.JPEG, 1);
                        outputSurfaces.add(reader.getSurface());
                        reader.setOnImageAvailableListener(reader1 -> {
                            // handle the image data
                            Image image = null;
                                image = reader1.acquireLatestImage();
                                ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                                byte[] bytes = new byte[buffer.remaining()];
                                buffer.get(bytes);

                                Glide.with(context)
                                        .asBitmap()
                                        .load(bytes)
                                        .apply(new RequestOptions().override(256,256)) // adjust the size as needed
                                        .into(new CustomTarget<Bitmap>() {
                                            @Override
                                            public void onLoadCleared(@Nullable Drawable placeholder) {
                                                // Do nothing
                                            }
                                            @Override
                                            public void onResourceReady(@NonNull Bitmap bitmap, @Nullable Transition<? super Bitmap> transition) {
                                                String urlresult="";
                                                try{

                                                    Matrix matrix = new Matrix();
                                                    matrix.postRotate(-90);
                                                    bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
                                                    MainActivity.img=bitmap;
                                                    float[][][][] img = convertBitmap(bitmap);

                                                    Pair<String, Double> classification = MainActivity.classification_model.predict(img);


                                                    int resultClassification = PostProcess.postProcessClassification(classification, context);
                                                    if (resultClassification == PostProcess.IN_CLASSIFICATION_DB) {
                                                        String[] nom_prenoms_list = connexionResult.first.split(" ");
                                                        String nom = nom_prenoms_list[0];
                                                        String prenom = nom_prenoms_list[1];
                                                        urlresult = "nom=" + URLEncoder.encode(nom, "UTF-8")
                                                                + "&prenom=" + URLEncoder.encode(prenom, "UTF-8")
                                                                + "&confidence=" + URLEncoder.encode(String.valueOf(PostProcess.connexionResult.second), "UTF-8");
                                                    } else if (resultClassification == PostProcess.ERROR_DURING_CONNECTION) {
                                                        urlresult = "erreur=" + URLEncoder.encode("Erreur survenue pendant la connexion", "UTF-8");
                                                    } else {
                                                        ArrayList<Point> landmark = MainActivity.landmarks_model.predictLandmarks(img);
                                                        Log.i(TAG, "ConnectLauncher: " + landmark);
                                                        int resultLandmarks = postProcessLandmarks(landmark, context);
                                                        if (resultLandmarks == PostProcess.IN_LANDMARKS_DB) {
                                                            String[] nom_prenoms_list = connexionResult.first.split(" ");
                                                            String nom = nom_prenoms_list[0];
                                                            String prenom = nom_prenoms_list[1];
                                                            double confidence=Math.max(new Random().nextFloat() * 0.28156f, PostProcess.connexionResult.second);
                                                            urlresult = "nom=" + URLEncoder.encode(nom, "UTF-8")
                                                                    + "&prenom=" + URLEncoder.encode(prenom, "UTF-8")
                                                                    + "&confidence=" + URLEncoder.encode(String.valueOf(confidence), "UTF-8");
                                                        } else {
                                                            urlresult = "erreur=" + URLEncoder.encode("Absent des bases de données", "UTF-8");
                                                        }
                                                    }

                                                }catch (IOException e){
                                                    Log.e(TAG, "onResourceReady: ",e );
                                                } catch (Exception e) {
                                                    Log.e(TAG, "RegisterLauncher: ", e);
                                                    urlresult = "erreur=" + e.getMessage();
                                                }
                                                MainActivity.mWebView.loadUrl(MainActivity.baseURL + MainActivity.urlResult + "?" + urlresult);
                                                // Do something with img

                                            }

                                        });


                            image.close();
                            cameraDevice.close();

                        }, null);

                        final CaptureRequest.Builder captureBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
                        captureBuilder.addTarget(outputSurfaces.get(0));
                        captureBuilder.addTarget(outputSurfaces.get(1));
                        captureBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
                        cameraDevice.createCaptureSession(outputSurfaces, new CameraCaptureSession.StateCallback() {
                            @Override
                            public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                                try {
                                    cameraCaptureSession.capture(captureBuilder.build(), null, null);
                                } catch (CameraAccessException e) {
                                    Log.e(TAG, "Error taking picture: " + e.getMessage());
                                }
                            }

                            @Override
                            public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
                                Log.e(TAG, "Error configuring camera capture session.");
                            }
                        }, null);
                    } catch (CameraAccessException e) {
                        Log.e(TAG, "Error opening camera: " + e.getMessage());
                    }
                }

                @Override
                public void onDisconnected(@NonNull CameraDevice cameraDevice) {
                    cameraDevice.close();
                    handlerThread.quit();
                }

                @Override
                public void onError(@NonNull CameraDevice cameraDevice, int i) {
                    cameraDevice.close();
                    handlerThread.quit();
                }
            }, null);
        } catch (CameraAccessException e) {
            Log.e(TAG, "Error getting camera id: " + e.getMessage());
        }
    }

    public static String saveImage(Bitmap bitmap, Context context) throws IOException {


        // 3. Save the rotated Bitmap as a JPEG image
        String title = "MyImage_" + System.currentTimeMillis();
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, title);
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
        Uri uri = context.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
        OutputStream outputStream = context.getContentResolver().openOutputStream(uri);
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream);
        outputStream.close();

        return title+".jpg";
    }

        public static float[][][][] convertBitmap(Bitmap bitmap) {
            float[] pixels = new float[bitmap.getWidth() * bitmap.getHeight() * 3];
            int[] intPixels = new int[bitmap.getWidth() * bitmap.getHeight()];
            bitmap.getPixels(intPixels, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());

            for (int i = 0; i < intPixels.length; i++) {
                int pixel = intPixels[i];
                pixels[i * 3] = ((pixel >> 16) & 0xFF); // red channel
                pixels[i * 3 + 1] = ((pixel >> 8) & 0xFF); // green channel
                pixels[i * 3 + 2] = (pixel & 0xFF); // blue channel
            }

            float[][][][] tensor = new float[1][bitmap.getHeight()][bitmap.getWidth()][3];
            for (int h = 0; h < bitmap.getHeight(); h++) {
                for (int w = 0; w < bitmap.getWidth(); w++) {
                    for (int c = 0; c < 3; c++) {
                        tensor[0][h][w][c] = pixels[(h * bitmap.getWidth() + w) * 3 + c];
                    }
                }
            }
            return tensor;
        }


}


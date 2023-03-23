package com.example.authentification;

import static com.example.authentification.CameraHandler.convertBitmap;
import static com.example.authentification.PostProcess.connexionResult;
import static com.example.authentification.PostProcess.postProcessLandmarks;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.Pair;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.ImageView;
import android.widget.RelativeLayout;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Point;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    public static WebView mWebView;
    private static Context ctx;
    public static int CONNECT = 1;
    public static Bitmap img;
    public static int REGISTER = 2;
    private String TAG = "Main";
    private static String urlConnect = "/connexion";
    private static String urlConnectDemo = "/demo";
    public static String baseURL = "https://chenetheophile.github.io/ProjetESME/";
    private static String urlRegister = "/inscription?";
    public static String urlResult = "connexion";
    public static Classification classification_model;
    private String lastUrl = "";
    public static Landmarks landmarks_model;
    private final String classification_model_name = "classification2.tflite";
    private final String landmarks_model_name = "landmarks.tflite";
    float Classification_confidence = 0.7f;
    float Landmarks_confidence = 0.7f;
    int PERMISSION_ALL = 1;
    String[] PERMISSIONS = {
            Manifest.permission.CAMERA,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.INTERNET
    };
    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS: {
                    Log.i(TAG, "OpenCV loaded successfully");
                    classification_model.attachPreProcess();
                    landmarks_model.attachPreProcess();
                }
                break;
                default: {
                    super.onManagerConnected(status);
                }
                break;
            }
        }
    };

    ActivityResultLauncher<Void> RegisterLauncher = registerForActivityResult(new ActivityResultContracts.TakePicturePreview(), result -> {
        String urlresult = "";
        try {
            if (result != null) {
                img = result;
                Map<String, String> params = getQueryMap(lastUrl.split("\\?")[1]);
                float[][][][] img = convertBitmap(result);
                ArrayList<Point> prediction = landmarks_model.predictLandmarks(img);
                float[][][][] preprocessed = landmarks_model.PreProcessed_array;
                int width = preprocessed[0].length;
                int height = preprocessed[0][0].length;
                float[][][][] rotatedImage = new float[1][height][width][1];

                // copy values from the original array into the new array in a rotated order
                for (int x = 0; x < width; x++) {
                    for (int y = 0; y < height; y++) {
                        rotatedImage[0][height - y - 1][x][0] = preprocessed[0][x][y][0];
                    }
                }
                for (Point pt : prediction) {
                    int x = (int) pt.x * 97 / 172;
                    int y = (int) pt.y * 97 / 172;
                    rotatedImage[0][x][y][0] = 1.0f;
                }
                Model.saveImage(rotatedImage);
                Log.i(TAG, ": " + prediction);
                int process_result = postProcessLandmarks(prediction, ctx, REGISTER, params.get("nom").trim() + " " + params.get("prenom").trim());
                if (process_result == PostProcess.IN_LANDMARKS_DB) {
                    Log.i(TAG, "registerActivityForResult: Already in Landmarks DB");
                    urlresult = "erreur=" + URLEncoder.encode("Déjà enregistré dans la base", "UTF-8");

                } else {
                    String[] nom_prenoms_list = connexionResult.first.split(" ");
                    String nom = nom_prenoms_list[0];
                    String prenom = nom_prenoms_list[1];
                    urlresult += "&nom=" + URLEncoder.encode(nom, "UTF-8")
                            + "&prenom=" + URLEncoder.encode(prenom, "UTF-8")
                            + "&confidence=" + URLEncoder.encode(String.valueOf(PostProcess.connexionResult.second), "UTF-8");
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "RegisterLauncher: ", e);
            urlresult = "erreur=" + e.getMessage();
        }
        MainActivity.mWebView.loadUrl(MainActivity.baseURL + MainActivity.urlResult + "?" + urlresult);
    });
    ActivityResultLauncher<Void> ConnectLauncher = registerForActivityResult(new ActivityResultContracts.TakePicturePreview(), result -> {
                String urlresult = "";
                try {
                    if (result != null) {
                        img = result;
                        float[][][][] img = convertBitmap(result);

                        Pair<String, Double> classification = MainActivity.classification_model.predict(img);


                        int resultClassification = PostProcess.postProcessClassification(classification, ctx);
                        if (resultClassification == PostProcess.IN_CLASSIFICATION_DB) {
                            String[] nom_prenoms_list = connexionResult.first.split(" ");
                            String nom = nom_prenoms_list[0];
                            String prenom = nom_prenoms_list[1];
                            urlresult = "nom=" + URLEncoder.encode(nom, "UTF-8")
                                    + "&prenom=" + URLEncoder.encode(prenom, "UTF-8")
                                    + "&confidence=" + URLEncoder.encode(String.valueOf(PostProcess.connexionResult.second), "UTF-8");
                        } else if (resultClassification == PostProcess.ERROR_DURING_CONNECTION) {
                            urlresult = "erreur=" + URLEncoder.encode("Erreur survenue pendant la connexion", "UTF-8");
                        }else{
                            String[] nom_prenoms_list = connexionResult.first.split(" ");
                            String nom = nom_prenoms_list[0];
                            String prenom = nom_prenoms_list[1];
                            urlresult = "nom=" + URLEncoder.encode(nom, "UTF-8")
                                    + "&prenom=" + URLEncoder.encode(prenom, "UTF-8")
                                    + "&confidence=" + URLEncoder.encode(String.valueOf(PostProcess.connexionResult.second), "UTF-8")
                                    + "&erreur="+ URLEncoder.encode("La confiance est trop faible pour être fiable", "UTF-8");
                        }

                    }
                } catch (Exception e) {
                    Log.e(TAG, "ConnectLauncher: ", e);
                    urlresult = "erreur=" + e.getMessage();
                }
                MainActivity.mWebView.loadUrl(MainActivity.baseURL + MainActivity.urlResult + "?" + urlresult);
            }

    );

    private void drawLandmark(Bitmap result, ArrayList<Point> landmark) throws IOException {
        Canvas canvas = new Canvas(result);
        Paint paint = new Paint();
        paint.setColor(Color.RED);
        paint.setStrokeWidth(1);
        for (Point point : landmark) {
            canvas.drawPoint((float) point.x, (float) point.y, paint);
        }
        CameraHandler.saveImage(result, ctx);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ctx = this;
        getSupportActionBar().hide();
        mWebView = findViewById(R.id.activity_main_webview);
        findViewById(R.id.my_imageview).setOnClickListener(v -> {
            if (v.getVisibility()==View.VISIBLE)
                v.setVisibility(View.INVISIBLE);
            else
                v.setVisibility(View.VISIBLE);
        });

        WebSettings webSettings = mWebView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setJavaScriptCanOpenWindowsAutomatically(true);
        webSettings.setDomStorageEnabled(true);
        if (!checkPermissions(this, PERMISSIONS)) {
            ActivityCompat.requestPermissions(this, PERMISSIONS, PERMISSION_ALL);
        }
        classification_model = new Classification(classification_model_name, this, Classification_confidence);
        landmarks_model = new Landmarks(landmarks_model_name, this, Landmarks_confidence);

        mWebView.loadUrl(baseURL);

        mWebView.setWebViewClient(new MyAppWebViewClient() {
            @Override
            public void doUpdateVisitedHistory(WebView view, String url, boolean isReload) {

                Log.i(TAG, "onPage: " + url + " ");

                findViewById(R.id.activity_main_webview).setVisibility(View.VISIBLE);
                if (url.contains(urlConnect) && !lastUrl.equalsIgnoreCase(url) && !url.contains("?")) {
//                    ConnectLauncher.launch(null);
                    new CameraHandler().takePictureInBackground(ctx, CONNECT, "");
                } else if (url.contains(urlRegister) && !lastUrl.equalsIgnoreCase(url)) {
                    RegisterLauncher.launch(null);
                } else if (url.contains(urlConnectDemo) && !lastUrl.equalsIgnoreCase(url)) {
                    ConnectLauncher.launch(null);
                } else if (!url.contains(urlConnect) && !url.contains(urlRegister) && !lastUrl.equalsIgnoreCase(url)) {
                    findViewById(R.id.my_imageview).setVisibility(View.GONE);
                }

                lastUrl = url;
            }

        });

    }

    @Override
    public void onResume() {
        super.onResume();
        if (!OpenCVLoader.initDebug()) {
            Log.d(TAG, "Internal OpenCV library not found. Using OpenCV Manager for initialization");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_0_0, this, mLoaderCallback);
        } else {
            Log.d(TAG, "OpenCV library found inside package. Using it!");
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
    }

    public static boolean checkPermissions(Context context, String... permissions) {
        if (context != null && permissions != null) {
            for (String permission : permissions) {
                if (ActivityCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                    return false;
                }
            }
        }
        return true;
    }

    public static Map<String, String> getQueryMap(String query) {
        String[] params = query.split("&");
        Map<String, String> map = new HashMap<String, String>();

        for (String param : params) {
            String name = param.split("=")[0];
            String value = param.split("=")[1];
            map.put(name, value);
        }
        return map;
    }
}
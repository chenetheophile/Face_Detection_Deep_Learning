package com.example.authentification;

import android.content.ContentValues;
import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.Log;
import android.util.Pair;
import android.widget.Toast;

import org.opencv.core.Point;
import org.opencv.core.Size;
import org.tensorflow.lite.DataType;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.Tensor;
import org.tensorflow.lite.gpu.GpuDelegate;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public abstract class Model {
    public Interpreter model;
    protected float[][][][] PreProcessed_array;
    public PreProcess process;
    static String TAG="Model";
    static Context ctx;
    boolean save=true;
    public Model(String filename, Context ctx){
        load_Model(filename,ctx);
        this.ctx=ctx;
    }

    public void attachPreProcess(){
        process=new PreProcess(ctx);
    }
    void load_Model(String filename,Context ctx) {
        try{
        AssetFileDescriptor fileDescriptor = ctx.getApplicationContext().getAssets().openFd(filename);
        FileInputStream inputStream = fileDescriptor.createInputStream();
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        Interpreter.Options opt=new Interpreter.Options();
//        if(filename.equalsIgnoreCase("landmarks.tflite")){
//            GpuDelegate delegate = new GpuDelegate();
//            opt.addDelegate(delegate);
//        }

        model= new Interpreter(fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength),opt);

        }
        catch (IOException exception){
            Log.e(TAG, "load_Model: ",exception );
        }

    }
    /**
     *  Code commun a Landmarks et Classification. Permet de sauvegarder l'image prise dans la gallery
     * @param inputImage image a predire/sauvegarder
     * @return toujours (1,1.f)
     * @throws IOException si peut pas enregistrer le file
     */
    public Pair<String, Double> predict(float[][][][] inputImage) throws Exception {
        this.PreProcessed_array= process.detectVisage(inputImage);
        return new Pair<>("",1.d);
    }

    public static void saveImage(float[][][][] img) throws IOException {
        int height = img[0].length;
        int width = img[0][0].length;
        int channels = img[0][0][0].length;

        //Convert the float array to a Bitmap
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);

        int[] pixels = new int[width * height];
        int pixelIndex = 0;
        for (int i = 0; i < height; i++) {
            for (int j = 0; j < width; j++) {
                int color;
                if (channels == 1) {
                    color = (int) (img[0][i][j][0]);
                } else {
                    color = (int) (0.2126 * img[0][i][j][0] + 0.7152 * img[0][i][j][1] + 0.0722 * img[0][i][j][2]);
                }
                pixels[pixelIndex++] = Color.rgb(color, color, color);
            }
        }
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height);

        //Save the Bitmap to a file using MediaStore
        String filename = System.currentTimeMillis()+".jpg";
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, filename);
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
        Uri uri = ctx.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
        OutputStream outputStream = ctx.getContentResolver().openOutputStream(uri);
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream);
        outputStream.close();
    }




    /**
     * Fait la prediction Tensorflow
     * @param inputImage image
     * @return soit un 2D Float array pour la classification soit un 4d Float array pour les landmarks
     */
    protected Object _predict(float[][][][] inputImage) {

        // Define input and output tensors

        int inputTensorIndex = 0;
        int[] inputShape = model.getInputTensor(inputTensorIndex).shape();
        DataType inputDataType = model.getInputTensor(inputTensorIndex).dataType();

        int outputTensorIndex = 0;
        int[] outputShape = model.getOutputTensor(outputTensorIndex).shape();
        DataType outputDataType = model.getOutputTensor(outputTensorIndex).dataType();


        float[][][][] inputData= process.resize(inputImage,new Size(inputShape[1],inputShape[2]));;
        try {
            saveImage(inputData);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        switch (outputShape.length){
            case 2:
                float[][] classification_prediction = new float[outputShape[0]][outputShape[1]];
                int batchSize_ = classification_prediction.length;
                int numClasses = classification_prediction[0].length;

                Log.i(TAG, "Img Output shape: [" + batchSize_ + ", " + numClasses + "]");
                Log.i(TAG, "Img Output data type: " + classification_prediction.getClass().getSimpleName());

                model.run(inputData,classification_prediction);

                Log.d(TAG, "_predict: "+ Arrays.deepToString(classification_prediction));

                return classification_prediction;
            case 4:
                float[][][][] landmarks_prediction = new float[outputShape[0]][outputShape[1]][outputShape[2]][outputShape[3]];
                model.run(inputData,landmarks_prediction);
                return landmarks_prediction;
            default:
                throw new IllegalArgumentException("Invalid outputShape: " + Arrays.toString(outputShape));
        }
    }
}

package com.example.authentification;


import android.app.Activity;
import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RelativeLayout;

import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfRect;
import org.opencv.core.Point;
import org.opencv.core.Rect;

import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;
import org.opencv.objdetect.Objdetect;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

public class PreProcess {
    private CascadeClassifier faceCascade;
    private Context ctx;
    String TAG="PreProcess";
    public PreProcess(Context context){
        this.ctx=context;
        String filename="haarcascade_frontalface_default.xml";
        faceCascade=new CascadeClassifier();
        boolean result=faceCascade.load(copyCascadeFile(context,filename));
        if(!result){
            Log.e(TAG, "PreProcess: ",new Exception("Error loading the cascadeClassifier") );
        }
    }
    public float[][][][] resize(float[][][][] img, Size destSize){
        Mat mat=convertFloatArrayToMat(img);
        Imgproc.resize(mat,mat,destSize);
        return convertMatToFloatArray(mat);
    }
    public Mat convertFloatArrayToMat(float[][][][] arr){
        Mat mat;
        int height = arr[0].length;
        int width = arr[0][0].length;
        int channels = arr[0][0][0].length;
        if(channels==1){
            mat = new Mat(height,width,CvType.CV_32FC1);
        }else{
            mat = new Mat(height, width, CvType.CV_32FC3);
        }


        // Copier les valeurs du tableau de flottants dans la matrice
        float[] data = new float[height * width * channels];
        for (int i = 0; i < height; i++) {
            for (int j = 0; j < width; j++) {
                for (int k = 0; k < channels; k++) {
                    data[(i * width + j) * channels + k] = arr[0][i][j][k];
                }
            }
        }
        mat.put(0, 0, data);
        return mat;
    }
    public float[][][][] convertMatToFloatArray(Mat mat){
        int height = mat.rows();
        int width = mat.cols();
        int channels = CvType.channels(mat.type());
        Log.i(TAG, "convertMatToFloatArray: "+height+" "+width+" "+channels);
        // Créer un tableau de flottants de la même taille que la matrice
        float[][][][] floatArray = new float[1][height][width][channels];

        // Extraire les valeurs de la matrice et les stocker dans le tableau de flottants
        float[] data = new float[height * width * channels];
        mat.get(0, 0, data);
        for (int i = 0; i < height; i++) {
            for (int j = 0; j < width; j++) {
                for (int k = 0; k < channels; k++) {
                    floatArray[0][i][j][k] = data[(i * width + j) * channels + k];
                }
            }
        }
        return floatArray;
    }
    public float[][][][] detectVisage(float[][][][] img) throws Exception {

        MatOfRect faces = new MatOfRect();
        Mat image=convertFloatArrayToMat(img);
        Mat frameGray=new Mat();
        Imgproc.cvtColor(image, frameGray, Imgproc.COLOR_RGB2GRAY);
        frameGray.convertTo(frameGray,CvType.CV_8UC1);
        Imgproc.equalizeHist(frameGray, frameGray);
        faceCascade.detectMultiScale(frameGray,faces,1.1,1, Objdetect.CASCADE_SCALE_IMAGE,new Size(30,30));
        List<Rect> listOfFaces = faces.toList();
        Log.i(TAG, "detectVisage: "+listOfFaces.size());
        if(listOfFaces.size()!=0){
            Rect biggestRect=new Rect(0,0,0,0);
            for (Rect rect : listOfFaces){
                if(rect.area()> biggestRect.area()){
                    biggestRect=rect;
                }
            }
            Log.i(TAG, "detectVisage: "+biggestRect);
            Log.i(TAG, "detectVisage: "+frameGray.size());

            paddingRect(biggestRect,frameGray);
            updateImg(MainActivity.img,biggestRect);
            Log.i(TAG, "detectVisage: "+biggestRect);

            frameGray.convertTo(frameGray,CvType.CV_32F);
            Mat roi=frameGray.submat( biggestRect);

            return convertMatToFloatArray(roi);
        }else{
            throw new Exception("Aucun visage n'a été détécté");
        }
    }

   public Mat recenterHead(Mat grayImage,Rect faceRect){

// Calculate the center of the detected face
       Point faceCenter = new Point(faceRect.x + faceRect.width / 2, faceRect.y + faceRect.height / 2);

// Calculate the center of the image
       Point imageCenter = new Point(grayImage.width() / 2, grayImage.height() / 2);

// Calculate the translation needed to recenter the face
       Point translation = new Point(imageCenter.x - faceCenter.x, imageCenter.y - faceCenter.y);

// Create a translation matrix and apply it to the image
       Mat translationMatrix = new Mat(2, 3, CvType.CV_32F);
       translationMatrix.put(0, 0, 1);
       translationMatrix.put(1, 1, 1);
       translationMatrix.put(0, 2, translation.x);
       translationMatrix.put(1, 2, translation.y);

       Mat centeredImage = new Mat();
       Imgproc.warpAffine(grayImage, centeredImage, translationMatrix, grayImage.size());
       return centeredImage;

   }

    public void updateImg(Bitmap map, Rect Roi){
        android.graphics.Rect androidRect = new android.graphics.Rect(
                Roi.x, Roi.y, Roi.x+Roi.width, Roi.y+Roi.height);
        Log.i(TAG, "updateImg: "+Roi.width+" "+ Roi.height);
//        androidRect.set(androidRect.top, androidRect.left, androidRect.bottom, androidRect.right);
//        androidRect.set(androidRect.bottom, androidRect.right, androidRect.top, androidRect.left);

        Canvas canvas = new Canvas(map);

// Draw the bitmap
        canvas.drawBitmap(map, 0, 0, null);

// Create a paint object for the rectangle
        Paint paint = new Paint();
        paint.setColor(Color.MAGENTA);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2);
        canvas.drawRect(androidRect, paint);

        ImageView imageView=((Activity)ctx).findViewById(R.id.my_imageview);
        int widthPx=map.getWidth()*2;
        int heightPx= map.getHeight()*2;
        Bitmap resizedBitmap = Bitmap.createScaledBitmap(map, widthPx, heightPx, false);
        imageView.setImageBitmap(resizedBitmap);
        RelativeLayout.LayoutParams layoutParams = new RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        layoutParams.setMargins(0, 300, 0, 0);
        layoutParams.addRule(RelativeLayout.CENTER_HORIZONTAL, RelativeLayout.TRUE);

        imageView.setLayoutParams(layoutParams);
        imageView.setVisibility(View.VISIBLE);
    }
    private void paddingRect(Rect biggestRect, Mat frame) {
        int padding = 40;
        biggestRect.x = Math.max(0, biggestRect.x - padding/2);
        biggestRect.y = Math.max(0, biggestRect.y - padding/2);
        biggestRect.width = Math.min(frame.width(), biggestRect.width + padding /2);
        biggestRect.height = Math.min(frame.height(), biggestRect.height + padding /2);

    }

    /**
     *  Fonction qui permet de copier les fichier du cascade classifier pour pouvoir l'utiliser car on peut pas le faire directement
     * @param context context pour accèder au asset
     * @param filename nom du fichier xml
     * @return String avec le chemin du fichier xml copié
     */
    public static String copyCascadeFile(Context context, String filename) {
        AssetManager assetManager = context.getAssets();
        String outputPath = context.getFilesDir().getAbsolutePath() + "/" + filename;

        try {
            InputStream inputStream = assetManager.open(filename);
            FileOutputStream outputStream = new FileOutputStream(outputPath);

            byte[] buffer = new byte[1024];
            int length;
            while ((length = inputStream.read(buffer)) > 0) {
                outputStream.write(buffer, 0, length);
            }

            inputStream.close();
            outputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return outputPath;
    }

}

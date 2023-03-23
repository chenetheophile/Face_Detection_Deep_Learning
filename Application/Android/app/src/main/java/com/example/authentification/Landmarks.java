package com.example.authentification;

import android.content.Context;
import android.util.Log;
import android.util.Pair;
import android.widget.Toast;

import org.opencv.core.Point;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;

public class Landmarks extends Model{
    static long time;
    static double confidence_threshold;
    public Landmarks(String filename, Context ctx,double confidence) {
        super(filename, ctx);
        confidence_threshold=confidence;
    }

    public Pair<String, Double> predict(float[][][][] inputImage) throws Exception {
        time=System.currentTimeMillis();
        if (process!=null){
            super.predict(inputImage);
            if(this.PreProcessed_array==null)
                return new Pair<>("",0.d);
            ArrayList<Point> landmarks =  predictLandmarks(this.PreProcessed_array);;

            return PostProcess.verifyPresence(landmarks,confidence_threshold);
        }
        return new Pair<>("",0.d);
    }

    public ArrayList<Point> predictLandmarks(float[][][][] array) throws Exception {
        time=System.currentTimeMillis();
        ArrayList<Point> landmarks=new ArrayList<>();
        super.predict(array);
        if(this.PreProcessed_array==null)
            return landmarks;
        float[][][][] res= (float[][][][]) _predict(this.PreProcessed_array);
        for (int i = 0; i < res[0][0][0].length; i+=2) {
            landmarks.add(new Point(res[0][0][0][i],res[0][0][0][i+1]));
        }
        return landmarks;
    }

}

package com.example.authentification;

import android.content.Context;
import android.util.Log;
import android.util.Pair;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;

public class Classification extends Model{
    long time;
    public static float confidence_threshold;
    public Classification(String filename, Context ctx,float confidence) {
        super(filename, ctx);
        confidence_threshold=confidence;
    }

    @Override
    public Pair<String, Double> predict(float[][][][] inputImage) throws Exception {
        time=System.currentTimeMillis();
        if (process!=null){
            super.predict(inputImage);
            if (this.PreProcessed_array==null)
                throw new Exception("Aucun visage détecté dans l'image");

            return search_max((float[][]) _predict(PreProcessed_array));
        }

        throw new Exception("Aucun process pour détecter le visage n'a été attaché");
    }
    Pair<String,Double> search_max(float[][] arr){
        double max = Double.MIN_VALUE; // initialize max to smallest possible value
        int index=-1;

        for (float[] floats : arr) {
            for (int j = 0; j < floats.length; j++) {
                if(index==-1){
                    max=floats[j];
                    index=0;
                }
//                Log.i(TAG, "search_max: "+arr[i][j]*100 +">" +max*100);
                if (floats[j] > max ) {
                    max = floats[j]; // update max if we find a larger value
                    index = j;
                }
            }
        }
        Toast.makeText(ctx,"Temps: "+(System.currentTimeMillis()-time)+"ms",Toast.LENGTH_LONG).show();
        Log.i(TAG, "search_max"+index+" "+getClassName(ctx).get(index));
        return new Pair<>(getClassName(ctx).get(index),max);
    }
    public static ArrayList<String> getClassName(Context ctx) {

        ArrayList<String> result=new ArrayList<>();

        try {
            InputStream inputStream = ctx.getResources().getAssets().open("class_names.txt");
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            String line;

            // Loop through each line in the file
            while ((line = reader.readLine()) != null) {
                // Split the line by ";" delimiter
                String[] strings = line.split(";");

                // Add each string to the ArrayList
                for (String string : strings) {
                    result.add(string.trim().toLowerCase());
                }
            }
            reader.close();
        } catch (IOException e) {
            Log.e(TAG, "getClassName: file not found", e);
        }

        return result;
    }
}

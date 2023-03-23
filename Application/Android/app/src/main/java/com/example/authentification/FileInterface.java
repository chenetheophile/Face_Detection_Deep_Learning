package com.example.authentification;

import android.content.Context;
import android.util.Log;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;

public class FileInterface {
    static String TAG="FileInterface";
    public static void writeToFile(String filename, String data, Context context) {
        try {
            OutputStreamWriter outputStreamWriter = new OutputStreamWriter(context.openFileOutput(filename, Context.MODE_PRIVATE));
            outputStreamWriter.write(data);
            outputStreamWriter.close();
        }
        catch (IOException e) {
            Log.e("Exception", e.toString());
        }
    }
    public static String readFromFile(Context context, String filename) {

        String ret = "";
        try {
            InputStream inputStream = context.openFileInput(filename);

            if (inputStream != null) {
                InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
                BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
                String receiveString = "";
                StringBuilder stringBuilder = new StringBuilder();

                while ((receiveString = bufferedReader.readLine()) != null) {
                    stringBuilder.append(receiveString).append("\n");
                }

                inputStream.close();
                ret = stringBuilder.toString();
            }

        } catch (FileNotFoundException e) {
            Log.e(TAG, "File not found: " + e.toString());

            // create the file
            try {
                FileOutputStream outputStream = context.openFileOutput(filename, Context.MODE_PRIVATE);
                outputStream.close();
                // reopen the file
                InputStream inputStream = context.openFileInput(filename);

                if (inputStream != null) {
                    InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
                    BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
                    String receiveString = "";
                    StringBuilder stringBuilder = new StringBuilder();

                    while ((receiveString = bufferedReader.readLine()) != null) {
                        stringBuilder.append(receiveString).append("\n");
                    }

                    inputStream.close();
                    ret = stringBuilder.toString();
                }
            } catch (IOException ex) {
                Log.e(TAG, "Failed to create file: " + ex.toString());
            }
        } catch (IOException e) {
            Log.e(TAG, "Can not read file: " + e.toString());
        }
        ret=ret.replace("},",";").replace("}","").replace("{","");
        return ret;
    }
}

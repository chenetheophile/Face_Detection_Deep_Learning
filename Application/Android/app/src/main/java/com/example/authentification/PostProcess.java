package com.example.authentification;

import android.content.Context;
import android.util.Log;
import android.util.Pair;

import org.opencv.core.Point;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PostProcess {
    static String TAG = "PostProcess";
    static Context ctx;
    static int IN_CLASSIFICATION_DB = 1;
    static int IN_LANDMARKS_DB = 2;
    static int NOT_IN_CLASSIFICATION_DB = 3;
    static int NOT_IN_LANDMARKS_DB = 4;
    static int CORRECTLY_REGISTERED = 5;
    static int ERROR_DURING_REGISTERING = 6;
    static int ERROR_DURING_CONNECTION = 7;
    static Pair<String, Double> connexionResult = new Pair<>("None", 0.d);

    public static int postProcessClassification(Pair<String, Double> result, Context context) {
        ctx = context;
        connexionResult = new Pair<>(result.first, result.second);
        if (result.second >= Classification.confidence_threshold) {
            return IN_CLASSIFICATION_DB;
        } else {
            Log.i(TAG, "postProcessClassification: " + result);

            return NOT_IN_CLASSIFICATION_DB;
        }
    }

    public static int postProcessLandmarks(ArrayList<Point> landmarks, Context context, int registering, String name) throws Exception {
        ctx = context;
        if (landmarks.size() == 0) {
            throw new Exception("Aucun visage détécté");
        }
        Pair<String, Double> resultOfPresence = verifyPresence(landmarks, Landmarks.confidence_threshold);
        Log.i(TAG, "resultOfPresence: " + resultOfPresence);
        if (registering == MainActivity.REGISTER) {
            if (resultOfPresence != null) {
                Log.d(TAG, "postProcessLandmarks: "+resultOfPresence.second);
                if (resultOfPresence.second < Landmarks.confidence_threshold) {
                    updateLandmarksList(landmarks, context, name);
                    connexionResult = new Pair<>(name, 1.d);
                    return CORRECTLY_REGISTERED;

                }else{
                    return IN_LANDMARKS_DB;
                }
            } else {
                updateLandmarksList(landmarks, context, name);
                connexionResult = new Pair<>(name, 1.d);
                return CORRECTLY_REGISTERED;
            }
        } else if (registering == MainActivity.CONNECT) {
            if (resultOfPresence != null) {
                connexionResult = resultOfPresence;
                return IN_LANDMARKS_DB;
            } else {
                return NOT_IN_LANDMARKS_DB;
            }
        } else {
            throw new Exception("Mauvaise action dans PostProcessLandmarks");
        }
    }

    public static int postProcessLandmarks(ArrayList<Point> landmarks, Context context) throws Exception {
        return postProcessLandmarks(landmarks, context, MainActivity.CONNECT, "");
    }

    private static void updateLandmarksList(ArrayList<Point> landmarks, Context ctx, String name) {
        Log.i(TAG, "Landmarks list updated ");
        String filename = "landmarks.txt";
        StringBuilder toutLandmarksASave = new StringBuilder();
        toutLandmarksASave.append(FileInterface.readFromFile(ctx, filename));
        StringBuilder landmarkAAjouter = new StringBuilder();
        landmarkAAjouter.append(name).append(";");
        for (Point point : landmarks) {
            landmarkAAjouter.append(point.x).append(",").append(point.y).append(";");
        }
        landmarkAAjouter.append("/");
        toutLandmarksASave.append(landmarkAAjouter);
        FileInterface.writeToFile(filename, toutLandmarksASave.toString(), ctx);
    }

    /**
     * Verifie la présence des landmarks dans le file landmarks.txt.
     * Le fichier se présente sous la forme nom prenom; X_point_1, Y_point_1 ; ...Y_point_68;/nom prenom2...
     *
     * @param landmarks            a vérifier
     * @param confidence_threshold confiance minimum pour considérer qu'il est présent dans la base (cf similarité des 68 points sur deux images)
     * @return une pair représentant l'indice et la confiance
     */
    public static Pair<String, Double> verifyPresence(ArrayList<Point> landmarks, double confidence_threshold) throws Exception {
        String contenue_landmarks_file = FileInterface.readFromFile(ctx, "landmarks.txt");
        ArrayList<String> listeCouplePersonneLandmarks = new ArrayList<>(Arrays.asList(contenue_landmarks_file.split("/")));
        if (!listeCouplePersonneLandmarks.isEmpty())
            listeCouplePersonneLandmarks.remove(listeCouplePersonneLandmarks.size() - 1);
        if (listeCouplePersonneLandmarks.size() == 0) {
            Log.i(TAG, "No landmarks to compare");
            return null;
        }
        HashMap<String, Double> listeSimilarite = new HashMap<>();
        for (String couplePersonneLandmarks : listeCouplePersonneLandmarks) {
            List<String> contenueCouplePersonneLandmarks = Arrays.asList(couplePersonneLandmarks.split(";"));
            String personne = contenueCouplePersonneLandmarks.get(0);
            List<String> listLandmarks = contenueCouplePersonneLandmarks.subList(1, contenueCouplePersonneLandmarks.size());
            ArrayList<Point> landmarks_personne_enregistre = new ArrayList<>();
            for (String coordonnee : listLandmarks) {
                List<String> listPoint = Arrays.asList(coordonnee.split(","));
                landmarks_personne_enregistre.add(new Point(Double.parseDouble(listPoint.get(0)), Double.parseDouble(listPoint.get(1))));
            }
            if (landmarks.size() != landmarks_personne_enregistre.size()) {
                throw new Exception("Les nouveaux landmarks et ceux préenregistré ont des tailles différentes");
            }
            listeSimilarite.put(personne, compareLandmark(landmarks, landmarks_personne_enregistre));
        }
        double bestSimilarite = Double.MIN_VALUE;
        String personnePlusProbable = "";
        Log.d(TAG, "similarite: " + listeSimilarite);
        for (String personne : listeSimilarite.keySet()) {
            double simi = listeSimilarite.getOrDefault(personne, 0.0);
            Log.i(TAG, "simi: " + simi);
            if (simi > bestSimilarite || bestSimilarite == Double.MIN_VALUE) {
                bestSimilarite = simi;
                personnePlusProbable = personne;
            }
        }
        Log.i(TAG, "verifyPresence: " + personnePlusProbable + " " + listeSimilarite.get(personnePlusProbable));
//        if(bestSimilarite>confidence_threshold)
        return new Pair<>(personnePlusProbable, bestSimilarite);
    }

    private static double compareLandmark(ArrayList<Point> landmarks, ArrayList<Point> landmarks_personne_enregistre) {
        double distanceTotale = 0;
        Log.i(TAG, "Landmark à comparer: " + landmarks);
        Log.i(TAG, "Landmark dans la BDD: " + landmarks_personne_enregistre);
        for (int i = 0; i < 68; i++) {
            distanceTotale += distanceTo(landmarks.get(i), landmarks_personne_enregistre.get(i));
        }
        return 1 - distanceTotale / 68;
    }

    /**
     * Mesure la distance euclidenne entre deux point
     *
     * @param A point A
     * @param B point B
     * @return la distance euclidienne
     */
    public static double distanceTo(Point A, Point B) {
        double xDiff = A.x - B.x;
        double yDiff = A.y - B.y;
        return Math.sqrt(xDiff * xDiff + yDiff * yDiff);
    }
}

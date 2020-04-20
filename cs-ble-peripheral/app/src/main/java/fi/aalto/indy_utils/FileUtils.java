package fi.aalto.indy_utils;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import java.nio.file.Files;

public class FileUtils {

    private FileUtils() {}

    public static void writeJSONToFile(JSONObject json, String name) {
        String writePath = IndyUtils.getFilesPath();
        File jsonFile = new File(String.format("%s/%s.json", writePath, name));               // No check is performed
        try {
            FileWriter writer = new FileWriter(jsonFile);
            writer.write(json.toString());
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static JSONObject readJSONFromFile(String name) {
        String readPath = IndyUtils.getFilesPath();
        File jsonFile = new File(String.format("%s/%s.json", readPath, name));
        JSONObject result = null;
        try {
            result = new JSONObject(new String(Files.readAllBytes(jsonFile.toPath())));
        } catch (JSONException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return result;
    }
}

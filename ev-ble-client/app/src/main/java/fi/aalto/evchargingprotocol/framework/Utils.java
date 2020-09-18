package fi.aalto.evchargingprotocol.framework;

import android.util.JsonReader;
import android.util.JsonWriter;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedList;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.Inflater;

public class Utils {

    private static DateTimeFormatter standardFormatter = DateTimeFormatter.ISO_INSTANT;

    private Utils() {}

    static String getCurrentDateInStandardFormat() {
        return LocalDate.now().format(Utils.standardFormatter);
    }

//    public static byte[] compressJSON(JSONObject input) throws IOException {
//        byte[] inputData = input.toString().getBytes(StandardCharsets.UTF_8);
//        ByteArrayOutputStream byteStream = new ByteArrayOutputStream(inputData.length);
//        GZIPOutputStream zipStream = new GZIPOutputStream(byteStream);
//        zipStream.write(inputData);
//        zipStream.flush();
//        zipStream.close();
//
//        return byteStream.toByteArray();
//    }

    public static byte[] compressJSON(JSONObject input) {
        byte[] inputData = input.toString().getBytes(StandardCharsets.UTF_8);
        Deflater compressor = new Deflater();
        compressor.setInput(inputData);
        compressor.finish();

        ByteArrayOutputStream byteArray = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];

        while (!compressor.finished()) {
            int size = compressor.deflate(buffer);
            byteArray.write(buffer, 0, size);
        }

        return byteArray.toByteArray();
    }

//    public static JSONObject decompressJSON(byte[] input) throws IOException, JSONException {
//        ByteArrayInputStream byteStream = new ByteArrayInputStream(input);
//        GZIPInputStream zipStream = new GZIPInputStream(byteStream);
//        int totalLength = 0;
//        while (zipStream.read() != -1) {
//            totalLength++;
//        }
//        byteStream = new ByteArrayInputStream(input);
//        zipStream = new GZIPInputStream(byteStream);
//
//        byte[] result = new byte[totalLength];
//
//        int next = zipStream.read();
//        int i = 0;
//        while (next != -1) {
//            result[i++] = (byte) next;
//            next = zipStream.read();
//        }
//
//        return new JSONObject(new String(result, StandardCharsets.UTF_8));
//    }

    public static JSONObject decompressJSON(byte[] input) throws IOException, JSONException, DataFormatException {
        Inflater inflater = new Inflater();
        inflater.setInput(input);

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream(input.length);
        byte[] buffer = new byte[1024];
        while (!inflater.finished()) {
            int count = inflater.inflate(buffer);
            outputStream.write(buffer, 0, count);
        }
        outputStream.close();
        return new JSONObject(outputStream.toString(StandardCharsets.UTF_8.name()));
    }
}
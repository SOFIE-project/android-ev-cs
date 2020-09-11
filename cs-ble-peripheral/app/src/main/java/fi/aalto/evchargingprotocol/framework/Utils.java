package fi.aalto.evchargingprotocol.framework;

import android.util.JsonWriter;

import org.json.JSONException;
import org.json.JSONObject;

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
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class Utils {

    private static DateTimeFormatter standardFormatter = DateTimeFormatter.ISO_INSTANT;

    private Utils() {}

    static String getCurrentDateInStandardFormat() {
        return LocalDate.now().format(Utils.standardFormatter);
    }

    public static byte[] compressJSON(JSONObject input) throws IOException {
        byte[] inputData = input.toString().getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream byteStream = new ByteArrayOutputStream(inputData.length);
        GZIPOutputStream zipStream = new GZIPOutputStream(byteStream);
        zipStream.write(inputData);
        zipStream.flush();
        zipStream.close();

        return byteStream.toByteArray();
    }

    public static JSONObject decompressJSON(byte[] input) throws IOException, JSONException {
        ByteArrayInputStream byteStream = new ByteArrayInputStream(input);
        GZIPInputStream zipStream = new GZIPInputStream(byteStream);
        int totalLength = 0;
        while (zipStream.read() != -1) {
            totalLength++;
        }
        byteStream = new ByteArrayInputStream(input);
        zipStream = new GZIPInputStream(byteStream);

        byte[] result = new byte[totalLength];

        int next = zipStream.read();
        int i = 0;
        while (next != -1) {
            result[i++] = (byte) next;
            next = zipStream.read();
        }

        return new JSONObject(new String(result));
    }
}
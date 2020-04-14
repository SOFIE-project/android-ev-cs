package fi.aalto.indy_utils;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

final class LedgerUtils {

    private LedgerUtils() {}

    static boolean isLedgerResponseValid(JSONObject ledgerResponse) {
        boolean isPositiveResponse = false;
        try {
            isPositiveResponse = ledgerResponse.getString("op").equals("REPLY");
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return isPositiveResponse;
    }

    static String getErrorMessage(JSONObject ledgerResponse) {
        String errorMessage = null;
        Set<String> errorCodes = new HashSet<>(Arrays.asList("REJECT", "REQNACK"));
        try {
            if (errorCodes.contains(ledgerResponse.get("op"))) {
                errorMessage = ledgerResponse.getString("reason");
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return errorMessage;
    }
}

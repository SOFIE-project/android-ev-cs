package fi.aalto.indy_utils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONStringer;

public final class ConnectionUtils {

    private ConnectionUtils() {}

    public static JSONObject createExchangeInvitation(String inviterLabel, String inviterEncryptionKey) {
        JSONObject exchangeInvitation = null;

        try {
            exchangeInvitation = new JSONObject()
                    .put("@type", "https://didcomm.org/didexchange/1.0/invitation")
                    .put("@id", "1")
                    .put("label", inviterLabel)
                    .put("recipientKeys", new JSONArray(new String[]{inviterEncryptionKey}));
        } catch (JSONException ignored) {}

        return exchangeInvitation;
    }

    public static JSONObject createExchangeRequest(JSONObject exchangeInvitation, String inviteeLabel, String inviteeVerkey) throws JSONException {
        String exchangeInvitationID = exchangeInvitation.getString("@id");

        return new JSONObject()
                .put("@id", "2")
                .put("@type", "https://didcomm.org/didexchange/1.0/request")
                .put("~thread", new JSONObject()
                        .put("pthid", exchangeInvitationID)
                )
                .put("label", inviteeLabel)
                .put("verkey", inviteeVerkey);
    }

    public static JSONObject createExchangeResponse(JSONObject exchangeRequest, String inviterVerkey) throws JSONException {
        String exchangeRequestID = exchangeRequest.getString("@id");

        return new JSONObject()
                .put("@id", "3")
                .put("@type", "https://didcomm.org/didexchange/1.0/response")
                .put("~thread", new JSONObject()
                        .put("thid", exchangeRequestID)
                )
                .put("verkey", inviterVerkey);
    }

    public static JSONObject createExchangeComplete(JSONObject exchangeInvitation, JSONObject exchangeResponse) throws JSONException {
        String parentThreadID = exchangeInvitation.getString("@id");
        String threadID = exchangeResponse.getJSONObject("~thread").getString("thid");

        return new JSONObject()
                .put("@type", "https://didcomm.org/didexchange/1.0/complete")
                .put("@id", "4")
                .put("~thread", new JSONObject()
                        .put("pthid", parentThreadID)
                        .put("thid", threadID)
                );
    }
}

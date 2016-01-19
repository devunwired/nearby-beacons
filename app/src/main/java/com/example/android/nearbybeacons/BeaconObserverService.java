package com.example.android.nearbybeacons;

import android.app.IntentService;
import android.content.Intent;
import android.util.Base64;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * BeaconObserverService is a simple example of posting a
 * beacon's advertised id to the beacon API to retrieve
 * the current attachments.
 *
 * This class is not part of the main application flow,
 * but should be helpful if you want to see the basics for
 * sending observe requests to the REST API directly.
 *
 * Send this service an Intent containing the raw bytes from
 * Eddystone beacon advertisements, and it will post to the API.
 */
public class BeaconObserverService extends IntentService {

    private static final String TAG =
            BeaconObserverService.class.getSimpleName();

    public static final String EXTRA_BEACON_ID =
            "BeaconObserverService.EXTRA_BEACON_ID";

    /**
     * API Key from Developer Console Project.
     * Requires use of a 'browser' key.
     */
    private static final String API_KEY =
            "YOUR_KEY_HERE";

    /**
     * POST requires a Referer header, which is typically
     * set to your company's domain
     */
    private static final String REFERER = "YOUR_DOMAIN_HERE";

    /**
     * Namespaced type of attachments created in
     * Developer Console Project.
     */
    private static final String ATTACHMENT_NAMESPACE =
            "YOUR_NAMESPACED_TYPE_HERE";

    public BeaconObserverService() {
        super(BeaconObserverService.class.getSimpleName());
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (intent == null || !intent.hasExtra(EXTRA_BEACON_ID)) {
            //Nothing we can do here
            return;
        }

        byte[] advertisedId = intent.getByteArrayExtra(EXTRA_BEACON_ID);
        try {
            JSONObject body = getObservedBody(advertisedId, ATTACHMENT_NAMESPACE);
            String response = postBeaconObserved(body.toString());
            List<String> attachments = parseAttachments(response);

            Log.i(TAG, "Attachments:");
            for (String attachment : attachments) {
                Log.i(TAG, attachment);
            }
        } catch (JSONException e) {
            Log.w(TAG, "Unable to process observed POST body", e);
        } catch (IOException e) {
            Log.w(TAG, "Unable to process POST to API", e);
        }
    }

    private JSONObject getObservedBody(byte[] advertisement, String namespace)
            throws JSONException {
        int packetLength = 16;
        int offset = advertisement.length - packetLength;
        String id = Base64.encodeToString(advertisement,
                offset, packetLength, Base64.NO_WRAP);

        SimpleDateFormat sdf =
                new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
        return new JSONObject()
                .put("observations", new JSONArray()
                        .put(new JSONObject()
                            .put("advertisedId", new JSONObject()
                                    .put("type", "EDDYSTONE")
                                    .put("id", id)
                            ).put("timestampMs", sdf.format(new Date()))
                        )
                    ).put("namespacedTypes", namespace);
    }

    private String postBeaconObserved(String body) throws IOException {
        URL url = new URL("https://proximitybeacon.googleapis.com/v1beta1/"
                + "beaconinfo:getforobserved?key=" + API_KEY);
        HttpURLConnection connection = (HttpURLConnection)url.openConnection();

        connection.setDoOutput(true);
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Referer", REFERER);
        connection.setRequestProperty("Content-Type", "application/json");
        connection.connect();

        //Upload body
        Writer out = new OutputStreamWriter(connection.getOutputStream(), "UTF-8");
        out.write(body);
        out.flush();
        out.close();

        //Download response
        if (connection.getResponseCode() != 200) {
            Log.w(TAG, connection.getResponseMessage());
        }
        StringBuilder sb = new StringBuilder();
        BufferedReader br = new BufferedReader(new InputStreamReader(
                connection.getInputStream(), "UTF-8"));
        String line;
        while ((line = br.readLine()) != null) {
            sb.append(line + "\n");
        }
        br.close();

        connection.disconnect();

        return sb.toString();
    }

    private List<String> parseAttachments(String response)
            throws JSONException {
        List<String> parsed = new ArrayList<>();
        JSONObject object = new JSONObject(response);

        JSONArray attachments = object.getJSONArray("beacons")
                .getJSONObject(0)
                .getJSONArray("attachments");

        for (int i=0; i < attachments.length(); i++) {
            String encoded = attachments.getJSONObject(i).getString("data");
            String decoded = new String(Base64.decode(encoded, Base64.NO_WRAP));
            parsed.add(decoded);
        }

        return parsed;
    }
}

package com.ikame.aift.utils;

import com.google.gson.reflect.TypeToken;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.slack.api.socket_mode.SocketModeClient.GSON;

@Slf4j
public class FunctionHelper {
    public static void main(String[] args) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss v");
        System.out.println(ZonedDateTime.now(ZoneId.of("UTC")).format(formatter));
    }

    public static String predictReviewCustomModel(String review) {
        try {
            URL url = new URL("https://ai.ikamegroup.com/model");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            String encoded = Base64.getEncoder().encodeToString(("ikame"+":"+"ikamemobi07").getBytes(StandardCharsets.UTF_8));
            conn.setRequestProperty("Authorization", "Basic "+encoded);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Accept", "application/json");
            conn.setDoOutput(true);
            String jsonBody = "{ \"review\": " + "\"" + review + "\"" + "}";
            try(OutputStream os = conn.getOutputStream()) {
                byte[] input = jsonBody.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }
            try(BufferedReader br = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder response = new StringBuilder();
                String responseLine = null;
                while ((responseLine = br.readLine()) != null) {
                    response.append(responseLine.trim());
                }

                List<List<Map<String, Object>>> result = GSON.fromJson(response.toString(), new TypeToken<List<List<Map<String, Object>>>>(){}.getType());
                return String.join(", ", result.get(0).stream().filter(item -> Double.parseDouble(item.get("score").toString()) > 0.9).map(item -> item.get("label").toString()).toList());
            }

        } catch (IOException e) {
            log.error("Predict error: {}", e.getMessage());
            return "";
        }
    }

    public static HttpURLConnection postHttpURLConnection(String linkUrl, String jsonBody,
                                                           Map<String, String> requestProperty
    ) throws IOException {
        URL url = new URL(linkUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Authorization", "Bearer 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");
        requestProperty.keySet().forEach(item -> {
            conn.setRequestProperty(item, requestProperty.get(item));
        });
        conn.setDoOutput(true);
        try(OutputStream os = conn.getOutputStream()) {
            byte[] input = jsonBody.getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);
        }
        return conn;
    }
    public static HttpURLConnection getHttpURLConnection(String linkUrl, Map<String, String> requestProperty) throws IOException {
        URL url = new URL(linkUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Accept", "application/json");
        requestProperty.keySet().forEach(item -> {
            conn.setRequestProperty(item, requestProperty.get(item));
        });
        conn.setDoOutput(true);
        return conn;
    }
    public static String epochSecondToZoneDateTime(Long epochSecond) {
        Instant instant = Instant.ofEpochSecond(epochSecond);
        ZoneId zoneId = ZoneId.of("UTC");
        return instant.atZone(zoneId).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z"));
    }
}

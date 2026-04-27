package com.msda.android;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import androidx.annotation.WorkerThread;

public class HttpHelper {
    @WorkerThread
    public static String performRefresh(String steamId, String deviceId) {
        try {
            // Obtain the current refresh token from the native active account.
            String activeJson = NativeBridge.INSTANCE.getActiveAccount();
            String refreshToken = "";
            if (activeJson != null && !activeJson.isEmpty()) {
                try {
                    JSONObject obj = new JSONObject(activeJson);
                    refreshToken = obj.optString("refreshToken", "");
                } catch (Exception ignored) { }
            }

            URL url = new URL("https://api.steampowered.com/IMobileLoginService/LoginToken/v1/");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setDoInput(true);
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);

            // Build POST body required by Steam's refresh endpoint.
            String postData = "steamid=" + steamId
                            + "&refresh_token=" + ((refreshToken != null) ? refreshToken : "")
                            + "&device_id=" + deviceId;

            try (DataOutputStream out = new DataOutputStream(conn.getOutputStream())) {
                out.writeBytes(postData);
                out.flush();
            }

            int respCode = conn.getResponseCode();
            if (respCode != HttpURLConnection.HTTP_OK) {
                return "";
            }

            BufferedReader reader = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            reader.close();
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    @WorkerThread
    public static String performReauthWithPassword(String steamId, String password, String deviceId) {
        try {
            // Generate a fresh 2FA TOTP code using the native library.
            String totpCode = NativeBridge.INSTANCE.getActiveCode();

            URL url = new URL("https://steamcommunity.com/mobilelogin/dologin/");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setDoInput(true);
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);

            // Build POST body for the mobile login endpoint.
            String postData = "username=" + steamId
                            + "&password=" + password
                            + "&twofactorcode=" + ((totpCode != null) ? totpCode : "")
                            + "&device_id=" + deviceId;

            try (DataOutputStream out = new DataOutputStream(conn.getOutputStream())) {
                out.writeBytes(postData);
                out.flush();
            }

            int respCode = conn.getResponseCode();
            if (respCode != HttpURLConnection.HTTP_OK) {
                return "";
            }

            BufferedReader reader = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            reader.close();
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }
}

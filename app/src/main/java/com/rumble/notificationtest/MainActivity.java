package com.rumble.notificationtest;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.messaging.FirebaseMessaging;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    public static final String PREFS_NAME = "rumble-fcm-test";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Map<String, EditText> fields = new LinkedHashMap<>();

    private SharedPreferences prefs;
    private TextView status;
    private TextView output;
    private String ownerToken = "";
    private String likerToken = "";
    private String fcmToken = "";
    private String registeredTokenHash = "";

    private final BroadcastReceiver fcmReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String payload = intent.getStringExtra(RumbleFirebaseMessagingService.EXTRA_PAYLOAD);
            if (payload == null) payload = "{}";
            log("FCM message", payload);
            refreshOwnerNotificationViews();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        fcmToken = prefs.getString("fcmToken", "");
        ownerToken = prefs.getString("ownerToken", "");
        likerToken = prefs.getString("likerToken", "");
        registeredTokenHash = prefs.getString("registeredTokenHash", "");

        setContentView(buildUi());

        String latestPayload = prefs.getString("latestFcmPayload", "");
        if (!latestPayload.isEmpty()) {
            log("Latest saved FCM payload", latestPayload);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        IntentFilter filter = new IntentFilter(RumbleFirebaseMessagingService.ACTION_FCM_MESSAGE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(fcmReceiver, filter, RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(fcmReceiver, filter);
        }
    }

    @Override
    protected void onStop() {
        unregisterReceiver(fcmReceiver);
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }

    private View buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(16), dp(16), dp(24));
        scroll.addView(root);

        TextView title = text("Rumble Firebase Notification Test", 22, Typeface.BOLD);
        root.addView(title);

        status = text("Ready.", 14, Typeface.NORMAL);
        status.setPadding(0, dp(4), 0, dp(12));
        root.addView(status);

        addSection(root, "API");
        addField(root, "apiBaseUrl", "API base URL", "http://10.0.2.2:5105", false);

        addSection(root, "Post Owner Device");
        addField(root, "ownerSchoolCode", "Owner school code", "string", false);
        addField(root, "ownerStudentCode", "Owner student code", "string", false);
        addField(root, "ownerPassword", "Owner password", "", true);
        addField(root, "platform", "Platform", "android", false);
        addField(root, "deviceId", "Device ID", "android-fcm-test", false);
        addField(root, "appVersion", "App version", "dev", false);
        addButtonRow(root,
            button("Login Owner", v -> loginOwner()),
            button("Permission", v -> requestNotificationPermission()));
        addButtonRow(root,
            button("Get FCM Token", v -> getFcmToken()),
            button("Copy Token", v -> copyFcmToken()));
        addButtonRow(root,
            button("Register Token", v -> registerDevice()),
            button("Delete Backend Token", v -> deleteBackendToken()));
        addButtonRow(root,
            button("Delete Firebase Token", v -> deleteFirebaseToken()),
            button("Unread Count", v -> unreadCount()));
        addButtonRow(root, button("List Notifications", v -> listNotifications()));

        addSection(root, "Trigger Like");
        addField(root, "contentId", "Post content ID", "3", false);
        addField(root, "likerSchoolCode", "Liker school code", "string", false);
        addField(root, "likerStudentCode", "Liker student code", "string1", false);
        addField(root, "likerPassword", "Liker password", "", true);
        addButtonRow(root,
            button("Login Liker", v -> loginLiker()),
            button("Unlike Then Like", v -> triggerLike()));

        addSection(root, "Output");
        output = text("", 12, Typeface.MONOSPACE);
        output.setPadding(dp(10), dp(10), dp(10), dp(10));
        output.setBackgroundColor(0xff101820);
        output.setTextColor(0xffd8f3dc);
        root.addView(output, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(260)));

        return scroll;
    }

    private void loginOwner() {
        runAsync("Owner login", () -> {
            JSONObject result = loginStudent(value("ownerSchoolCode"), value("ownerStudentCode"), value("ownerPassword"));
            ownerToken = result.getJSONObject("data").getString("accessToken");
            prefs.edit().putString("ownerToken", ownerToken).apply();
            return pretty(result);
        });
    }

    private void loginLiker() {
        runAsync("Liker login", () -> {
            JSONObject result = loginStudent(value("likerSchoolCode"), value("likerStudentCode"), value("likerPassword"));
            likerToken = result.getJSONObject("data").getString("accessToken");
            prefs.edit().putString("likerToken", likerToken).apply();
            return pretty(result);
        });
    }

    private JSONObject loginStudent(String schoolCode, String studentId, String password) throws Exception {
        JSONObject body = new JSONObject()
            .put("role", "Student")
            .put("schoolCode", schoolCode)
            .put("studentId", studentId)
            .put("password", password);
        return requestJson("/api/mobile/auth/login", "POST", body, "");
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            setStatus("Notification permission not required on this Android version.");
            return;
        }

        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            setStatus("Notification permission already granted.");
            return;
        }

        requestPermissions(new String[] { Manifest.permission.POST_NOTIFICATIONS }, 1001);
    }

    private void getFcmToken() {
        setStatus("Requesting FCM token...");
        FirebaseMessaging.getInstance().getToken().addOnCompleteListener(task -> {
            if (!task.isSuccessful()) {
                log("Get FCM token failed", task.getException() == null ? "Unknown error" : task.getException().toString());
                setStatus("Could not get FCM token.");
                return;
            }

            fcmToken = task.getResult();
            prefs.edit().putString("fcmToken", fcmToken).apply();
            log("FCM token", fcmToken);
            setStatus("FCM token received.");
        });
    }

    private void copyFcmToken() {
        if (fcmToken.isEmpty()) {
            toast("Get FCM token first.");
            return;
        }

        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText("FCM token", fcmToken));
        toast("FCM token copied.");
    }

    private void registerDevice() {
        runAsync("Register device token", () -> {
            requireOwnerToken();
            if (fcmToken.isEmpty()) throw new IllegalStateException("Get FCM token first.");

            JSONObject body = new JSONObject()
                .put("token", fcmToken)
                .put("platform", value("platform"))
                .put("deviceId", value("deviceId"))
                .put("appVersion", value("appVersion"));

            JSONObject result = requestJson("/api/notification/devices/register", "POST", body, ownerToken);
            registeredTokenHash = result.getJSONObject("data").getString("tokenHash");
            prefs.edit().putString("registeredTokenHash", registeredTokenHash).apply();
            return pretty(result);
        });
    }

    private void deleteBackendToken() {
        runAsync("Delete backend token", () -> {
            requireOwnerToken();
            if (registeredTokenHash.isEmpty()) throw new IllegalStateException("Register a token first.");

            JSONObject result = requestJson(
                "/api/notification/devices/" + urlEncode(registeredTokenHash),
                "DELETE",
                null,
                ownerToken);
            registeredTokenHash = "";
            prefs.edit().remove("registeredTokenHash").apply();
            return pretty(result);
        });
    }

    private void deleteFirebaseToken() {
        setStatus("Deleting Firebase token...");
        FirebaseMessaging.getInstance().deleteToken().addOnCompleteListener(task -> {
            if (!task.isSuccessful()) {
                log("Delete Firebase token failed", task.getException() == null ? "Unknown error" : task.getException().toString());
                setStatus("Could not delete Firebase token.");
                return;
            }

            fcmToken = "";
            prefs.edit().remove("fcmToken").apply();
            log("Firebase token deleted", "Call Get FCM Token to request a new token.");
            setStatus("Firebase token deleted.");
        });
    }

    private void unreadCount() {
        runAsync("Unread count", () -> {
            requireOwnerToken();
            return pretty(requestJson("/api/notification/notifications/unread-count", "GET", null, ownerToken));
        });
    }

    private void listNotifications() {
        runAsync("Notifications", () -> {
            requireOwnerToken();
            return pretty(requestJson("/api/notification/notifications?startPage=1&pageLength=10", "GET", null, ownerToken));
        });
    }

    private void refreshOwnerNotificationViews() {
        if (ownerToken.isEmpty()) return;
        runAsync("Auto unread count", () ->
            pretty(requestJson("/api/notification/notifications/unread-count", "GET", null, ownerToken)));
    }

    private void triggerLike() {
        runAsync("Unlike then like", () -> {
            if (likerToken.isEmpty()) throw new IllegalStateException("Login liker first.");

            String id = urlEncode(value("contentId"));
            requestText("/api/social-content/posts/" + id + "/like?entityType=Image", "DELETE", null, likerToken);
            JSONObject result = requestJson("/api/social-content/posts/" + id + "/like?entityType=Image", "PUT", null, likerToken);
            return pretty(result);
        });
    }

    private JSONObject requestJson(String path, String method, JSONObject body, String bearerToken) throws Exception {
        String text = requestText(path, method, body, bearerToken);
        return text.isEmpty() ? new JSONObject() : new JSONObject(text);
    }

    private String requestText(String path, String method, JSONObject body, String bearerToken) throws Exception {
        saveFields();
        HttpURLConnection connection = (HttpURLConnection) new URL(apiUrl(path)).openConnection();
        connection.setRequestMethod(method);
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(30000);
        connection.setRequestProperty("Accept", "application/json");
        if (!bearerToken.isEmpty()) {
            connection.setRequestProperty("Authorization", "Bearer " + bearerToken);
        }

        if (body != null) {
            byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setFixedLengthStreamingMode(bytes.length);
            try (OutputStream output = connection.getOutputStream()) {
                output.write(bytes);
            }
        }

        int statusCode = connection.getResponseCode();
        String response = readAll(statusCode >= 400 ? connection.getErrorStream() : connection.getInputStream());
        if (statusCode >= 400) {
            throw new IllegalStateException(statusCode + " " + connection.getResponseMessage() + ": " + response);
        }

        return response;
    }

    private void runAsync(String label, Callable<String> work) {
        hideKeyboard();
        setStatus(label + "...");
        executor.execute(() -> {
            try {
                String result = work.call();
                runOnUiThread(() -> {
                    log(label, result);
                    setStatus(label + " completed.");
                });
            } catch (Exception ex) {
                runOnUiThread(() -> {
                    log(label + " failed", ex.toString());
                    setStatus(label + " failed.");
                });
            }
        });
    }

    private void requireOwnerToken() {
        if (ownerToken.isEmpty()) throw new IllegalStateException("Login owner first.");
    }

    private String apiUrl(String path) {
        String base = value("apiBaseUrl").replaceAll("/+$", "");
        return base + path;
    }

    private String value(String key) {
        EditText field = fields.get(key);
        return field == null ? "" : field.getText().toString().trim();
    }

    private void saveFields() {
        SharedPreferences.Editor editor = prefs.edit();
        for (Map.Entry<String, EditText> entry : fields.entrySet()) {
            editor.putString(entry.getKey(), entry.getValue().getText().toString());
        }
        editor.apply();
    }

    private void log(String label, String data) {
        String line = "[" + Instant.now() + "] " + label + "\n" + data + "\n\n";
        output.setText(line + output.getText());
    }

    private void setStatus(String text) {
        status.setText(text);
    }

    private static String readAll(InputStream stream) throws Exception {
        if (stream == null) return "";
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }
        }
        return builder.toString();
    }

    private static String pretty(JSONObject json) throws Exception {
        return json.toString(2);
    }

    private static String urlEncode(String value) throws Exception {
        return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8.name());
    }

    private Button button(String text, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setOnClickListener(listener);
        return button;
    }

    private void addButtonRow(LinearLayout root, Button... buttons) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(4), 0, dp(4));
        for (Button button : buttons) {
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
            params.setMargins(dp(2), 0, dp(2), 0);
            row.addView(button, params);
        }
        root.addView(row);
    }

    private void addSection(LinearLayout root, String title) {
        TextView view = text(title, 17, Typeface.BOLD);
        view.setPadding(0, dp(18), 0, dp(6));
        root.addView(view);
    }

    private void addField(LinearLayout root, String key, String label, String defaultValue, boolean password) {
        TextView labelView = text(label, 13, Typeface.BOLD);
        root.addView(labelView);

        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setText(prefs.getString(key, defaultValue));
        input.setTextSize(14);
        if (password) input.setInputType(0x00000081);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, dp(8));
        root.addView(input, params);
        fields.put(key, input);
    }

    private TextView text(String value, int sp, int style) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTypeface(Typeface.DEFAULT, style);
        return view;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void hideKeyboard() {
        View focus = getCurrentFocus();
        if (focus == null) return;
        InputMethodManager manager = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (manager != null) manager.hideSoftInputFromWindow(focus.getWindowToken(), 0);
    }

    private void toast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }
}

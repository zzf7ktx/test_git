package com.rumble.notificationtest;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import org.json.JSONObject;

import java.util.Map;

public final class RumbleFirebaseMessagingService extends FirebaseMessagingService {
    public static final String ACTION_FCM_MESSAGE = "com.rumble.notificationtest.FCM_MESSAGE";
    public static final String EXTRA_PAYLOAD = "payload";

    private static final String CHANNEL_ID = "rumble_fcm_test";

    @Override
    public void onNewToken(String token) {
        getSharedPreferences(MainActivity.PREFS_NAME, MODE_PRIVATE)
            .edit()
            .putString("fcmToken", token)
            .apply();

        Intent intent = new Intent(ACTION_FCM_MESSAGE);
        intent.setPackage(getPackageName());
        intent.putExtra(EXTRA_PAYLOAD, "{\"event\":\"onNewToken\"}");
        sendBroadcast(intent);
    }

    @Override
    public void onMessageReceived(RemoteMessage message) {
        String payload = toJson(message.getData());
        getSharedPreferences(MainActivity.PREFS_NAME, MODE_PRIVATE)
            .edit()
            .putString("latestFcmPayload", payload)
            .apply();

        Intent intent = new Intent(ACTION_FCM_MESSAGE);
        intent.setPackage(getPackageName());
        intent.putExtra(EXTRA_PAYLOAD, payload);
        sendBroadcast(intent);

        showNotification(payload);
    }

    private void showNotification(String payload) {
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Rumble FCM Test",
                NotificationManager.IMPORTANCE_DEFAULT);
            manager.createNotificationChannel(channel);
        }

        Intent launchIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
            ? new Notification.Builder(this, CHANNEL_ID)
            : new Notification.Builder(this);

        Notification notification = builder
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Rumble notification")
            .setContentText(shortText(payload))
            .setStyle(new Notification.BigTextStyle().bigText(payload))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build();

        manager.notify((int) System.currentTimeMillis(), notification);
    }

    private static String toJson(Map<String, String> data) {
        return new JSONObject(data).toString();
    }

    private static String shortText(String value) {
        return value.length() <= 120 ? value : value.substring(0, 117) + "...";
    }
}


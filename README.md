# Firebase Notification Android Test

Tiny native Android harness for testing Rumble Firebase Cloud Messaging without the main mobile app.

## Setup

1. Open this folder in Android Studio.
2. In Firebase Console, add an Android app with package:

   ```text
   com.rumble.notificationtest
   ```

3. Download `google-services.json` and place it at:

   ```text
   app/google-services.json
   ```

4. Sync Gradle and run the app on an emulator or device.

## API Base URL

- Android emulator to host machine:

  ```text
  http://10.0.2.2:5105
  ```

- Physical device:

  ```text
  http://<your-lan-ip>:5105
  ```

The app enables cleartext HTTP because this is only a local test harness.

## Flow

1. Login as the notification recipient student.
2. Request Android notification permission.
3. Get an FCM token.
4. Register the token with `POST /api/notification/devices/register`.
5. Login as another student and trigger unlike/like on a published post.
6. Watch foreground messages in the app log and background messages as Android notifications.

Use **Delete Backend Token** to revoke the current token hash from Rumble.
Use **Delete Firebase Token** to force Firebase to issue a new token on the next **Get FCM Token** call.


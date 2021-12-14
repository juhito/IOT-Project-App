package com.juhito.iot_project;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.google.android.gms.common.util.SharedPreferencesUtils;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.io.Serializable;
import java.util.Arrays;
import java.util.HashMap;

public class FirebaseService extends FirebaseMessagingService {
    private static final String TAG = "FirebaseMsgService";
    private LocalBroadcastManager broadcastManager;

    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        Log.d(TAG, "From: " + remoteMessage.getFrom());
        broadcastManager = LocalBroadcastManager.getInstance(getBaseContext());

        if(remoteMessage.getNotification() != null) {
            Log.d(TAG, "Message Notification Body: " + remoteMessage.getNotification().getBody());

            Intent intent = new Intent(getString(R.string.broadcast_intent) + "FirebaseService.UPDATE_UI");
            if(remoteMessage.getData().size() > 0) {
                HashMap<String, String> dataMap = new HashMap<>(remoteMessage.getData());

                if(remoteMessage.getData().get("state") != null) {
                    intent.putExtra("state_request", dataMap);
                }
                else {
                    intent.putExtra("data_payload", dataMap);
                }
            }

            broadcastManager.sendBroadcast(intent);
            sendNotification(remoteMessage.getNotification());
        }
        else if(remoteMessage.getData().size() > 0) {
            Intent intent = new Intent(getString(R.string.broadcast_intent) + "FirebaseService.STATE_REQUEST");
            HashMap<String, String> dataMap = new HashMap<>(remoteMessage.getData());

            intent.putExtra("state_request", dataMap);
            broadcastManager.sendBroadcast(intent);
        }

    }

    @Override
    public void onNewToken(@NonNull String token) {
        super.onNewToken(token);
        Log.d(TAG, "Refreshed Token: " + token);

        sendRegistrationToServer(token);
    }

    private void sendRegistrationToServer(String token) {
        SharedPreferences preferences = getApplicationContext().getSharedPreferences(
                getString(R.string.shared_token_preference),
                MODE_PRIVATE
        );

        preferences.edit().putString(getString(R.string.firebase_cloud_messaging_token), token).apply();
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if(user != null) {
            DatabaseReference ref = FirebaseDatabase.getInstance(getString(R.string.region)).getReference();

            ref.child("users/Tokens/" + user.getUid()).setValue(token);
        }
    }

    /*
    Create and show a simple notification containing the received FCM message.
     */
    private void sendNotification(RemoteMessage.Notification notification) {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent,
                PendingIntent.FLAG_ONE_SHOT);

        String channelId = getString(R.string.channel_id);
        Uri defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        NotificationCompat.Builder notificationBuilder =
                new NotificationCompat.Builder(this, channelId)
                .setSmallIcon(R.drawable.ic_launcher_background)
                .setContentTitle(notification.getTitle())
                .setContentText(notification.getBody())
                .setAutoCancel(true)
                .setSound(defaultSoundUri)
                .setContentIntent(pendingIntent);

        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(channelId,
                    getString(R.string.channel_name),
                    NotificationManager.IMPORTANCE_DEFAULT);
            notificationManager.createNotificationChannel(channel);
        }

        notificationManager.notify(0, notificationBuilder.build());
    }
}

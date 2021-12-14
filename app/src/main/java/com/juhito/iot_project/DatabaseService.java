package com.juhito.iot_project;

import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.Calendar;
import java.util.HashMap;

public class DatabaseService extends Service {
    private LocalBroadcastManager broadcastManager;
    private NotificationManager notificationManager;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        return START_STICKY;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT);

        // Create the Foreground Service

        String channelId = getString(R.string.channel_id) + ".DatabaseService";
        Uri defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);

        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this, channelId);
        Notification notification = notificationBuilder.setOngoing(true)
                .setContentTitle("Sensor BackgroundService")
                .setContentText("Click to Open")
                .setSmallIcon(R.drawable.ic_launcher_background)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setAutoCancel(false)
                .setSound(defaultSoundUri)
                .setContentIntent(pendingIntent)
                .build();

        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(channelId,
                    getString(R.string.db_channel_name),
                    NotificationManager.IMPORTANCE_DEFAULT);
            notificationManager.createNotificationChannel(channel);
        }

        broadcastManager = LocalBroadcastManager.getInstance(getBaseContext());
        createDBListener();
        startForeground(1337, notification);
    }

    private void createDBListener() {
        DatabaseReference ref = FirebaseDatabase.getInstance(getString(R.string.region)).getReference().child("/users/notifications");

        ChildEventListener eventListener = new ChildEventListener() {
            @Override
            public void onChildAdded(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
                if(snapshot.getValue() != null) {
                    Intent intent = new Intent(getApplicationContext(), MainActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    PendingIntent pendingIntent = PendingIntent.getActivity(getApplicationContext(), 0, intent,
                            PendingIntent.FLAG_ONE_SHOT);

                    Notification notification = new NotificationCompat.Builder(getApplicationContext(), getString(R.string.channel_id))
                            .setSmallIcon(R.mipmap.ic_launcher)
                            .setPriority(NotificationManager.IMPORTANCE_DEFAULT)
                            .setVibrate(new long[]{2000})
                            .setContentTitle("Sensor triggered!")
                            .setContentText("Click to open app!")
                            .setAutoCancel(true)
                            .setContentIntent(pendingIntent)
                            .build();
                    notificationManager.notify(1028, notification);


                    Intent update_ui_intent = new Intent(getString(R.string.broadcast_intent) + "DatabaseListener.UPDATE_UI");
                    HashMap<String, String> data = new HashMap<>();

                    for(DataSnapshot s : snapshot.getChildren()) {
                        System.out.println(s);
                        if(!s.getKey().equals("server_timestamp")) {
                            data.put("value", s.getValue().toString());
                            data.put("timestamp", s.getKey());
                        }
                    }

                    update_ui_intent.putExtra("data_payload", data);
                    broadcastManager.sendBroadcast(update_ui_intent);
                }
            }

            @Override
            public void onChildChanged(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {

            }

            @Override
            public void onChildRemoved(@NonNull DataSnapshot snapshot) {

            }

            @Override
            public void onChildMoved(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {

            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {

            }
        };

        ref.orderByChild("server_timestamp").startAt(Calendar.getInstance().getTimeInMillis()).addChildEventListener(eventListener);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}

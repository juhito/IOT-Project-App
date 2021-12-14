package com.juhito.iot_project;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.icu.text.SimpleDateFormat;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.navigation.fragment.NavHostFragment;

import com.google.android.gms.tasks.Task;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.messaging.FirebaseMessaging;
import com.juhito.iot_project.databinding.FragmentFirstBinding;

import org.json.JSONObject;
import org.w3c.dom.Text;

import java.net.URISyntaxException;
import java.text.DateFormat;
import java.text.ParseException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;


public class FirstFragment extends Fragment {
    private static final String TAG = "FirstFragment";

    private FragmentFirstBinding binding;

    private TextView textView;
    private ListView listView;
    private SharedPreferences preferences;
    private Context preferencesContext;
    private ArrayList<String> notifications;
    private ArrayAdapter<String> aa;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentFirstBinding.inflate(inflater, container, false);

        IntentFilter filter = new IntentFilter();
        filter.addAction(getString(R.string.broadcast_intent) + "DatabaseListener.UPDATE_UI");
        filter.addAction(getString(R.string.broadcast_intent) + "FirebaseService.STATE_REQUEST");
        filter.addAction(getString(R.string.broadcast_intent) + "FirebaseService.UPDATE_UI");
        LocalBroadcastManager.getInstance(getActivity()).registerReceiver((receiver), filter);

        return binding.getRoot();
    }

    private BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "Broadcast message received!");
            preferences = preferencesContext.getSharedPreferences("test_shared_preferences", Context.MODE_PRIVATE);
            if(intent.getExtras() != null) {
                System.out.println("GOT EXTRAS BOYS");

                if(intent.getSerializableExtra("data_payload") != null) {
                    HashMap<String, String> data_payload = (HashMap<String, String>) intent.getSerializableExtra("data_payload");
                    preferences.edit().putString("notification_value", data_payload.get("value")).apply();
                    preferences.edit().putString("notification_ts", data_payload.get("timestamp")).apply();

                    if(getView() != null)
                        textView.setText("Latest trigger\nSensor value: " + data_payload.get("value") + ", on: " + getDateFormatted(data_payload.get("timestamp")));
                }
                else if(intent.getSerializableExtra("state_request") != null) {
                    System.out.println("STATE REQUEST!!!!");
                    HashMap<String, String> state_request = (HashMap<String, String>) intent.getSerializableExtra("state_request");
                    preferences.edit().putString("sensor_state", state_request.get("state")).apply();

                    if(getView() != null)
                        binding.buttonFirst.setText(state_request.get("state").equals("true") ? "Start" : "Pause");

                }
            }
        }
    };

    private String getDateFormatted(String ts) {
        Date date = new Date(Long.parseLong(ts));

        Calendar cal = Calendar.getInstance();
        cal.setTime(date);

        return String.format(Locale.ENGLISH, "%d %d %d on %d:%d", cal.get(Calendar.DAY_OF_MONTH),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.YEAR),
                cal.get(Calendar.HOUR_OF_DAY),
                cal.get(Calendar.MINUTE));
    }

    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        textView = getView().findViewById(R.id.lastData);
        listView = getView().findViewById(R.id.latestNotifications);

        preferencesContext = getActivity();
        notifications = new ArrayList<String>();
        aa = new ArrayAdapter<>(getContext(),
                android.R.layout.simple_list_item_1,
                notifications);
        listView.setAdapter(aa);
        preferences = preferencesContext.getSharedPreferences("test_shared_preferences", Context.MODE_PRIVATE);

        sendStateRequest();
        getLatestNotifications();

        String notification_value = preferences.getString("notification_value", "");
        String buttonState = preferences.getString("sensor_state", "Unknown");

        if(!notification_value.isEmpty()) {
            textView.setText("Latest trigger\nSensor value: " + preferences.getString("notification_value", "") + ", on: " +
                    getDateFormatted(preferences.getString("notification_ts", "")));
        }
        else {
            textView.setText("No data found!");
        }
        binding.buttonFirst.setText(buttonState.equals("true") ? "Start" : "Pause");
        binding.buttonFirst.setOnClickListener(view1 -> {
            FirebaseDatabase db = FirebaseDatabase.getInstance(getString(R.string.region));

            DatabaseReference ref = db.getReference().child("users/pauseRequests");

            FirebaseMessaging.getInstance().getToken().addOnCompleteListener(task -> {
                if(task.isSuccessful()) {

                    Map pauseRequest = new HashMap<>();
                    pauseRequest.put("token", task.getResult());
                    pauseRequest.put("pause", true);

                    ref.push().setValue(pauseRequest);
                    Log.d(TAG, "DATABASE UPDATE SUCCESS!");
                }
                else {
                    Log.w(TAG, "Can't get token", task.getException());
                }
            });
        });
    }

    private void sendStateRequest() {
        FirebaseDatabase db = FirebaseDatabase.getInstance(getString(R.string.region));
        DatabaseReference stateref = db.getReference().child("users/stateRequests");

        FirebaseMessaging.getInstance().getToken().addOnCompleteListener(task -> {
            if(task.isSuccessful()) {


                Map stateRequest = new HashMap<>();
                stateRequest.put("token", task.getResult());

                stateref.push().setValue(stateRequest);
                Log.d(TAG, "DATABASE UPDATE SUCCESS!");
            }
            else {
                Log.w(TAG, "Can't get token", task.getException());
            }
        });
    }

    private void getLatestNotifications() {
        DatabaseReference ref = FirebaseDatabase.getInstance(getString(R.string.region)).getReference().child("/users/notifications");

        ref.orderByChild("server_timestamp").limitToLast(5).addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if(snapshot.getValue() != null) {
                    aa.clear();
                    for(DataSnapshot s : snapshot.getChildren()) {
                       for(DataSnapshot d : s.getChildren()) {
                           if(!d.getKey().equals("server_timestamp")) {
                               notifications.add("Sensor value: " + d.getValue() + ", on: " + getDateFormatted(d.getKey()));
                           }
                       }
                    }
                    System.out.println("UPDATED DATASET");
                    aa.notifyDataSetChanged();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {

            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

}
package com.example.collegebustracker;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.google.android.gms.location.*;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class LocationService extends Service {

    private static final String CHANNEL_ID = "bus_tracker_channel";
    private static final int NOTIF_ID = 1;
    private static final String TAG = "LocationService";

    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private String busId;
    private FirebaseFirestore db;

    public class LocationBinder extends Binder {
        LocationService getService() {
            return LocationService.this;
        }
    }
    private final IBinder binder = new LocationBinder();

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        db = FirebaseFirestore.getInstance();
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        busId = intent.getStringExtra("busId");
        if (busId == null) {
            Log.e(TAG, "busId is null, service cannot proceed");
            stopSelf();
            return START_NOT_STICKY;
        }
        Log.d(TAG, "Starting with busId: " + busId);
        startForeground(NOTIF_ID, createNotification());
        startLocationUpdates();
        return START_STICKY;
    }

    private Notification createNotification() {
        Intent notifIntent = new Intent(this, DriverBusTrackingActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notifIntent, PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Bus Tracking Ongoing")
                .setContentText("Tracking enabled for bus: " + busId)
                .setSmallIcon(R.drawable.ic_bus)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                    "Bus Tracker", NotificationManager.IMPORTANCE_LOW);
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
    }

    private void startLocationUpdates() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "Location permission denied, stopping service");
            stopSelf();
            return;
        }

        LocationRequest req = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000)
                .setWaitForAccurateLocation(false)
                .build();

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult result) {
                if (result == null) return;
                for (Location loc : result.getLocations()) {
                    sendLocationToFirestore(loc.getLatitude(), loc.getLongitude());
                }
            }
        };

        fusedLocationClient.requestLocationUpdates(req, locationCallback, Looper.getMainLooper());
    }

    private void sendLocationToFirestore(double latitude, double longitude) {
        if (busId == null) {
            Log.e(TAG, "busId is null, cannot upload location");
            return;
        }
        Map<String, Object> loc = new HashMap<>();
        loc.put("latitude", latitude);
        loc.put("longitude", longitude);
        loc.put("timestamp", System.currentTimeMillis());

        Map<String, Object> updateMap = new HashMap<>();
        updateMap.put("lastLocation", loc);

        db.collection("buses").document(busId)
                .update(updateMap)
                .addOnSuccessListener(aVoid -> Log.d(TAG, "Location updated for busId: " + busId))
                .addOnFailureListener(e -> Log.e(TAG, "Failed to update location: " + e.getMessage(), e));
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
        }
    }
}
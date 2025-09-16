package com.example.collegebustracker;

import android.Manifest;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.*;
import com.google.android.gms.maps.*;
import com.google.android.gms.maps.model.*;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class DriverBusTrackingActivity extends AppCompatActivity implements OnMapReadyCallback {

    private static final String TAG = "DriverBusTracking";
    private static final int LOCATION_PERMISSION_REQUEST = 1001;

    private GoogleMap mMap;
    private Marker busMarker;
    private Button btnStartStop, btnEmergency;
    private TextView tvStatus, tvLocationInfo;

    private boolean isTracking = false;
    private String busId;
    private FirebaseFirestore db;
    private FusedLocationProviderClient fusedLocationProviderClient;
    private LocationCallback locationCallback;  // For removal

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_driver_bus_tracking);

        db = FirebaseFirestore.getInstance();
        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);

        // Get busId (from SharedPreferences or intent)
        SharedPreferences prefs = getSharedPreferences("user_prefs", MODE_PRIVATE);
        busId = prefs.getString("busId", "test_bus_001");

        tvStatus = findViewById(R.id.tvStatus);
        tvLocationInfo = findViewById(R.id.tvLocationInfo);
        btnStartStop = findViewById(R.id.btnStartStop);
        btnEmergency = findViewById(R.id.btnEmergency);

        btnStartStop.setOnClickListener(v -> {
            if (isTracking) stopTracking();
            else startTracking();
        });

        btnEmergency.setOnClickListener(v ->
                Toast.makeText(this, "Emergency reporting coming soon.", Toast.LENGTH_SHORT).show()
        );

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.mapFragment);
        if (mapFragment != null) mapFragment.getMapAsync(this);

        if (!hasLocationPermission()) requestLocationPermissions();
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        mMap = googleMap;
        if (hasLocationPermission()) {
            try { mMap.setMyLocationEnabled(true); } catch (SecurityException ignored) {}
        }
        LatLng defaultLoc = new LatLng(28.6139, 77.2090);  // Change as needed
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(defaultLoc, 16));
    }

    private void startTracking() {
        if (!hasLocationPermission()) {
            requestLocationPermissions();
            return;
        }
        isTracking = true;
        btnStartStop.setText("Stop Tracking");
        updateStatus("Tracking active");
        startLocationUpdates();
        Toast.makeText(this, "Tracking started", Toast.LENGTH_SHORT).show();
    }

    private void stopTracking() {
        isTracking = false;
        btnStartStop.setText("Start Tracking");
        updateStatus("Tracking stopped");
        stopLocationUpdates();
        Toast.makeText(this, "Tracking stopped", Toast.LENGTH_SHORT).show();
    }

    private void startLocationUpdates() {
        if (!hasLocationPermission()) return;

        LocationRequest request = LocationRequest.create()
                .setInterval(5000).setFastestInterval(3000)
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult result) {
                Location location = result.getLastLocation();
                if (location != null) {
                    double lat = location.getLatitude();
                    double lng = location.getLongitude();
                    uploadToFirestore(lat, lng);
                    updateMapMarker(lat, lng);
                }
            }
        };
        try {
            fusedLocationProviderClient.requestLocationUpdates(request, locationCallback, null);
        } catch (SecurityException e) {
            e.printStackTrace();
            // Inform user or disable location-dependent features
        }

    }

    private void stopLocationUpdates() {
        if (locationCallback != null)
            fusedLocationProviderClient.removeLocationUpdates(locationCallback);
    }

    private void uploadToFirestore(double latitude, double longitude) {
        if (busId == null) return;
        Map<String, Object> locationMap = new HashMap<>();
        locationMap.put("latitude", latitude);
        locationMap.put("longitude", longitude);
        locationMap.put("timestamp", System.currentTimeMillis());

        db.collection("buses")
                .document(busId)
                .set(Collections.singletonMap("lastLocation", locationMap), SetOptions.merge())
                .addOnSuccessListener(aVoid -> Log.d(TAG, "Location updated"))
                .addOnFailureListener(e -> Log.e(TAG, "Failed to update location", e));
    }

    private void updateMapMarker(double latitude, double longitude) {
        LatLng pos = new LatLng(latitude, longitude);
        tvLocationInfo.setText(String.format("Latitude: %.5f\nLongitude: %.5f", latitude, longitude));
        if (busMarker == null && mMap != null) {
            busMarker = mMap.addMarker(new MarkerOptions()
                    .position(pos)
                    .title("Bus")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE)));
            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(pos, 16));
        } else if (busMarker != null) {
            busMarker.setPosition(pos);
        }
    }

    private void updateStatus(String status) {
        tvStatus.setText(status);
    }

    private boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestLocationPermissions() {
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION},
                LOCATION_PERMISSION_REQUEST);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopLocationUpdates();
    }
}

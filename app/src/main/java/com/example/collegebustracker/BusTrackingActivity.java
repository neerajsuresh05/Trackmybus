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

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;

import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.SetOptions;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class BusTrackingActivity extends AppCompatActivity implements OnMapReadyCallback {

    private static final String TAG = "BusTrackingActivity";
    private static final int LOCATION_PERMISSION_REQUEST = 1001;

    private GoogleMap mMap;
    private Marker busMarker;

    private TextView tvStatus, tvLocationInfo;
    private Button btnStartStop, btnEmergency;

    private boolean isTracking = false;

    private String userType, busId, userId;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    private ListenerRegistration locationListener;

    private FusedLocationProviderClient fusedLocationProviderClient;
    private LocationCallback locationCallback;  // Keep reference for removal

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bus_tracking);

        // Initialize Firebase Auth and Firestore
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);

        getUserData();
        initViews();
        setupMap();
        setupListeners();

        // Check and request location permission if needed
        if (!hasLocationPermission()) {
            requestLocationPermissions();
        }
    }

    private void getUserData() {
        userType = getIntent().getStringExtra("userType");

        SharedPreferences prefs = getSharedPreferences("user_prefs", MODE_PRIVATE);
        userId = prefs.getString("userId", "");
        busId = prefs.getString("busId", "test_bus_001"); // Default bus ID for testing

        Log.d(TAG, "User type: " + userType + ", Bus ID: " + busId);
    }

    private void initViews() {
        tvStatus = findViewById(R.id.tvStatus);
        tvLocationInfo = findViewById(R.id.tvLocationInfo);
        btnStartStop = findViewById(R.id.btnStartStop);
        btnEmergency = findViewById(R.id.btnEmergency);

        if ("student".equalsIgnoreCase(userType)) {
            btnStartStop.setText("Find Bus");
            btnEmergency.setText("Report Issue");
        } else {
            btnStartStop.setText("Start Tracking");
        }
    }

    private void setupMap() {
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.mapFragment);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }
    }

    private void setupListeners() {
        btnStartStop.setOnClickListener(v -> {
            if (isTracking) {
                stopTracking();
            } else {
                if ("driver".equalsIgnoreCase(userType)) {
                    startTracking();
                } else {
                    findBus();
                }
            }
        });

        btnEmergency.setOnClickListener(v -> {
            Toast.makeText(this, "Emergency feature coming soon.", Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        mMap = googleMap;

        if (hasLocationPermission()) {
            try {
                mMap.setMyLocationEnabled(true);
            } catch (SecurityException ex) {
                Log.e(TAG, "SecurityException enabling location: " + ex.getMessage());
            }
        }

        // Center map to default
        LatLng defaultLocation = new LatLng(28.6139, 77.2090);
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(defaultLocation, 15));

        Log.d(TAG, "Map ready");
    }

    private void startTracking() {
        if (!hasLocationPermission()) {
            requestLocationPermissions();
            return;
        }

        isTracking = true;
        btnStartStop.setText("Stop Tracking");
        updateStatus("Tracking active");

        startListeningToLocation();

        Toast.makeText(this, "Tracking started", Toast.LENGTH_SHORT).show();
    }

    private void stopTracking() {
        isTracking = false;
        btnStartStop.setText("Start Tracking");
        updateStatus("Tracking stopped");

        stopListeningToLocation();

        Toast.makeText(this, "Tracking stopped", Toast.LENGTH_SHORT).show();
    }

    private void findBus() {
        if (busId == null || busId.isEmpty()) {
            Toast.makeText(this, "Invalid bus ID", Toast.LENGTH_SHORT).show();
            return;
        }
        subscribeToBusLocation(busId);
        updateStatus("Searching for bus...");
        // Optionally disable the start button after tap
    }

    private void startListeningToLocation() {
        if (!hasLocationPermission()) {
            Log.w(TAG, "Missing location permission, cannot start updates.");
            return;
        }

        LocationRequest locationRequest = LocationRequest.create()
                .setInterval(5000)
                .setFastestInterval(3000)
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                Location location = locationResult.getLastLocation();
                if (location != null) {
                    double lat = location.getLatitude();
                    double lng = location.getLongitude();
                    updateLocationInFirestore(lat, lng);
                    updateBusLocationUI(lat, lng);
                }
            }
        };

        fusedLocationProviderClient.requestLocationUpdates(locationRequest, locationCallback, null);
    }

    private void stopListeningToLocation() {
        if (locationCallback != null) {
            fusedLocationProviderClient.removeLocationUpdates(locationCallback);
            locationCallback = null;
        }
    }

    private void updateLocationInFirestore(double latitude, double longitude) {
        if (busId == null) {
            Log.w(TAG, "Bus ID is null, cannot update location");
            return;
        }

        Map<String, Object> locationMap = new HashMap<>();
        locationMap.put("latitude", latitude);
        locationMap.put("longitude", longitude);
        locationMap.put("timestamp", System.currentTimeMillis());

        db.collection("buses")
                .document(busId)
                .set(Collections.singletonMap("lastLocation", locationMap), SetOptions.merge())
                .addOnSuccessListener(aVoid -> Log.d(TAG, "Location updated in Firestore"))
                .addOnFailureListener(e -> Log.e(TAG, "Failed to update location in Firestore", e));
    }

    private void subscribeToBusLocation(String busId) {
        if (locationListener != null) {
            locationListener.remove();
        }

        locationListener = db.collection("buses")
                .document(busId)
                .addSnapshotListener((documentSnapshot, e) -> {
                    if (e != null) {
                        Log.w(TAG, "Listen failed.", e);
                        return;
                    }

                    if (documentSnapshot != null && documentSnapshot.exists()) {
                        Map<String, Object> loc = (Map<String, Object>) documentSnapshot.get("lastLocation");
                        if (loc != null) {
                            double lat = ((Number) loc.get("latitude")).doubleValue();
                            double lng = ((Number) loc.get("longitude")).doubleValue();

                            runOnUiThread(() -> updateBusLocationUI(lat, lng));
                        }
                    }
                });
    }

    private void updateBusLocationUI(double latitude, double longitude) {
        LatLng pos = new LatLng(latitude, longitude);
        tvLocationInfo.setText(String.format("Latitude: %.5f\nLongitude: %.5f", latitude, longitude));
        if (busMarker == null && mMap != null) {
            busMarker = mMap.addMarker(new MarkerOptions()
                    .position(pos)
                    .title("Bus")
                    .icon(com.google.android.gms.maps.model.BitmapDescriptorFactory.defaultMarker(com.google.android.gms.maps.model.BitmapDescriptorFactory.HUE_BLUE))
            );
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
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == LOCATION_PERMISSION_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Location permission granted", Toast.LENGTH_SHORT).show();
                if ("driver".equalsIgnoreCase(userType) && isTracking) {
                    startListeningToLocation();
                }
                if (mMap != null) {
                    try {
                        mMap.setMyLocationEnabled(true);
                    } catch (SecurityException ex) {
                        Log.e(TAG, "SecurityException enabling location: " + ex.getMessage());
                    }
                }
            } else {
                Toast.makeText(this, "Location permission denied", Toast.LENGTH_SHORT).show();
                updateStatus("Permission denied");
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (locationListener != null) {
            locationListener.remove();
        }
        if (isTracking && fusedLocationProviderClient != null) {
            stopListeningToLocation();
        }
    }
}


package com.example.collegebustracker;

import android.Manifest;
import android.content.Intent;
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

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class DriverBusTrackingActivity extends AppCompatActivity implements OnMapReadyCallback {
    private static final String POLICE_NUMBER = "100";
    private static final String AMBULANCE_NUMBER = "108";
    private static final String CHILD_HELPLINE_NUMBER = "1098";
    private static final String WOMEN_HELPLINE_NUMBER = "1091";

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_driver_bus_tracking);

        db = FirebaseFirestore.getInstance();
        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);

        tvStatus = findViewById(R.id.tvStatus);
        tvLocationInfo = findViewById(R.id.tvLocationInfo);
        btnStartStop = findViewById(R.id.btnStartStop);
        btnEmergency = findViewById(R.id.btnEmergency);

        // Fetch assigned busId from Firestore for logged-in driver
        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        db.collection("users").document(uid)
                .get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        busId = doc.getString("busId");
                        if (busId != null && !busId.isEmpty()) {
                            tvStatus.setText("Assigned Bus: " + busId);
                            btnStartStop.setEnabled(true);
                        } else {
                            tvStatus.setText("No bus assigned.");
                            btnStartStop.setEnabled(false);
                        }
                    } else {
                        tvStatus.setText("User data not found.");
                        btnStartStop.setEnabled(false);
                    }
                })
                .addOnFailureListener(e -> {
                    tvStatus.setText("Error fetching user data");
                    btnStartStop.setEnabled(false);
                });

        btnStartStop.setOnClickListener(v -> {
            if (isTracking) stopTracking();
            else startTracking();
        });

        btnEmergency.setOnClickListener(v ->
                showEmergencyOptions());

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.mapFragment);
        if (mapFragment != null) mapFragment.getMapAsync(this);

        if (!hasLocationPermission()) requestLocationPermissions();
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        mMap = googleMap;
        if (hasLocationPermission()) {
            try {
                mMap.setMyLocationEnabled(true);
            } catch (SecurityException ignored) {
            }
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
        Intent serviceIntent = new Intent(this, LocationService.class);
        serviceIntent.putExtra("busId", busId);
        startService(serviceIntent);
        Toast.makeText(this, "Tracking started", Toast.LENGTH_SHORT).show();
    }

    private void stopTracking() {
        isTracking = false;
        btnStartStop.setText("Start Tracking");
        updateStatus("Tracking stopped");
        stopService(new Intent(this, LocationService.class));
        Toast.makeText(this, "Tracking stopped", Toast.LENGTH_SHORT).show();
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
    private void showEmergencyOptions() {
        String[] options = {"Call Police", "Call Ambulance", "Call Child Helpline", "Call Women Helpline"};
        String[] numbers = {POLICE_NUMBER, AMBULANCE_NUMBER, CHILD_HELPLINE_NUMBER, WOMEN_HELPLINE_NUMBER};

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Select Emergency Service")
                .setItems(options, (dialog, which) -> dialNumber(numbers[which]))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void dialNumber(String number) {
        Intent intent = new Intent(Intent.ACTION_DIAL);
        intent.setData(android.net.Uri.parse("tel:" + number));
        startActivity(intent);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (isTracking) {
            stopService(new Intent(this, LocationService.class));
        }
    }
}
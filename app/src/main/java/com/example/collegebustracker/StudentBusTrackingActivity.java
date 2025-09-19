package com.example.collegebustracker;

import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.maps.*;
import com.google.android.gms.maps.model.*;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.*;

import java.util.Map;

public class StudentBusTrackingActivity extends AppCompatActivity implements OnMapReadyCallback {

    private static final String TAG = "StudentBusTracking";
    private GoogleMap mMap;
    private Marker busMarker;
    private TextView tvStatus, tvLocationInfo;
    private Button btnFindBus, btnEmergency;

    private ListenerRegistration busLocationListener;

    private String busId;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_student_bus_tracking);

        db = FirebaseFirestore.getInstance();

        tvStatus = findViewById(R.id.tvStatus);
        tvLocationInfo = findViewById(R.id.tvLocationInfo);
        btnFindBus = findViewById(R.id.btnFindBus);
        btnEmergency = findViewById(R.id.btnEmergency);

        // Fetch assigned busId on student login
        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        db.collection("users").document(uid)
                .get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        busId = doc.getString("busId");
                        if (busId != null && !busId.isEmpty()) {
                            tvStatus.setText("Your Bus: " + busId);
                            btnFindBus.setEnabled(true);
                        } else {
                            tvStatus.setText("No bus assigned.");
                            btnFindBus.setEnabled(false);
                        }
                    } else {
                        tvStatus.setText("User data not found.");
                        btnFindBus.setEnabled(false);
                    }
                })
                .addOnFailureListener(e -> {
                    tvStatus.setText("Error fetching user data");
                    btnFindBus.setEnabled(false);
                });

        btnFindBus.setOnClickListener(v -> {
            if (busId != null) {
                subscribeToBusLocation(busId);
                updateStatus("Searching for bus...");
            }
        });

        btnEmergency.setOnClickListener(v ->
                Toast.makeText(this, "Emergency feature coming soon.", Toast.LENGTH_SHORT).show()
        );

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.mapFragment);
        if (mapFragment != null) mapFragment.getMapAsync(this);
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        mMap = googleMap;
        LatLng defaultLoc = new LatLng(28.6139, 77.2090);
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(defaultLoc, 16));
    }

    private void subscribeToBusLocation(String busId) {
        if (busLocationListener != null)
            busLocationListener.remove();

        busLocationListener = db.collection("buses").document(busId)
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
                            runOnUiThread(() -> updateBusMarker(lat, lng));
                        }
                    }
                });
    }

    private void updateBusMarker(double latitude, double longitude) {
        LatLng pos = new LatLng(latitude, longitude);
        tvLocationInfo.setText(String.format("Latitude: %.5f\nLongitude: %.5f", latitude, longitude));
        if (busMarker == null && mMap != null) {
            busMarker = mMap.addMarker(new MarkerOptions()
                    .position(pos)
                    .title("Bus")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)));
            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(pos, 16));
        } else if (busMarker != null) {
            busMarker.setPosition(pos);
        }
    }

    private void updateStatus(String status) {
        tvStatus.setText(status);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (busLocationListener != null) busLocationListener.remove();
    }
}

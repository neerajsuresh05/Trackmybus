package com.example.collegebustracker;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.graphics.drawable.Drawable;
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
import com.google.firebase.firestore.*;

import com.google.maps.android.PolyUtil;

import okhttp3.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class StudentBusTrackingActivity extends AppCompatActivity implements OnMapReadyCallback {
    private static final String POLICE_NUMBER = "100";
    private static final String AMBULANCE_NUMBER = "108";
    private static final String CHILD_HELPLINE_NUMBER = "1098";
    private static final String WOMEN_HELPLINE_NUMBER = "1091";
    private static final String TAG = "StudentBusTracking";
    private GoogleMap mMap;
    private Marker busMarker;
    private Marker destinationMarker;
    private BitmapDescriptor busIcon;
    private TextView tvStatus, tvLocationInfo;
    private Button btnFindBus, btnEmergency;

    private ListenerRegistration busLocationListener;

    private enum TripState {WAITING, BOARDED, LEAVING}

    private TripState tripState = TripState.WAITING;

    private LatLng driverLocation;
    private LatLng studentLocation;
    private LatLng collegeLocation = new LatLng(9.510055, 76.551495);
    private LatLng homeLocation;

    private String busId;
    private FirebaseFirestore db;

    private static final String DIRECTIONS_API_KEY = "AIzaSyDcgnMAB6DsrvKWi8JydMPaILHo3Gu9fLI";
    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1010;

    private final List<Polyline> polylines = new ArrayList<>();

    private LatLng lastCameraPosition = null;

    // Handler and Runnable for recenter delay logic
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable recenterRunnable;
    private static final long RECENTER_DELAY_MS = 10000; // 10 seconds delay

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_student_bus_tracking);

        db = FirebaseFirestore.getInstance();

        tvStatus = findViewById(R.id.tvStatus);
        tvLocationInfo = findViewById(R.id.tvLocationInfo);
        btnFindBus = findViewById(R.id.btnFindBus);
        btnEmergency = findViewById(R.id.btnEmergency);

        Button btnBoardBus = findViewById(R.id.btnBoardBus);
        Button btnLeaveCollege = findViewById(R.id.btnLeaveCollege);

        btnBoardBus.setOnClickListener(v -> {
            if (driverLocation == null || studentLocation == null) {
                Toast.makeText(this, "Locations not found.", Toast.LENGTH_SHORT).show();
                return;
            }
            float[] result = new float[1];
            Location.distanceBetween(studentLocation.latitude, studentLocation.longitude,
                    driverLocation.latitude, driverLocation.longitude, result);
            float distanceInMeters = result[0];
            if (distanceInMeters < 50) {
                tripState = TripState.BOARDED;
                showDestinationMarker(collegeLocation, "College");
                updateRoute();
            } else {
                Toast.makeText(this, "Too far from bus to board.", Toast.LENGTH_SHORT).show();
            }
        });

        btnLeaveCollege.setOnClickListener(v -> {
            if (driverLocation == null || studentLocation == null || collegeLocation == null) {
                Toast.makeText(this, "Locations not ready.", Toast.LENGTH_SHORT).show();
                return;
            }
            boolean busNearCollege = isNearLocation(driverLocation, collegeLocation, 50);
            boolean studentNearCollege = isNearLocation(studentLocation, collegeLocation, 50);
            if (busNearCollege && studentNearCollege) {
                tripState = TripState.LEAVING;
                if (destinationMarker != null) destinationMarker.remove();
                showDestinationMarker(homeLocation, "Home");
                updateRoute();
            } else {
                Toast.makeText(this, "Bus and student must be near the college to leave.", Toast.LENGTH_SHORT).show();
            }
        });

        busIcon = getBitmapDescriptorFromVector(this, R.drawable.bus_icon);

        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        db.collection("users").document(uid)
                .get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        busId = doc.getString("busId");
                        if (busId != null && !busId.isEmpty()) {
                            tvStatus.setText("Your Bus: " + busId);
                            btnFindBus.setEnabled(true);
                            Double homeLat = doc.getDouble("homeLat");
                            Double homeLng = doc.getDouble("homeLng");
                            if (homeLat != null && homeLng != null) {
                                homeLocation = new LatLng(homeLat, homeLng);
                            }
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
            if (busId != null && !busId.isEmpty()) {
                subscribeToBusLocation(busId);
                updateStatus("Searching for bus...");
            }
        });

        btnEmergency.setOnClickListener(v -> showEmergencyOptions());

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.mapFragment);
        if (mapFragment != null) mapFragment.getMapAsync(this);

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        if (hasLocationPermission()) {
            startLocationUpdates();
        } else {
            requestLocationPermission();
        }
    }

    private boolean isNearLocation(LatLng location1, LatLng location2, float thresholdMeters) {
        float[] results = new float[1];
        Location.distanceBetween(location1.latitude, location1.longitude,
                location2.latitude, location2.longitude, results);
        return results[0] <= thresholdMeters;
    }

    private BitmapDescriptor getBitmapDescriptorFromVector(Context context, int vectorResId) {
        Drawable vectorDrawable = ContextCompat.getDrawable(context, vectorResId);
        vectorDrawable.setBounds(0, 0, vectorDrawable.getIntrinsicWidth(), vectorDrawable.getIntrinsicHeight());
        Bitmap bitmap = Bitmap.createBitmap(vectorDrawable.getIntrinsicWidth(),
                vectorDrawable.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        vectorDrawable.draw(canvas);
        return BitmapDescriptorFactory.fromBitmap(bitmap);
    }

    private boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestLocationPermission() {
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSION_REQUEST_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,@NonNull String[] permissions,@NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startLocationUpdates();
                if (mMap != null) {
                    if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                        return;
                    }
                    mMap.setMyLocationEnabled(true);
                    mMap.getUiSettings().setMyLocationButtonEnabled(true);
                }
            } else {
                Toast.makeText(this, "Location permission denied.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void startLocationUpdates() {
        LocationRequest locationRequest = LocationRequest.create().setPriority(Priority.PRIORITY_HIGH_ACCURACY).setInterval(10000);

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                if (locationResult == null) return;
                Location lastLocation = locationResult.getLastLocation();
                if (lastLocation != null) {
                    studentLocation = new LatLng(lastLocation.getLatitude(), lastLocation.getLongitude());
                    Log.d(TAG, "Live studentLocation: " + studentLocation);

                    if (mMap != null) {
                        if (lastCameraPosition == null) {
                            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(studentLocation, 16));
                            lastCameraPosition = studentLocation;
                        }

                        if (isLocationVisibleOnMap(studentLocation)) {
                            Log.d(TAG, "Student location visible on map, cancelling recenter.");
                            if (recenterRunnable != null) {
                                handler.removeCallbacks(recenterRunnable);
                            }
                        } else {
                            Log.d(TAG, "Student location NOT visible, scheduling recenter.");
                            if (recenterRunnable != null) {
                                handler.removeCallbacks(recenterRunnable);
                            }
                            recenterRunnable = () -> {
                                if (mMap != null && studentLocation != null) {
                                    Log.d(TAG, "Recentering map to student location...");
                                    mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(studentLocation, 16));
                                    lastCameraPosition = studentLocation;
                                }
                            };
                            handler.postDelayed(recenterRunnable, RECENTER_DELAY_MS);
                        }
                    }
                    updateRoute();
                }
            }
        };

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());
    }

    private boolean isLocationVisibleOnMap(LatLng location) {
        if (mMap == null) return false;
        LatLngBounds bounds = mMap.getProjection().getVisibleRegion().latLngBounds;
        return bounds.contains(location);
    }

    private void showDestinationMarker(LatLng position, String title) {
        if (destinationMarker != null) destinationMarker.remove();
        if (mMap != null && position != null) {
            destinationMarker = mMap.addMarker(new MarkerOptions().position(position).title(title).icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)));
        }
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        mMap = googleMap;
        LatLng defaultLoc = new LatLng(28.6139, 77.2090);
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(defaultLoc, 16));
        if (hasLocationPermission()) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                    ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
            mMap.setMyLocationEnabled(true);
            mMap.getUiSettings().setMyLocationButtonEnabled(true);
        }
    }

    private void subscribeToBusLocation(String busId) {
        if (busLocationListener != null) busLocationListener.remove();

        busLocationListener = db.collection("buses").document(busId).addSnapshotListener((documentSnapshot, e) -> {
            if (e != null) {
                Log.w(TAG, "Listen failed.", e);
                return;
            }
            if (documentSnapshot != null && documentSnapshot.exists()) {
                Object lastLocObj = documentSnapshot.get("lastLocation");
                if (lastLocObj instanceof Map) {
                    Map<String, Object> loc = (Map<String, Object>) lastLocObj;
                    if (loc.containsKey("latitude") && loc.containsKey("longitude")) {
                        double lat = ((Number) loc.get("latitude")).doubleValue();
                        double lng = ((Number) loc.get("longitude")).doubleValue();
                        driverLocation = new LatLng(lat, lng);

                        runOnUiThread(() -> {
                            updateBusMarker(lat, lng);
                            updateRoute();
                            updateBusLocationAddress(lat, lng);
                        });
                    }
                }
            } else {
                Log.w(TAG, "Bus document does not exist.");
            }
        });
    }

    private void updateBusLocationAddress(double latitude, double longitude) {
        new Thread(() -> {
            String addressStr = "Location not available";
            try {
                android.location.Geocoder geocoder = new android.location.Geocoder(this, Locale.getDefault());
                List<android.location.Address> addresses = geocoder.getFromLocation(latitude, longitude, 1);
                if (addresses != null && !addresses.isEmpty()) {
                    android.location.Address address = addresses.get(0);
                    addressStr = address.getAddressLine(0);
                }
            } catch (IOException e) {
                Log.e(TAG, "Geocoder failed", e);
                addressStr = "Unable to get address";
            }
            final String finalAddressStr = addressStr;
            runOnUiThread(() -> tvLocationInfo.setText(finalAddressStr));
        }).start();
    }

    private void updateBusMarker(double latitude, double longitude) {
        LatLng pos = new LatLng(latitude, longitude);

        if (busMarker != null) busMarker.remove();

        if (mMap != null) {
            busMarker = mMap.addMarker(new MarkerOptions().position(pos).title("Bus").icon(busIcon));
            // Removed animateCamera here to prevent zoom jitter
        }
    }

    private void updateRoute() {
        if (driverLocation == null || collegeLocation == null || homeLocation == null || studentLocation == null) {
            Log.d(TAG, "Locations not ready for routing");
            return;
        }

        LatLng origin, destination;
        switch (tripState) {
            case WAITING:
                origin = driverLocation;
                destination = studentLocation;
                break;
            case BOARDED:
                origin = driverLocation;
                destination = collegeLocation;
                break;
            case LEAVING:
                origin = collegeLocation;
                destination = homeLocation;
                break;
            default:
                return;
        }
        drawRoute(mMap, origin, destination, DIRECTIONS_API_KEY);
    }

    private void clearPolylines() {
        for (Polyline polyline : polylines) {
            polyline.remove();
        }
        polylines.clear();
    }

    private void drawRoute(GoogleMap googleMap, LatLng origin, LatLng destination, String apiKey) {
        String url = "https://maps.googleapis.com/maps/api/directions/json?" +
                "origin=" + origin.latitude + "," + origin.longitude +
                "&destination=" + destination.latitude + "," + destination.longitude +
                "&key=" + apiKey;

        OkHttpClient client = new OkHttpClient();

        Request request = new Request.Builder().url(url).build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "Directions API call failed: ", e);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    Log.e(TAG, "Unsuccessful response from Directions API");
                    return;
                }
                String body = response.body().string();

                try {
                    org.json.JSONObject json = new org.json.JSONObject(body);
                    org.json.JSONArray routes = json.getJSONArray("routes");
                    if (routes.length() == 0) {
                        Log.e(TAG, "No route found in directions response.");
                        return;
                    }

                    org.json.JSONObject route = routes.getJSONObject(0);
                    org.json.JSONObject overviewPolyline = route.getJSONObject("overview_polyline");
                    String encodedPoints = overviewPolyline.getString("points");

                    List<LatLng> decodedPath = PolyUtil.decode(encodedPoints);

                    runOnUiThread(() -> {
                        clearPolylines();

                        PolylineOptions options = new PolylineOptions()
                                .addAll(decodedPath)
                                .color(0xFF007AFF)
                                .width(12)
                                .geodesic(true);

                        Polyline polyline = googleMap.addPolyline(options);
                        polylines.add(polyline);

                        if (driverLocation != null) {
                            if (busMarker != null) busMarker.remove();
                            busMarker = googleMap.addMarker(new MarkerOptions()
                                    .position(driverLocation)
                                    .title("Bus")
                                    .icon(busIcon));
                        }

                        if ((tripState == TripState.BOARDED || tripState == TripState.LEAVING) && destination != null) {
                            if (destinationMarker != null) destinationMarker.remove();
                            destinationMarker = googleMap.addMarker(new MarkerOptions()
                                    .position(destination)
                                    .title(tripState == TripState.BOARDED ? "College" : "Home")
                                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)));
                        }

                        LatLngBounds.Builder boundsBuilder = new LatLngBounds.Builder();
                        for (LatLng point : decodedPath) {
                            boundsBuilder.include(point);
                        }
                        LatLngBounds bounds = boundsBuilder.build();

                        if (lastCameraPosition == null || distanceBetweenLatLng(lastCameraPosition, bounds.getCenter()) > 50) {
                            googleMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 100));
                            lastCameraPosition = bounds.getCenter();
                        }
                    });

                } catch (Exception e) {
                    Log.e(TAG, "Failed to parse directions JSON", e);
                }
            }
        });
    }

    private float distanceBetweenLatLng(LatLng pos1, LatLng pos2) {
        float[] results = new float[1];
        Location.distanceBetween(pos1.latitude, pos1.longitude, pos2.latitude, pos2.longitude, results);
        return results[0];
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

    private void updateStatus(String status) {
        tvStatus.setText(status);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (busLocationListener != null) busLocationListener.remove();
        if (fusedLocationClient != null && locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
        }
        if (recenterRunnable != null) {
            handler.removeCallbacks(recenterRunnable);
        }
    }
}

package com.example.collegebustracker;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.*;
import com.google.firebase.firestore.*;

public class LoginActivity extends AppCompatActivity {

    private TextView tvRegisterLink, tvTitle;
    private TextInputEditText etEmail, etPassword;
    private Button btnLogin;
    private ProgressBar progressBar;
    private String userType;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        userType = getIntent().getStringExtra("userType");
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        tvRegisterLink = findViewById(R.id.tvRegisterLink);
        tvTitle = findViewById(R.id.tvTitle);
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        btnLogin = findViewById(R.id.btnLogin);
        progressBar = findViewById(R.id.progressBar);

        if ("driver".equalsIgnoreCase(userType)) { tvTitle.setText("Driver Login"); }
        else { tvTitle.setText("Student Login"); }

        btnLogin.setOnClickListener(v -> performLogin());
        tvRegisterLink.setOnClickListener(v -> {
            Intent reg = new Intent(LoginActivity.this, RegisterActivity.class);
            startActivity(reg);
        });
    }

    private void performLogin() {
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();
        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show();
            return;
        }
        progressBar.setVisibility(android.view.View.VISIBLE);
        btnLogin.setEnabled(false);

        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    progressBar.setVisibility(android.view.View.GONE);
                    btnLogin.setEnabled(true);
                    if (task.isSuccessful()) {
                        FirebaseUser firebaseUser = mAuth.getCurrentUser();
                        if (firebaseUser == null) {
                            Toast.makeText(LoginActivity.this, "Auth failed: User not found", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        // Retrieve user's role from Firestore
                        db.collection("users").document(firebaseUser.getUid())
                                .get()
                                .addOnSuccessListener(doc -> {
                                    if (doc.exists()) {
                                        String role = doc.getString("role");
                                        if (role == null ||
                                                (!role.equalsIgnoreCase(userType) && !role.equalsIgnoreCase("admin"))) {
                                            Toast.makeText(LoginActivity.this, "Incorrect user type", Toast.LENGTH_LONG).show();
                                            mAuth.signOut();
                                            return;
                                        }
                                        // Save user info
                                        SharedPreferences prefs = getSharedPreferences("user_prefs", MODE_PRIVATE);
                                        SharedPreferences.Editor editor = prefs.edit();
                                        editor.putString("userId", firebaseUser.getUid());
                                        editor.putString("email", firebaseUser.getEmail());
                                        editor.putString("role", role);
                                        if (doc.contains("busId") && doc.get("busId") != null)
                                            editor.putString("busId", doc.getString("busId"));
                                        editor.apply();

                                        Toast.makeText(LoginActivity.this, "Login successful", Toast.LENGTH_SHORT).show();
                                        Intent intent;
                                        if ("driver".equalsIgnoreCase(role)) {
                                            intent = new Intent(LoginActivity.this, DriverBusTrackingActivity.class);
                                        } else if ("student".equalsIgnoreCase(role)) {
                                            intent = new Intent(LoginActivity.this, StudentBusTrackingActivity.class);
                                        } else {
                                            Toast.makeText(LoginActivity.this, "Unknown role: " + role, Toast.LENGTH_SHORT).show();
                                            mAuth.signOut();
                                            return;
                                        }
                                        startActivity(intent);
                                        finish();
                                    } else {
                                        Toast.makeText(LoginActivity.this, "User data not found. Contact admin.", Toast.LENGTH_LONG).show();
                                        mAuth.signOut();
                                    }
                                })
                                .addOnFailureListener(e -> {
                                    Toast.makeText(LoginActivity.this, "Failed to retrieve user info", Toast.LENGTH_LONG).show();
                                    mAuth.signOut();
                                });
                    } else {
                        Toast.makeText(LoginActivity.this, "Auth failed: " +
                                (task.getException() != null ? task.getException().getMessage() : "Unknown"), Toast.LENGTH_LONG).show();
                    }
                });
    }
}


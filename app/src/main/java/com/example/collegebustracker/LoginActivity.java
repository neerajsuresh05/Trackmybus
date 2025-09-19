package com.example.collegebustracker;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

public class LoginActivity extends AppCompatActivity {

    private EditText etEmail, etPassword;
    private TextView btnLogin, tvRegister, tvTitle;
    private ProgressBar progressBar;
    private FirebaseAuth mAuth;
    private String userType = "driver"; // default

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        // Find views
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        btnLogin = findViewById(R.id.btnLogin);
        tvRegister = findViewById(R.id.tvRegister);
        progressBar = findViewById(R.id.progressBar);
        tvTitle = findViewById(R.id.tvTitle);
        mAuth = FirebaseAuth.getInstance();

        // Get userType from MainActivity or elsewhere
        userType = getIntent().getStringExtra("userType");
        if (userType != null && userType.equalsIgnoreCase("student")) {
            tvTitle.setText("Student Login");
        } else {
            tvTitle.setText("Driver Login");
            userType = "driver"; // fallback
        }

        // Handle Login click
        btnLogin.setOnClickListener(v -> loginUser());

        // Handle Register click (pass userType forward)
        tvRegister.setOnClickListener(v -> {
            Intent intent = new Intent(LoginActivity.this, RegisterActivity.class);
            intent.putExtra("userType", userType);
            startActivity(intent);
        });
    }

    private void loginUser() {
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        if (email.isEmpty()) {
            etEmail.setError("Email required");
            etEmail.requestFocus();
            return;
        }

        if (password.isEmpty()) {
            etPassword.setError("Password required");
            etPassword.requestFocus();
            return;
        }

        progressBar.setVisibility(View.VISIBLE);

        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    progressBar.setVisibility(View.GONE);
                    if (task.isSuccessful()) {
                        String uid = mAuth.getCurrentUser().getUid();
                        FirebaseFirestore.getInstance().collection("users").document(uid)
                                .get()
                                .addOnSuccessListener(doc -> {
                                    if (doc.exists()) {
                                        String role = doc.getString("role");
                                        Boolean approved = doc.getBoolean("approved");
                                        if (approved == null || !approved) {
                                            Toast.makeText(LoginActivity.this, "Account awaiting admin approval.", Toast.LENGTH_LONG).show();
                                            mAuth.signOut();
                                            return;
                                        }
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
                        Toast.makeText(LoginActivity.this, "Login Failed: " + (task.getException() == null ? "Unknown error" : task.getException().getMessage()), Toast.LENGTH_LONG).show();
                    }
                });
    }
}

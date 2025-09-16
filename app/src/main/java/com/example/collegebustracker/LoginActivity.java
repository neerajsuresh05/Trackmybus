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

public class LoginActivity extends AppCompatActivity {

    private EditText etEmail, etPassword;
    private TextView btnLogin, tvRegister, tvTitle;
    private ProgressBar progressBar;
    private FirebaseAuth mAuth;
    private String userType = "driver"; // default value

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

        // ✅ Get userType from MainActivity
        userType = getIntent().getStringExtra("userType");
        if (userType != null && userType.equalsIgnoreCase("student")) {
            tvTitle.setText("Student Login");
        } else {
            tvTitle.setText("Driver Login");
        }

        // ✅ Handle Login click
        btnLogin.setOnClickListener(v -> loginUser());

        // ✅ Handle Register click (pass same userType forward)
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

        progressBar.setVisibility(View.VISIBLE);

        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    progressBar.setVisibility(View.GONE);
                    if (task.isSuccessful()) {
                        Toast.makeText(LoginActivity.this, userType + " Login Successful", Toast.LENGTH_SHORT).show();
                        startActivity(new Intent(LoginActivity.this, BusTrackingActivity.class));
                        finish();

                    } else {
                        Toast.makeText(LoginActivity.this, "Login Failed: " + task.getException().getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
    }
}

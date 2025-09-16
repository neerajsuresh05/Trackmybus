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

public class RegisterActivity extends AppCompatActivity {

    private EditText etEmail, etPassword;
    private TextView btnRegister, tvLogin;
    private ProgressBar progressBar;
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        btnRegister = findViewById(R.id.btnRegister);  // TextView is okay
        tvLogin = findViewById(R.id.tvLogin);
        progressBar = findViewById(R.id.progressBar); // FIX: Make sure you add this in XML
        mAuth = FirebaseAuth.getInstance();

        btnRegister.setOnClickListener(v -> registerUser());

        tvLogin.setOnClickListener(v -> {
            startActivity(new Intent(RegisterActivity.this, LoginActivity.class));
            finish();
        });
    }

    private void registerUser() {
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

        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    progressBar.setVisibility(View.GONE);

                    btnRegister.setEnabled(true);
                    if(task.isSuccessful()) {
                        FirebaseUser user = mAuth.getCurrentUser();
                        if(user == null) {
                            Toast.makeText(this, "Registration failed: User null", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        Map<String, Object> userData = new HashMap<>();
                        userData.put("email", email);
                        userData.put("role", role);
                        db.collection("users").document(user.getUid())
                                .set(userData)
                                .addOnSuccessListener(aVoid -> {
                                    Toast.makeText(this, "Registration successful!", Toast.LENGTH_SHORT).show();
                                    // Optionally auto-login and direct route
                                    Intent intent;
                                    if ("driver".equals(role)) {
                                        intent = new Intent(RegisterActivity.this, DriverBusTrackingActivity.class);
                                    } else {
                                        intent = new Intent(RegisterActivity.this, StudentBusTrackingActivity.class);
                                    }
                                    startActivity(intent);
                                    finish();

                                })
                                .addOnFailureListener(e ->
                                        Toast.makeText(this, "Failed to save user info: " + e.getMessage(), Toast.LENGTH_LONG).show()
                                );

                    if (task.isSuccessful()) {
                        Toast.makeText(RegisterActivity.this, "Registration Successful", Toast.LENGTH_SHORT).show();
                        startActivity(new Intent(RegisterActivity.this, LoginActivity.class));
                        finish();

                    } else {
                        Toast.makeText(RegisterActivity.this, "Registration Failed: " + task.getException().getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
    }
}

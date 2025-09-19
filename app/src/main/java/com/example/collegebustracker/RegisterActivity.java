package com.example.collegebustracker;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class RegisterActivity extends AppCompatActivity {
    private EditText etEmail, etPassword;
    private RadioGroup rgUserRole;
    private Button btnRegister;
    private ProgressBar progressBar;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        rgUserRole = findViewById(R.id.rgUserRole);
        btnRegister = findViewById(R.id.btnRegister);
        progressBar = findViewById(R.id.progressBar);

        btnRegister.setOnClickListener(v -> registerUser());
    }

    private void registerUser() {
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        int selectedRoleId = rgUserRole.getCheckedRadioButtonId();
        if (selectedRoleId == -1) {
            Toast.makeText(this, "Please select a role", Toast.LENGTH_SHORT).show();
            return;
        }

        RadioButton selectedRadioButton = findViewById(selectedRoleId);
        String role = selectedRadioButton.getText().toString().toLowerCase();

        if (!role.equals("driver") && !role.equals("student")) {
            Toast.makeText(this, "Only driver or student registration allowed", Toast.LENGTH_SHORT).show();
            return;
        }
        if (email.isEmpty() || password.isEmpty() || password.length() < 6) {
            Toast.makeText(this, "Enter valid email and password (min 6 chars)", Toast.LENGTH_SHORT).show();
            return;
        }

        progressBar.setVisibility(View.VISIBLE);
        btnRegister.setEnabled(false);

        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    progressBar.setVisibility(View.GONE);
                    btnRegister.setEnabled(true);
                    if (task.isSuccessful()) {
                        if (mAuth.getCurrentUser() == null) {
                            Toast.makeText(this, "Registration failed: User not found", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        Map<String, Object> userData = new HashMap<>();
                        userData.put("email", email);
                        userData.put("role", role);
                        userData.put("approved", false);  // default to not approved
                        userData.put("busId", null);      // not yet assigned

                        db.collection("users").document(mAuth.getCurrentUser().getUid())
                                .set(userData)
                                .addOnSuccessListener(aVoid -> {
                                    Toast.makeText(this, "Registration successful! Await admin approval.", Toast.LENGTH_SHORT).show();
                                    startActivity(new Intent(RegisterActivity.this, LoginActivity.class));
                                    finish();
                                })
                                .addOnFailureListener(e -> Toast.makeText(this, "Failed to save user info: " + e.getMessage(), Toast.LENGTH_LONG).show());
                    } else {
                        Toast.makeText(this, "Registration failed: " +
                                (task.getException() != null ? task.getException().getMessage() : "Unknown error"), Toast.LENGTH_LONG).show();
                    }
                });
    }
}

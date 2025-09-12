package com.example.collegebustracker;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private TextView tvDriver, tvStudent, btnRegister;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // ✅ Match IDs with your XML
        tvDriver = findViewById(R.id.tvDriver);
        tvStudent = findViewById(R.id.tvStudent);
        btnRegister = findViewById(R.id.btnRegister);

        // ✅ Driver login button click
        tvDriver.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, LoginActivity.class);
            intent.putExtra("userType", "driver"); // send info to login screen
            startActivity(intent);
        });

        // ✅ Student login button click
        tvStudent.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, LoginActivity.class);
            intent.putExtra("userType", "student"); // send info to login screen
            startActivity(intent);
        });

        // ✅ Register click
        btnRegister.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, RegisterActivity.class);
            startActivity(intent);
        });
    }
}

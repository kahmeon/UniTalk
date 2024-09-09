package com.example.unitalk;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.Calendar;

public class EditProfileActivity extends AppCompatActivity {

    private TextView usernameTextView;
    private EditText phoneNumberEditText, bioEditText, birthdayEditText, genderEditText;
    private Button saveButton;
    private FirebaseAuth mAuth;
    private FirebaseFirestore mFirestore;
    private Calendar calendar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_profile);

        mAuth = FirebaseAuth.getInstance();
        mFirestore = FirebaseFirestore.getInstance();

        // Initialize views
        usernameTextView = findViewById(R.id.usernameTextView);
        phoneNumberEditText = findViewById(R.id.phoneNumberEditText);
        bioEditText = findViewById(R.id.bioEditText);
        birthdayEditText = findViewById(R.id.birthdayEditText);
        genderEditText = findViewById(R.id.genderEditText);
        saveButton = findViewById(R.id.saveButton);
        View backButton = findViewById(R.id.back_button);

        backButton.setOnClickListener(v -> onBackPressed());

        // Initialize calendar for DatePicker
        calendar = Calendar.getInstance();

        // Load current user profile
        loadUserProfile();

        // Set DatePicker on birthday EditText
        birthdayEditText.setOnClickListener(view -> showDatePicker());

        // Set save button click listener
        saveButton.setOnClickListener(view -> saveProfile());
    }

    private void loadUserProfile() {
        String userId = mAuth.getCurrentUser().getUid();
        DocumentReference docRef = mFirestore.collection("users").document(userId);
        docRef.get().addOnSuccessListener(documentSnapshot -> {
            if (documentSnapshot.exists()) {
                String username = documentSnapshot.getString("username");
                String phoneNumber = documentSnapshot.getString("phone_number");
                String bio = documentSnapshot.getString("bio");
                String birthday = documentSnapshot.getString("birthday");
                String gender = documentSnapshot.getString("gender");

                // Display username in TextView
                if (!TextUtils.isEmpty(username)) {
                    usernameTextView.setText(username);
                }
                if (!TextUtils.isEmpty(phoneNumber)) {
                    phoneNumberEditText.setText(phoneNumber);
                }
                if (!TextUtils.isEmpty(bio)) {
                    bioEditText.setText(bio);
                }
                if (!TextUtils.isEmpty(birthday)) {
                    birthdayEditText.setText(birthday);
                }
                if (!TextUtils.isEmpty(gender)) {
                    genderEditText.setText(gender);
                }
            }
        }).addOnFailureListener(e -> {
            Toast.makeText(EditProfileActivity.this, "Failed to load profile.", Toast.LENGTH_SHORT).show();
        });
    }

    private void showDatePicker() {
        int year = calendar.get(Calendar.YEAR);
        int month = calendar.get(Calendar.MONTH);
        int day = calendar.get(Calendar.DAY_OF_MONTH);

        DatePickerDialog datePickerDialog = new DatePickerDialog(EditProfileActivity.this,
                (view, selectedYear, selectedMonth, selectedDay) -> {
                    String formattedDate = selectedDay + "/" + (selectedMonth + 1) + "/" + selectedYear;
                    birthdayEditText.setText(formattedDate);
                }, year, month, day);

        datePickerDialog.show();
    }

    private void saveProfile() {
        String phoneNumber = phoneNumberEditText.getText().toString().trim();
        String bio = bioEditText.getText().toString().trim();
        String birthday = birthdayEditText.getText().toString().trim();
        String gender = genderEditText.getText().toString().trim();

        String userId = mAuth.getCurrentUser().getUid();
        DocumentReference docRef = mFirestore.collection("users").document(userId);

        // Update user profile data without changing the username
        docRef.update(
                        "phone_number", phoneNumber,
                        "bio", bio,
                        "birthday", birthday,
                        "gender", gender)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(EditProfileActivity.this, "Profile updated successfully.", Toast.LENGTH_SHORT).show();

                    // Send updated data back to ProfileActivity
                    Intent resultIntent = new Intent();
                    resultIntent.putExtra("phone_number", phoneNumber);
                    resultIntent.putExtra("bio", bio);
                    resultIntent.putExtra("birthday", birthday);
                    resultIntent.putExtra("gender", gender);
                    setResult(RESULT_OK, resultIntent);
                    finish(); // Close the activity and return to the previous screen
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(EditProfileActivity.this, "Failed to update profile.", Toast.LENGTH_SHORT).show();
                });
    }
}

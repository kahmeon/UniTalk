package com.example.unitalk;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import androidx.annotation.Nullable;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.bumptech.glide.Glide;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;

public class ProfileActivity extends AppCompatActivity {

    private static final int PICK_IMAGE_REQUEST = 1;
    private static final int EDIT_PROFILE_REQUEST_CODE = 1;
    private FirebaseAuth mAuth;
    private FirebaseFirestore mFirestore;
    private TextView usernameTextView, emailTextView, phoneNumberTextView, bioTextView, genderTextView, birthdayTextView, profileCompletionTextView, profileViewsTextView, lastSeenTextView;
    private ImageView profileImageView;
    private Button editProfileButton, changePasswordButton, logoutButton;
    private int requestCode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        mAuth = FirebaseAuth.getInstance();
        mFirestore = FirebaseFirestore.getInstance();

        // Initialize views
        usernameTextView = findViewById(R.id.username_profile_frag);
        emailTextView = findViewById(R.id.emailTextView);
        phoneNumberTextView = findViewById(R.id.phoneNumberTextView);
        bioTextView = findViewById(R.id.bioTextView);
        genderTextView = findViewById(R.id.genderTextView);
        birthdayTextView = findViewById(R.id.birthdayTextView);
        profileCompletionTextView = findViewById(R.id.profileCompletionTextView);
        profileViewsTextView = findViewById(R.id.profileViewsTextView);
        lastSeenTextView = findViewById(R.id.lastSeenTextView);
        profileImageView = findViewById(R.id.profile_frag_image);
        View backButton = findViewById(R.id.back_button);

        FloatingActionButton fab = findViewById(R.id.fab);

        editProfileButton = findViewById(R.id.editProfileButton);
        changePasswordButton = findViewById(R.id.changePasswordButton);
        logoutButton = findViewById(R.id.logoutButton);

        updateLastSeen();

        // Increment profile views (only if viewing another user's profile)
        incrementProfileViews();

        backButton.setOnClickListener(v -> onBackPressed());

        fab.setOnClickListener(view -> selectImage());

        String userId = getIntent().getStringExtra("userId");
        logoutButton.setOnClickListener(view -> handleLogout());


        if (userId != null) {
            loadUserProfile(userId); // Load profile of the user whose ID is passed
        } else {
            // Optionally, handle the case where no userId is passed
            Toast.makeText(this, "No user ID provided", Toast.LENGTH_SHORT).show();
        }

        // Handle edit profile button click
        editProfileButton.setOnClickListener(view -> {
            // Create an Intent to open EditProfileActivity
            Intent intent = new Intent(ProfileActivity.this, EditProfileActivity.class);
            startActivityForResult(intent, EDIT_PROFILE_REQUEST_CODE);
        });

        changePasswordButton.setOnClickListener(view -> {
            // Create an Intent to open ChangePasswordActivity
            Intent intent = new Intent(ProfileActivity.this, ChangePasswordActivity.class);
            startActivity(intent);
        });

        // Handle logout button click
        logoutButton.setOnClickListener(view -> {
            FirebaseAuth.getInstance().signOut();
            // Redirect to login screen
            startActivity(new Intent(ProfileActivity.this, SignInActivity.class));
            finish();
        });
    }

    private void loadUserProfile(String userId) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        FirebaseFirestore.getInstance().collection("users").document(userId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        // Populate the profile with the user's information
                        String username = documentSnapshot.getString("username");
                        String phoneNumber = documentSnapshot.getString("phone_number");
                        String bio = documentSnapshot.getString("bio");
                        String email = documentSnapshot.getString("email");
                        String gender = documentSnapshot.getString("gender");
                        String birthday = documentSnapshot.getString("birthday");
                        String lastSeen = documentSnapshot.getString("last_seen");
                        Long profileViews = documentSnapshot.getLong("profile_views");
                        String imageUrl = documentSnapshot.getString("image");

                        // Set text for the respective TextViews
                        usernameTextView.setText(username);
                        emailTextView.setText(email);
                        phoneNumberTextView.setText("Phone No: " + (phoneNumber != null ? phoneNumber : "Not specified"));
                        bioTextView.setText("Bio: " + (bio != null ? bio : "Not specified"));
                        genderTextView.setText("Gender: " + (gender != null ? gender : "Not specified"));
                        birthdayTextView.setText("Birthday: " + (birthday != null ? birthday : "Not specified"));
                        lastSeenTextView.setText("Last Seen: " + (lastSeen != null ? lastSeen : "Never"));
                        profileViewsTextView.setText("Profile Views: " + (profileViews != null ? profileViews : "0"));

                        // Calculate and set profile completion
                        int completionPercentage = calculateProfileCompletion(username, email, phoneNumber, bio, gender, birthday, imageUrl);
                        profileCompletionTextView.setText("Profile Completion Percentage: " + completionPercentage + "%");

                        // Handle profile image loading
                        if (imageUrl != null && !imageUrl.isEmpty()) {
                            // Load the profile image if available
                            Glide.with(this).load(imageUrl).into(profileImageView);
                        } else {
                            // Load a default profile image if none is set
                            Glide.with(this)
                                    .load(R.drawable.person_icon) // Replace with your default image resource
                                    .into(profileImageView);
                        }

                    }
                }).addOnFailureListener(e -> {
                    Toast.makeText(ProfileActivity.this, "Failed to load user profile", Toast.LENGTH_SHORT).show();
                });
    }

    private int calculateProfileCompletion(String... fields) {
        int filledFields = 0;
        for (String field : fields) {
            if (field != null && !field.isEmpty()) {
                filledFields++;
            }
        }
        return (filledFields * 100) / fields.length;
    }

    private void handleLogout() {
        // Sign out the current user
        FirebaseAuth.getInstance().signOut();

        // Clear any active listeners or resources if needed
        // (If you have active Firestore listeners, remove them here)

        // Navigate back to the SignInActivity
        Intent signInIntent = new Intent(ProfileActivity.this, SignInActivity.class);
        signInIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(signInIntent);
        finish(); // Close the ProfileActivity to prevent users from navigating back to it
    }

    private void selectImage() {
        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setType("image/*");
        startActivityForResult(intent, PICK_IMAGE_REQUEST);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == PICK_IMAGE_REQUEST && resultCode == RESULT_OK && data != null && data.getData() != null) {
            Uri imageUri = data.getData();
            uploadImageToFirebase(imageUri);
        }

        if (requestCode == EDIT_PROFILE_REQUEST_CODE && resultCode == RESULT_OK && data != null) {
            // Update the profile views with the new data
            String username = data.getStringExtra("username");
            String phoneNumber = data.getStringExtra("phone_number");
            String bio = data.getStringExtra("bio");
            String birthday = data.getStringExtra("birthday");
            String gender = data.getStringExtra("gender");

            // Update the TextViews
            usernameTextView.setText(username);
            phoneNumberTextView.setText(phoneNumber);
            bioTextView.setText(bio);
            birthdayTextView.setText(birthday);
            genderTextView.setText(gender);

            // Recalculate profile completion
            int profileCompletion = calculateProfileCompletion(username, phoneNumber, bio, birthday, gender);
            profileCompletionTextView.setText(profileCompletion + "%");
        }
    }

    private void uploadImageToFirebase(Uri imageUri) {
        FirebaseStorage storage = FirebaseStorage.getInstance();
        StorageReference storageRef = storage.getReference();
        StorageReference profileImageRef = storageRef.child("profile_images/" + UUID.randomUUID().toString());

        profileImageRef.putFile(imageUri)
                .addOnSuccessListener(taskSnapshot -> {
                    profileImageRef.getDownloadUrl().addOnSuccessListener(uri -> {
                        String imageUrl = uri.toString();
                        updateUserProfileImage(imageUrl);
                    });
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(ProfileActivity.this, "Image upload failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void updateUserProfileImage(String imageUrl) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            DocumentReference docRef = mFirestore.collection("users").document(user.getUid());

            // Use set with merge to ensure other fields are not overwritten
            docRef.set(Collections.singletonMap("image", imageUrl), SetOptions.merge())
                    .addOnSuccessListener(aVoid -> {
                        Toast.makeText(ProfileActivity.this, "Profile image updated.", Toast.LENGTH_SHORT).show();

                        // Load the updated profile image using Glide
                        Glide.with(this).load(imageUrl).into(profileImageView);

                        // Reload the full profile information to ensure everything is updated
                        loadUserProfile(user.getUid());
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(ProfileActivity.this, "Failed to update profile image", Toast.LENGTH_SHORT).show();
                    });
        }
    }

    private void incrementProfileViews() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            String userId = getIntent().getStringExtra("userId");

            if (userId != null) {
                DocumentReference docRef = mFirestore.collection("users").document(userId);
                docRef.update("profile_views", FieldValue.increment(1))
                        .addOnSuccessListener(aVoid -> {
                            // Successfully incremented profile views
                        })
                        .addOnFailureListener(e -> {
                            Toast.makeText(ProfileActivity.this, "Failed to increment profile views", Toast.LENGTH_SHORT).show();
                        });
            }
        }
    }

    private void updateLastSeen() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            DocumentReference docRef = mFirestore.collection("users").document(user.getUid());
            String lastSeen = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(new Date());
            docRef.update("last_seen", lastSeen)
                    .addOnFailureListener(e -> {
                        Toast.makeText(ProfileActivity.this, "Failed to update last seen", Toast.LENGTH_SHORT).show();
                    });
        }
    }
}

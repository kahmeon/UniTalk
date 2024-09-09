package com.example.unitalk;

import android.content.ContentResolver;
import android.Manifest;
import java.util.ArrayList;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.webkit.MimeTypeMap;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.util.HashMap;
import java.util.Map;

public class PostHomepage extends AppCompatActivity {

    private EditText postTitleEditText, postDescriptionEditText;
    private Button uploadPhotoButton, submitPostButton;
    private ImageView uploadedImageView;
    private Uri imageUri;
    private StorageReference storageReference;
    private FirebaseFirestore db;
    private FirebaseAuth auth;
    private ImageButton backButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_post_homepage);

        // Initialize views
        postDescriptionEditText = findViewById(R.id.postDescription);
        uploadPhotoButton = findViewById(R.id.uploadPhotoButton);
        submitPostButton = findViewById(R.id.submitPostButton);
        uploadedImageView = findViewById(R.id.uploadedImageView);
        backButton = findViewById(R.id.backButton);

        // Initialize Firebase references
        storageReference = FirebaseStorage.getInstance().getReference("post_images");
        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

        // Set up click listeners
        uploadPhotoButton.setOnClickListener(view -> openFileChooser());
        submitPostButton.setOnClickListener(view -> submitPost());
        backButton.setOnClickListener(view -> finish());
    }

    private void openFileChooser() {
        Intent intent = new Intent();
        intent.setType("image/*");
        intent.setAction(Intent.ACTION_GET_CONTENT);
        startActivityForResult(Intent.createChooser(intent, "Select Image"), 1);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == 1 && resultCode == RESULT_OK && data != null && data.getData() != null) {
            imageUri = data.getData();
            uploadedImageView.setImageURI(imageUri);
            uploadedImageView.setVisibility(View.VISIBLE);
        }
    }

    private void submitPost() {
        String postTitle = postTitleEditText.getText().toString().trim();
        String postDescription = postDescriptionEditText.getText().toString().trim();

        if (postTitle.isEmpty() || postDescription.isEmpty()) {
            Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show();
            return;
        }

        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "You need to be logged in to post", Toast.LENGTH_SHORT).show();
            return;
        }

        String userId = user.getUid();

        // Retrieve username from Firestore
        db.collection("users").document(userId).get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        User userProfile = documentSnapshot.toObject(User.class);
                        String username = userProfile != null ? userProfile.getUsername() : "Unknown";

                        Map<String, Object> post = new HashMap<>();
                        post.put("title", postTitle);
                        post.put("description", postDescription);
                        post.put("username", username);
                        post.put("likeCount", 0); // Initialize like count
                        post.put("comments", new ArrayList<>());
                        post.put("userId", userId);


                        if (imageUri != null) {
                            StorageReference fileReference = storageReference.child(System.currentTimeMillis()
                                    + "." + getFileExtension(imageUri));

                            fileReference.putFile(imageUri)
                                    .addOnSuccessListener(taskSnapshot -> fileReference.getDownloadUrl()
                                            .addOnSuccessListener(uri -> {
                                                post.put("imageUrl", uri.toString());
                                                db.collection("posts").add(post)
                                                        .addOnSuccessListener(documentReference -> {
                                                            Toast.makeText(PostHomepage.this, "Post Successful", Toast.LENGTH_SHORT).show();
                                                            // Notify homepage to refresh
                                                            Intent refreshIntent = new Intent("REFRESH_HOMEPAGE");
                                                            LocalBroadcastManager.getInstance(PostHomepage.this).sendBroadcast(refreshIntent);
                                                            setResult(RESULT_OK);
                                                            finish(); // Close activity
                                                        })
                                                        .addOnFailureListener(e -> {
                                                            Toast.makeText(PostHomepage.this, "Failed to post: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                                                        });
                                            })
                                            .addOnFailureListener(e -> {
                                                Toast.makeText(PostHomepage.this, "Failed to get download URL: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                                            }))
                                    .addOnFailureListener(e -> {
                                        Toast.makeText(PostHomepage.this, "Upload failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                                    });
                        } else {
                            post.put("imageUrl", ""); // No image URL
                            db.collection("posts").add(post)
                                    .addOnSuccessListener(documentReference -> {
                                        Toast.makeText(PostHomepage.this, "Post Successful", Toast.LENGTH_SHORT).show();
                                        // Notify homepage to refresh
                                        Intent refreshIntent = new Intent("REFRESH_HOMEPAGE");
                                        LocalBroadcastManager.getInstance(PostHomepage.this).sendBroadcast(refreshIntent);
                                        setResult(RESULT_OK);
                                        finish(); // Close activity
                                    })
                                    .addOnFailureListener(e -> {
                                        Toast.makeText(PostHomepage.this, "Failed to post: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                                    });
                        }
                    } else {
                        Toast.makeText(this, "User profile not found", Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to retrieve user profile: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private String getFileExtension(Uri uri) {
        ContentResolver cR = getContentResolver();
        MimeTypeMap mime = MimeTypeMap.getSingleton();
        return mime.getExtensionFromMimeType(cR.getType(uri));
    }
}

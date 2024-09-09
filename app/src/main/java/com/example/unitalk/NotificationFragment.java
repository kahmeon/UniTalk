package com.example.unitalk;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.unitalk.adapter.NotificationAdapter;
import com.example.unitalk.model.NotificationModel;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class NotificationFragment extends Fragment {

    private RecyclerView recyclerViewNotifications;
    private NotificationAdapter notificationAdapter;
    private List<NotificationModel> notificationList;
    private ListenerRegistration notificationListener;
    private TextView textViewUsername, noNotificationsText;
    private ImageView userProfile, menuButton;
    private FirebaseFirestore db;
    private String currentUserId;
    private FirebaseAuth mAuth;

    // Added Executor and Handler for background threading
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_notification, container, false);

        Button clearAllButton = view.findViewById(R.id.clear_all_button);
        clearAllButton.setOnClickListener(v -> clearAllNotifications());
        mAuth = FirebaseAuth.getInstance();

        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            currentUserId = currentUser.getUid();
        } else {
            currentUserId = ""; // Handle the case when the user is not authenticated
            Toast.makeText(getContext(), "Please log in to continue.", Toast.LENGTH_SHORT).show();
        }

        // Initialize Firestore and UI components
        db = FirebaseFirestore.getInstance();
        recyclerViewNotifications = view.findViewById(R.id.recycler_view_notifications);
        userProfile = view.findViewById(R.id.user_profile);
        menuButton = view.findViewById(R.id.menu_button);
        textViewUsername = view.findViewById(R.id.username);
        noNotificationsText = view.findViewById(R.id.no_notifications_text);

        setupMenuButton(menuButton);

        notificationList = new ArrayList<>();
        setUpRecyclerView();

        // Display the current user's username
        displayUsername();

        // Load notifications for the current user
        loadNotifications();

        // Profile button click handler
        userProfile.setOnClickListener(v -> {
            Toast.makeText(getContext(), "Profile clicked", Toast.LENGTH_SHORT).show();
        });

        return view;
    }

    // Set up RecyclerView with the NotificationAdapter
    private void setUpRecyclerView() {
        notificationAdapter = new NotificationAdapter(notificationList, getContext());
        recyclerViewNotifications.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerViewNotifications.setAdapter(notificationAdapter);
    }

    // Display the current user's username
    private void displayUsername() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            currentUserId = user.getUid();

            // Fetch the username from Firestore using the UID
            executor.execute(() -> { // Moving this to a background thread
                db.collection("users")
                        .document(currentUserId)
                        .get()
                        .addOnSuccessListener(documentSnapshot -> {
                            handler.post(() -> { // Updating the UI on the main thread
                                if (documentSnapshot.exists()) {
                                    String username = documentSnapshot.getString("username");
                                    if (username != null && !username.isEmpty()) {
                                        textViewUsername.setText(username);
                                    } else {
                                        textViewUsername.setText("Unknown User");
                                        Toast.makeText(getContext(), "Username not set. Update your profile.", Toast.LENGTH_SHORT).show();
                                    }
                                } else {
                                    textViewUsername.setText("Unknown User");
                                    Toast.makeText(getContext(), "User document not found.", Toast.LENGTH_SHORT).show();
                                }
                            });
                        })
                        .addOnFailureListener(e -> {
                            handler.post(() -> {
                                Log.e("FirestoreError", "Error fetching username", e);
                                textViewUsername.setText("Unknown User");
                                Toast.makeText(getContext(), "Failed to fetch username.", Toast.LENGTH_SHORT).show();
                            });
                        });
            });
        } else {
            textViewUsername.setText("No User Signed In");
            Toast.makeText(getContext(), "Please sign in to access this feature.", Toast.LENGTH_SHORT).show();
        }
    }

    // Show the popup menu for profile and logout options
    private void setupMenuButton(View view) {
        menuButton.setOnClickListener(v -> showPopupMenu());
    }

    // Show the popup menu for settings option
    private void showPopupMenu() {
        PopupMenu popupMenu = new PopupMenu(getContext(), menuButton);
        // Add settings option to the menu
        popupMenu.getMenu().add(0, 1, 0, "Settings");

        popupMenu.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) {
                openSettings();
                return true;
            }
            return false;
        });

        popupMenu.show();
    }

    private void openSettings() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user != null) {
            Intent intent = new Intent(getContext(), ProfileActivity.class);
            intent.putExtra("userId", user.getUid()); // Pass the current user's ID
            startActivity(intent);
        } else {
            Toast.makeText(getContext(), "User not authenticated", Toast.LENGTH_SHORT).show();
        }
    }

    // Load notifications from Firestore for the current user
    private void loadNotifications() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Toast.makeText(getContext(), "User not signed in", Toast.LENGTH_SHORT).show();
            return;
        }

        currentUserId = user.getUid();

        // Remove existing listener if it exists before adding a new one
        if (notificationListener != null) {
            notificationListener.remove();
        }

        // Add a Firestore listener and keep a reference to it
        executor.execute(() -> {
            notificationListener = db.collection("Notifications")
                    .whereEqualTo("receiverId", currentUserId)
                    .orderBy("timestamp", Query.Direction.DESCENDING)
                    .addSnapshotListener((value, error) -> {
                        handler.post(() -> {
                            if (error != null) {
                                Log.e("NotificationFragment", "Error getting notifications: ", error);
                                Toast.makeText(getContext(), "Failed to load notifications: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                                return;
                            }

                            if (value != null && !value.isEmpty()) {
                                notificationList.clear();
                                toggleNoNotificationsMessage(false);
                                for (DocumentChange docChange : value.getDocumentChanges()) {
                                    NotificationModel notification = docChange.getDocument().toObject(NotificationModel.class);
                                    notification.setDocumentId(docChange.getDocument().getId());
                                    if (notification != null) {
                                        // Fetch sender's username using senderId
                                        fetchSenderUsername(notification);
                                    } else {
                                        Log.e("NotificationFragment", "Error parsing notification document.");
                                    }
                                }
                            } else {
                                toggleNoNotificationsMessage(true);
                            }
                        });
                    });
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // Remove Firestore listener when the view is destroyed
        if (notificationListener != null) {
            notificationListener.remove();
            notificationListener = null;
        }
    }

    // Fetch the sender's username using the senderId
    private void fetchSenderUsername(NotificationModel notification) {
        String senderId = notification.getSenderId();

        if (senderId != null) {
            // Fetch username from Firestore
            executor.execute(() -> {
                db.collection("users")
                        .document(senderId)
                        .get()
                        .addOnSuccessListener(documentSnapshot -> handler.post(() -> {
                            if (documentSnapshot.exists()) {
                                String senderName = documentSnapshot.getString("username");
                                notification.setSenderName(senderName != null ? senderName : "Unknown Sender");
                            } else {
                                notification.setSenderName("Unknown Sender");
                            }
                            // Add the notification to the list after setting the sender name
                            notificationList.add(notification);
                            notificationAdapter.notifyDataSetChanged();
                        }))
                        .addOnFailureListener(e -> handler.post(() -> {
                            Log.e("NotificationFragment", "Failed to fetch sender username: ", e);
                            notification.setSenderName("Unknown Sender");
                            notificationList.add(notification);
                            notificationAdapter.notifyDataSetChanged();
                        }));
            });
        } else {
            // Handle cases where senderId is missing
            notification.setSenderName("Unknown Sender");
            notificationList.add(notification);
            notificationAdapter.notifyDataSetChanged();
        }
    }

    // Method to toggle the visibility of the "No Notifications" message
    private void toggleNoNotificationsMessage(boolean show) {
        if (show) {
            noNotificationsText.setVisibility(View.VISIBLE);
            recyclerViewNotifications.setVisibility(View.GONE);
        } else {
            noNotificationsText.setVisibility(View.GONE);
            recyclerViewNotifications.setVisibility(View.VISIBLE);
        }
    }

    private void clearAllNotifications() {
        executor.execute(() -> {
            db.collection("Notifications")
                    .whereEqualTo("receiverId", currentUserId)
                    .get()
                    .addOnSuccessListener(querySnapshot -> handler.post(() -> {
                        for (DocumentSnapshot document : querySnapshot.getDocuments()) {
                            document.getReference().delete();
                        }
                        notificationList.clear();
                        notificationAdapter.notifyDataSetChanged();
                        toggleNoNotificationsMessage(true);
                        Toast.makeText(getContext(), "All notifications cleared", Toast.LENGTH_SHORT).show();
                    }))
                    .addOnFailureListener(e -> handler.post(() -> {
                        Toast.makeText(getContext(), "Failed to clear notifications: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }));
        });
    }
}

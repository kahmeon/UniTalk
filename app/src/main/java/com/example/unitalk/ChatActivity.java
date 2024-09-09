package com.example.unitalk;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.unitalk.adapter.ChatMessageAdapter;
import com.example.unitalk.model.ChatMessage;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ChatActivity extends AppCompatActivity {

    private EditText chatMessageInput;
    private ImageButton messageSendBtn, backButton;
    private RecyclerView chatRecyclerView;
    private TextView textViewUsername;
    private ChatMessageAdapter messageAdapter;
    private List<ChatMessage> chatMessages;
    private FirebaseAuth auth;
    private String senderId, receiverId, chatRoomId;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        // Initialize Firestore
        db = FirebaseFirestore.getInstance();

        // Find views by their IDs
        chatMessageInput = findViewById(R.id.chat_message_input);
        messageSendBtn = findViewById(R.id.message_send_btn);
        backButton = findViewById(R.id.back_button);
        chatRecyclerView = findViewById(R.id.chat_recycler_view);
        textViewUsername = findViewById(R.id.other_username);

        // Initialize Firebase Auth
        auth = FirebaseAuth.getInstance();
        FirebaseUser currentUser = auth.getCurrentUser();

        // Check if the user is authenticated
        if (currentUser == null) {
            Toast.makeText(this, "User not authenticated", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Get the sender's ID
        senderId = currentUser.getUid();
        Log.d("ChatActivity", "Current User ID: " + senderId);

        // Retrieve receiverId and username from Intent
        receiverId = getIntent().getStringExtra("receiverId");
        String receiverUsername = getIntent().getStringExtra("receiverUsername");

        Log.d("ChatActivity", "Received receiver username: " + receiverUsername);
        Log.d("ChatActivity", "Received receiver ID: " + receiverId);

        if (receiverUsername == null || receiverUsername.isEmpty()) {
            Log.e("ChatActivity", "Receiver username is null or empty.");
            Toast.makeText(this, "Receiver username not found.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Display the username on the toolbar
        textViewUsername.setText(receiverUsername);
        Log.d("ChatActivity", "Received Receiver Username: " + receiverUsername);
        Log.d("ChatActivity", "Received Receiver ID: " + receiverId);

        // Initialize the list and adapter for chat messages
        chatMessages = new ArrayList<>();
        messageAdapter = new ChatMessageAdapter(chatMessages, this);
        chatRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        chatRecyclerView.setAdapter(messageAdapter);

        // Retrieve the receiver's ID using the username
        retrieveReceiverId(receiverUsername);

        // Set the send button click listener
        messageSendBtn.setOnClickListener(v -> {
            Log.d("ChatActivity", "Send button clicked");
            sendMessage();
        });

        // Set the back button click listener
        backButton.setOnClickListener(v -> onBackPressed());
    }

    // Fetch the receiver's username using the receiverId
    private void fetchReceiverUsername(String receiverId) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection("users").document(receiverId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        String receiverUsername = documentSnapshot.getString("username"); // Ensure 'username' is the correct field name
                        if (receiverUsername != null) {
                            textViewUsername.setText(receiverUsername);
                            Log.d("ChatActivity", "Receiver Username: " + receiverUsername);
                        } else {
                            Toast.makeText(ChatActivity.this, "Username not found", Toast.LENGTH_SHORT).show();
                            Log.e("ChatActivity", "Receiver username is null.");
                        }
                    } else {
                        Toast.makeText(ChatActivity.this, "Receiver data not found.", Toast.LENGTH_SHORT).show();
                        Log.e("ChatActivity", "Receiver document does not exist.");
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(ChatActivity.this, "Failed to retrieve receiver username.", Toast.LENGTH_SHORT).show();
                    Log.e("ChatActivity", "Error fetching receiver username", e);
                });
    }

    // Retrieve the receiver's ID based on the provided username
    private void retrieveReceiverId(String username) {
        db.collection("users")
                .whereEqualTo("username", username)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult() != null && !task.getResult().isEmpty()) {
                        DocumentSnapshot document = task.getResult().getDocuments().get(0);
                        receiverId = document.getId();
                        textViewUsername.setText(username);
                        Log.d("ChatActivity", "Receiver ID found: " + receiverId);

                        // Initialize the chat room with both sender and receiver IDs
                        initializeChatRoom(senderId, receiverId);
                    } else {
                        Log.e("ChatActivity", "Receiver not found");
                        Toast.makeText(ChatActivity.this, "Receiver not found", Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e("ChatActivity", "Failed to retrieve receiver ID", e);
                    Toast.makeText(ChatActivity.this, "Failed to retrieve receiver ID", Toast.LENGTH_SHORT).show();
                    finish();
                });
    }

    // Initialize the chat room using the sender's and receiver's IDs
    private void initializeChatRoom(String senderId, String receiverId) {
        chatRoomId = createChatRoomId(senderId, receiverId);

        // Check if the chat room already exists in Firestore
        db.collection("chatRooms").document(chatRoomId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (!documentSnapshot.exists()) {
                        // Chat room does not exist, create it
                        Map<String, Object> chatRoom = new HashMap<>();
                        chatRoom.put("userIds", Arrays.asList(senderId, receiverId));
                        chatRoom.put("lastMessageTimeStamp", System.currentTimeMillis());

                        db.collection("chatRooms").document(chatRoomId)
                                .set(chatRoom)
                                .addOnSuccessListener(aVoid -> {
                                    Log.d("ChatActivity", "Chat room created successfully.");
                                    loadMessagesFromFirestore(); // Load messages from Firestore
                                })
                                .addOnFailureListener(e -> {
                                    Log.e("ChatActivity", "Failed to create chat room", e);
                                    Toast.makeText(ChatActivity.this, "Failed to create chat room", Toast.LENGTH_SHORT).show();
                                });
                    } else {
                        // Chat room exists, proceed to load messages
                        loadMessagesFromFirestore();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e("ChatActivity", "Failed to check chat room existence", e);
                    Toast.makeText(ChatActivity.this, "Failed to check chat room", Toast.LENGTH_SHORT).show();
                });
    }

    // Create a unique chat room ID by comparing sender and receiver IDs
    private String createChatRoomId(String senderId, String receiverId) {
        return senderId.compareTo(receiverId) < 0 ? senderId + "_" + receiverId : receiverId + "_" + senderId;
    }

    // Method to send a message
    private void sendMessage() {
        String messageText = chatMessageInput.getText().toString().trim();
        if (messageText.isEmpty()) {
            Toast.makeText(this, "Cannot send empty message", Toast.LENGTH_SHORT).show();
            return;
        }

        if (receiverId == null || receiverId.equals(senderId)) {
            Toast.makeText(this, "Receiver ID is incorrect or not available", Toast.LENGTH_SHORT).show();
            Log.e("ChatActivity", "Invalid receiver ID: " + receiverId);
            return;
        }

        // Create a message object with necessary fields
        Map<String, Object> message = new HashMap<>();
        message.put("senderId", senderId);
        message.put("receiverId", receiverId); // Make sure this is the correct receiver
        message.put("messageText", messageText);
        message.put("timestamp", System.currentTimeMillis());

        // Add the message to Firestore under the specified chat room
        db.collection("Chats").document(chatRoomId).collection("Messages")
                .add(message)
                .addOnSuccessListener(documentReference -> {
                    chatMessageInput.setText(""); // Clear the input field
                    updateChatRoom(chatRoomId, messageText);
                    Log.d("ChatActivity", "Message sent successfully.");

                    addNotification(receiverId, messageText);
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to send message: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    Log.e("ChatActivity", "Failed to send message", e);
                });
    }

    private void updateChatRoom(String chatRoomId, String lastMessage) {
        Map<String, Object> chatRoomUpdates = new HashMap<>();
        chatRoomUpdates.put("lastMessage", lastMessage);
        chatRoomUpdates.put("lastMessageTimeStamp", System.currentTimeMillis());

        db.collection("chatRooms").document(chatRoomId)
                .update(chatRoomUpdates)
                .addOnSuccessListener(aVoid -> Log.d("ChatActivity", "Chat room updated successfully"))
                .addOnFailureListener(e -> Log.e("ChatActivity", "Failed to update chat room", e));
    }

    // Load messages from Firestore in real-time
    private void loadMessagesFromFirestore() {
        db.collection("Chats").document(chatRoomId).collection("Messages")
                .orderBy("timestamp")
                .addSnapshotListener((snapshots, e) -> {
                    if (e != null) {

                        return;
                    }

                    if (snapshots != null && !snapshots.isEmpty()) {
                        chatMessages.clear(); // Clear existing messages to avoid duplicates
                        for (DocumentSnapshot snapshot : snapshots.getDocuments()) {
                            ChatMessage chatMessage = snapshot.toObject(ChatMessage.class);
                            if (chatMessage != null) {
                                // Check if the message involves the current sender and receiver
                                if ((chatMessage.getSenderId().equals(senderId) && chatMessage.getReceiverId().equals(receiverId)) ||
                                        (chatMessage.getSenderId().equals(receiverId) && chatMessage.getReceiverId().equals(senderId))) {
                                    chatMessages.add(chatMessage);
                                    Log.d("ChatActivity", "Displaying message: " + chatMessage.getMessageText());
                                } else {
                                    Log.d("ChatActivity", "Message filtered out. Sender: " + chatMessage.getSenderId() + ", Receiver: " + chatMessage.getReceiverId());
                                }
                            }
                        }
                        messageAdapter.notifyDataSetChanged();
                        chatRecyclerView.scrollToPosition(chatMessages.size() - 1);
                    } else {
                        Log.d("ChatActivity", "No messages found.");
                    }
                });
    }

    private void addNotification(String receiverId, String messageText) {
        Map<String, Object> notification = new HashMap<>();
        notification.put("receiverId", receiverId);
        notification.put("senderId", senderId);
        notification.put("messageText", messageText);
        notification.put("timestamp", System.currentTimeMillis());

        // Add the notification entry to Firestore
        db.collection("Notifications").add(notification)
                .addOnSuccessListener(documentReference -> {
                    Log.d("ChatActivity", "Notification added successfully.");
                })
                .addOnFailureListener(e -> {
                    Log.e("ChatActivity", "Failed to add notification", e);
                });
    }
}

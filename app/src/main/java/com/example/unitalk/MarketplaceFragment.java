package com.example.unitalk;

import android.content.Intent;
import android.os.Bundle;
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
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class MarketplaceFragment extends Fragment {

    private RecyclerView recyclerView;
    private MarketplaceAdapter adapter;
    private List<Product> productList;
    private SwipeRefreshLayout swipeRefreshLayout;
    private TextView usernameTextView;
    private FirebaseAuth auth;
    private ImageView menuButton;
    private TextView postTextView; // Change this line

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_marketplace, container, false);
        postTextView = view.findViewById(R.id.postTextView); // Update this line
        postTextView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openPostMarketplaceActivity(v); // Ensure the TextView triggers the method
            }
        });

        // Initialize Firebase Auth
        auth = FirebaseAuth.getInstance();

        // Initialize SwipeRefreshLayout
        swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout);
        swipeRefreshLayout.setOnRefreshListener(this::loadProductsFromFirestore);

        // Initialize RecyclerView
        recyclerView = view.findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new GridLayoutManager(getContext(), 2)); // 2 columns
        productList = new ArrayList<>();
        adapter = new MarketplaceAdapter(getContext(), productList);
        recyclerView.setAdapter(adapter);

        // Initialize username TextView
        usernameTextView = view.findViewById(R.id.username);

        // Initialize menu button
        menuButton = view.findViewById(R.id.menuButton);
        setupMenuButton(menuButton);

        // Load current username
        loadUsername();

        // Load products from Firestore
        loadProductsFromFirestore();

        return view;
    }

    private void setupMenuButton(View view) {
        menuButton.setOnClickListener(v -> showPopupMenu());
    }

    private void showPopupMenu() {
        PopupMenu popupMenu = new PopupMenu(getContext(), menuButton);
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
        Toast.makeText(getContext(), "Opening Settings...", Toast.LENGTH_SHORT).show();
        Intent intent = new Intent(getContext(), ProfileActivity.class);
        startActivity(intent);
    }

    private void loadUsername() {
        FirebaseUser firebaseUser = auth.getCurrentUser();
        if (firebaseUser != null) {
            FirebaseFirestore db = FirebaseFirestore.getInstance();
            db.collection("users").document(firebaseUser.getUid()).get()
                    .addOnSuccessListener(documentSnapshot -> {
                        if (documentSnapshot.exists()) {
                            String username = documentSnapshot.getString("username");
                            if (username != null && !username.isEmpty()) {
                                usernameTextView.setText(username);
                            } else {
                                usernameTextView.setText(firebaseUser.getEmail());
                            }
                        } else {
                            usernameTextView.setText(firebaseUser.getEmail());
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.e("MarketplaceFragment", "Error getting user document", e);
                        usernameTextView.setText(firebaseUser.getEmail());
                    });
        } else {
            usernameTextView.setText("Guest");
        }
    }

    private void loadProductsFromFirestore() {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection("products")
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        productList.clear();
                        for (QueryDocumentSnapshot document : task.getResult()) {
                            Map<String, Object> data = document.getData();
                            String name = (String) data.get("name");
                            Object priceObj = data.get("price");
                            double price = 0;
                            if (priceObj instanceof Double) {
                                price = (Double) priceObj;
                            } else if (priceObj instanceof String) {
                                try {
                                    price = Double.parseDouble((String) priceObj);
                                } catch (NumberFormatException e) {
                                    Log.e("MarketplaceFragment", "Error parsing price", e);
                                }
                            }
                            String description = (String) data.get("description");
                            String imageUrl = (String) data.get("imageUrl");
                            String username = (String) data.get("username");

                            double sellerLat = data.containsKey("sellerLat") ? (double) data.get("sellerLat") : 0.0;
                            double sellerLng = data.containsKey("sellerLng") ? (double) data.get("sellerLng") : 0.0;
                            Product product = new Product(name, price, description, imageUrl, username, sellerLat, sellerLng);
                            productList.add(product);
                        }
                        adapter.notifyDataSetChanged();
                    } else {
                        Log.e("MarketplaceFragment", "Error getting documents: ", task.getException());
                    }

                    // Stop the refresh animation
                    swipeRefreshLayout.setRefreshing(false);
                });
    }

    public void openPostMarketplaceActivity(View view) {
        // Use the appropriate context from the fragment
        Intent intent = new Intent(requireContext(), PostMarketplaceActivity.class);
        startActivity(intent);
    }

}



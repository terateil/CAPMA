package com.example.capma;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.util.Log;

import androidx.core.app.ActivityCompat;

import com.google.android.libraries.places.api.Places;
import com.google.android.libraries.places.api.model.Place;
import com.google.android.libraries.places.api.model.PlaceLikelihood;
import com.google.android.libraries.places.api.net.FindCurrentPlaceRequest;
import com.google.android.libraries.places.api.net.PlacesClient;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Manager class for handling Google Places API functionality
 */
public class PlacesManager {
    private static final String TAG = "PlacesManager";
    private final PlacesClient placesClient;
    private final Context context;
    
    /**
     * Interface for receiving place data results
     */
    public interface PlacesCallback {
        void onPlacesDetected(List<PlaceLikelihood> placeLikelihoods);
        void onPlacesError(Exception e);
    }
    
    /**
     * Constructor
     * @param context Application context
     * @param apiKey Google Places API key
     */
    public PlacesManager(Context context, String apiKey) {
        this.context = context;
        
        // Initialize Places API
        if (!Places.isInitialized()) {
            Places.initialize(context, apiKey);
        }
        placesClient = Places.createClient(context);
    }
    
    /**
     * Get current place information
     * @param callback Callback to receive results
     */
    public void getCurrentPlace(PlacesCallback callback) {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            callback.onPlacesError(new SecurityException("Location permission not granted"));
            return;
        }
        
        // Define the place fields to request
        List<Place.Field> placeFields = Arrays.asList(
                Place.Field.NAME,
                Place.Field.ADDRESS,
                Place.Field.TYPES,
                Place.Field.BUSINESS_STATUS
        );
        
        // Create the request
        FindCurrentPlaceRequest request = FindCurrentPlaceRequest.newInstance(placeFields);
        
        // Execute the request
        placesClient.findCurrentPlace(request)
                .addOnSuccessListener(response -> {
                    List<PlaceLikelihood> likelihoods = response.getPlaceLikelihoods();
                    Log.d(TAG, "Places detected: " + likelihoods.size());
                    callback.onPlacesDetected(likelihoods);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error finding current place", e);
                    callback.onPlacesError(e);
                    // Return empty list on error
                    callback.onPlacesDetected(Collections.emptyList());
                });
    }
    
    /**
     * Format place data as readable text
     * @param placeLikelihoods List of place likelihoods
     * @return Formatted string
     */
    public static String formatPlaceData(List<PlaceLikelihood> placeLikelihoods) {
        StringBuilder builder = new StringBuilder();
        builder.append("PLACE DATA:\n");
        
        if (placeLikelihoods != null && !placeLikelihoods.isEmpty()) {
            for (PlaceLikelihood placeLikelihood : placeLikelihoods) {
                Place place = placeLikelihood.getPlace();
                double likelihood = placeLikelihood.getLikelihood();
                
                builder.append(place.getName())
                        .append(" (").append(String.format("%.1f%%", likelihood * 100))
                        .append(")\n");
                
                if (place.getAddress() != null) {
                    builder.append("Address: ").append(place.getAddress()).append("\n");
                }
                
                if (place.getTypes() != null) {
                    builder.append("Types: ").append(place.getTypes()).append("\n");
                }
                
                builder.append("\n");
                
                // Just show the top result for brevity
                break;
            }
        } else {
            builder.append("No place data available\n");
        }
        
        return builder.toString();
    }
} 
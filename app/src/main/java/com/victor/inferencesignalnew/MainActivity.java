package com.victor.inferencesignalnew;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private TextView inferredLocationView, signalStrength, location1View, location2View, location3View, location4View;
    private String[] locationLabels = {"Location 1", "Location 2", "Location 3", "Location 4"};
    private int[] locationSignalStrengths = {Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
    private final double[] probabilities = {0.25, 0.25, 0.25, 0.25}; // Start with uniform distribution
    private final Handler handler = new Handler();
    private final int INTERVAL = 1000; // 1-second interval
    private boolean resetMode = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize reset button
        Button buttonReset = findViewById(R.id.buttonReset);
        buttonReset.setOnClickListener(view -> resetProbabilities());

        Button locReset = findViewById(R.id.locReset);
        locReset.setOnClickListener(view -> resetLocations());

        // Initialize signal strength & inferred location displays
        inferredLocationView = findViewById(R.id.inferredLocation);
        signalStrength = findViewById(R.id.signalStrength);

        // Initialize location views
        location1View = findViewById(R.id.location1View);
        location2View = findViewById(R.id.location2View);
        location3View = findViewById(R.id.location3View);
        location4View = findViewById(R.id.location4View);

        // Set up buttons
        Button buttonLocation1 = findViewById(R.id.buttonLocation1);
        Button buttonLocation2 = findViewById(R.id.buttonLocation2);
        Button buttonLocation3 = findViewById(R.id.buttonLocation3);
        Button buttonLocation4 = findViewById(R.id.buttonLocation4);

        buttonLocation1.setOnClickListener(view -> saveLocation(0));
        buttonLocation2.setOnClickListener(view -> saveLocation(1));
        buttonLocation3.setOnClickListener(view -> saveLocation(2));
        buttonLocation4.setOnClickListener(view -> saveLocation(3));

        // Start real-time inference
        startRealTimeInference();
    }

    private void saveLocation(int locationIndex) {
        WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        int signalStrength = wifiManager.getConnectionInfo().getRssi();

        // Save the signal strength for the selected location
        locationSignalStrengths[locationIndex] = signalStrength;

        // Recalculate uniform probabilities
        int savedLocations = 0;
        for (int signal : locationSignalStrengths) {
            if (signal != Integer.MIN_VALUE) {
                savedLocations++;
            }
        }

        double uniformProbability = savedLocations > 0 ? 1.0 / savedLocations : 0;

        for (int i = 0; i < probabilities.length; i++) {
            probabilities[i] = (locationSignalStrengths[i] != Integer.MIN_VALUE) ? uniformProbability : 0.0;
        }

        // Update UI for all saved locations
        for (int i = 0; i < locationSignalStrengths.length; i++) {
            if (locationSignalStrengths[i] != Integer.MIN_VALUE) {
                updateLocationUI(i, probabilities[i]);
            }
        }
    }

    private void updateLocationUI(int locationIndex, double probability) {
        String label = locationLabels[locationIndex];
        int signalStrength = locationSignalStrengths[locationIndex];
        String displayText = label + ": " + signalStrength + "dBm, Probability: " + String.format("%.2f", probability);

        switch (locationIndex) {
            case 0:
                location1View.setText(displayText);
                break;
            case 1:
                location2View.setText(displayText);
                break;
            case 2:
                location3View.setText(displayText);
                break;
            case 3:
                location4View.setText(displayText);
                break;
        }
    }

    private void startRealTimeInference() {
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!resetMode) { // Skip updates if reset mode is active
                    updateSignalStrengthDisplay();
                    inferCurrentLocation();
                }
                handler.postDelayed(this, INTERVAL);
            }
        }, INTERVAL);
    }


    private void updateSignalStrengthDisplay() {
        WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        int currentSignalStrength = wifiManager.getConnectionInfo().getRssi();
        signalStrength.setText("Current Signal Strength: " + currentSignalStrength + "dBm");
    }

    private void inferCurrentLocation() {
        WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        int currentSignalStrength = wifiManager.getConnectionInfo().getRssi();

        // Parameters for the normal distribution
        double variance = 10.0;
        double standardDeviation = Math.sqrt(variance);
        double normalizationFactor = 1.0 / (Math.sqrt(2 * Math.PI * variance));

        // Update probabilities using Bayes' theorem
        double[] likelihoods = new double[locationSignalStrengths.length];
        double totalProbability = 0.0;

        for (int i = 0; i < locationSignalStrengths.length; i++) {
            if (locationSignalStrengths[i] == Integer.MIN_VALUE) {
                // Ignore locations that are not set
                likelihoods[i] = 0.0;
                continue;
            }

            // Compute likelihood using the normal distribution formula
            double signalDifference = currentSignalStrength - locationSignalStrengths[i];
            likelihoods[i] = normalizationFactor * Math.exp(-Math.pow(signalDifference, 2) / (2 * variance));

            // Update probability (multiply prior by likelihood)
            probabilities[i] *= likelihoods[i];
            totalProbability += probabilities[i];
        }

        // Normalize probabilities to sum to 1
        if (totalProbability > 0) {
            for (int i = 0; i < probabilities.length; i++) {
                probabilities[i] /= totalProbability;

                if (locationSignalStrengths[i] != Integer.MIN_VALUE) {
                    updateLocationUI(i, probabilities[i]);
                }
            }
        }

        // Find the most likely location
        double maxProbability = Double.NEGATIVE_INFINITY;
        String inferredLocation = "Unknown";

        for (int i = 0; i < probabilities.length; i++) {
            if (probabilities[i] > maxProbability) {
                maxProbability = probabilities[i];
                inferredLocation = locationLabels[i];
            }
        }

        if (totalProbability != 0){
            inferredLocationView.setText(String.format("Inferred Location: %s (Confidence: %.2f)", inferredLocation, maxProbability));
        }
    }

    private void resetProbabilities() {
        int savedLocations = 0;

        for (int signal : locationSignalStrengths) {
            if (signal != Integer.MIN_VALUE) {
                savedLocations++;
            }
        }

        double uniformProbability = savedLocations > 0 ? 1.0 / savedLocations : 0;

        for (int i = 0; i < probabilities.length; i++) {
            probabilities[i] = (locationSignalStrengths[i] != Integer.MIN_VALUE) ? uniformProbability : 0.0;
            if (locationSignalStrengths[i] != Integer.MIN_VALUE) {
                updateLocationUI(i, probabilities[i]);
            }
        }
    }

    private void resetLocations() {
        // Set reset mode to true
        resetMode = true;

        // Reset signal strengths to Integer.MIN_VALUE
        for (int i = 0; i < locationSignalStrengths.length; i++) {
            locationSignalStrengths[i] = Integer.MIN_VALUE;
        }

        // Reset probabilities to uniform distribution
        for (int i = 0; i < probabilities.length; i++) {
            probabilities[i] = 0.0; // Uniform probability
        }

        // Update the UI to reflect N/A for signal strengths
        for (int i = 0; i < locationLabels.length; i++) {
            String displayText = locationLabels[i] + ": N/A";
            switch (i) {
                case 0:
                    location1View.setText(displayText);
                    break;
                case 1:
                    location2View.setText(displayText);
                    break;
                case 2:
                    location3View.setText(displayText);
                    break;
                case 3:
                    location4View.setText(displayText);
                    break;
            }
        }

        // Reset inferred location text
        inferredLocationView.setText("Inferred Location: N/A");

        // Notify the user about the reset
        signalStrength.setText("Current Signal Strength: N/A");

        // Delay to exit reset mode and resume updates
        handler.postDelayed(() -> resetMode = false, 2000); // 2 seconds delay
    }


}

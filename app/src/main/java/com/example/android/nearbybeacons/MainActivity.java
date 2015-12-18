package com.example.android.nearbybeacons;

import android.Manifest;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.nearby.Nearby;
import com.google.android.gms.nearby.messages.Message;
import com.google.android.gms.nearby.messages.MessageListener;
import com.google.android.gms.nearby.messages.Strategy;
import com.google.android.gms.nearby.messages.SubscribeOptions;

public class MainActivity extends AppCompatActivity implements
        AdapterView.OnItemClickListener,
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener {
    private static final String TAG =
            MainActivity.class.getSimpleName();


    private static final int REQUEST_RESOLVE_ERROR = 100;
    private static final int REQUEST_PERMISSION = 42;

    private GoogleApiClient mGoogleApiClient;
    private ArrayAdapter<OfferBeacon> mAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ListView list = (ListView) findViewById(R.id.list);

        mAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1);
        list.setAdapter(mAdapter);
        list.setOnItemClickListener(this);

        //Construct a connection to Play Services
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(Nearby.MESSAGES_API)
                .build();

        //When launching from a notification link
        if (BeaconService.ACTION_DISMISS.equals(getIntent().getAction())) {
            //Fire a clear action to the service
            Intent intent = new Intent(this, BeaconService.class);
            intent.setAction(BeaconService.ACTION_DISMISS);
            startService(intent);
        }

    }

    @Override
    protected void onStart() {
        super.onStart();
        //Initiate connection to Play Services
        mGoogleApiClient.connect();

        //The location permission is required on API 23+ to obtain BLE scan results
        int result = ActivityCompat
                .checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION);
        if (result != PackageManager.PERMISSION_GRANTED) {
            //Ask for the location permission
            ActivityCompat.requestPermissions(this,
                    new String[] {Manifest.permission.ACCESS_COARSE_LOCATION},
                    REQUEST_PERMISSION);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        //Tear down Play Services connection
        if (mGoogleApiClient.isConnected()) {
            Log.d(TAG, "Un-subscribing…");
            Nearby.Messages.unsubscribe(
                    mGoogleApiClient,
                    mMessageListener);
            mAdapter.clear();

            mGoogleApiClient.disconnect();
        }
    }

    // This is called in response to a button tap in the system permissions dialog.
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_RESOLVE_ERROR) {
            if (resultCode == RESULT_OK) {
                // Permission granted or error resolved successfully then we proceed
                // with publish and subscribe..
                subscribe();
            } else {
                // This may mean that user had rejected to grant nearby permission.
                showToast("Failed to resolve error with code " + resultCode);
            }
        }

        if (requestCode == REQUEST_PERMISSION) {
            if (resultCode != RESULT_OK) {
                showToast("We need location permission to get scan results!");
                finish();
            }
        }
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        OfferBeacon item = mAdapter.getItem(position);
        showToast(item.offer);
    }

    /* Nearby Messages Callbacks */

    //NOTE: These callbacks are NOT triggered on the main thread!
    private MessageListener mMessageListener = new MessageListener() {
        // Called each time a new message is discovered nearby.
        @Override
        public void onFound(Message message) {
            Log.i(TAG, "Found message: " + message);
            final OfferBeacon beacon = new OfferBeacon(message);
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mAdapter.add(beacon);
                }
            });
        }

        // Called when the publisher (beacon) is no longer nearby.
        @Override
        public void onLost(Message message) {
            Log.i(TAG, "Lost message: " + message);
            final OfferBeacon beacon = new OfferBeacon(message);
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mAdapter.remove(beacon);
                }
            });
        }
    };

    private ResultCallback<Status> mRegisterCallback = new ResultCallback<Status>() {
        @Override
        public void onResult(@NonNull Status status) {
            //Validate if we were able to register for background scans
            if (status.isSuccess()) {
                Log.d(TAG, "Background Register Success!");
            } else {
                Log.w(TAG, "Background Register Error ("
                        + status.getStatusCode() + "): "
                        + status.getStatusMessage());
            }
        }
    };

    /* API Client Callbacks */

    @Override
    public void onConnected(Bundle bundle) {
        //Once connected, we have to check that the user has opted in
        Runnable runOnSuccess = new Runnable() {
            @Override
            public void run() {
                //Subscribe once user permission is verified
                subscribe();
            }
        };
        ResultCallback<Status> callback =
                new ErrorCheckingCallback(runOnSuccess);
        Nearby.Messages.getPermissionStatus(mGoogleApiClient)
                .setResultCallback(callback);
    }

    @Override
    public void onConnectionSuspended(int i) {
        Log.d(TAG, "OnConnectionSuspended");
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        Log.w(TAG, "OnConnectionFailed");
    }

    private void subscribe() {
        Log.d(TAG, "Subscribing…");
        SubscribeOptions options = new SubscribeOptions.Builder()
                .setStrategy(Strategy.BLE_ONLY)
                .build();
        //Active subscription for foreground messages
        Nearby.Messages.subscribe(mGoogleApiClient,
                mMessageListener, options);

        //Passive subscription for background messages
        Intent serviceIntent = new Intent(this, BeaconService.class);
        PendingIntent trigger = PendingIntent.getService(this, 0,
                serviceIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        Nearby.Messages.subscribe(mGoogleApiClient, trigger, options)
                .setResultCallback(mRegisterCallback);

    }

    //ResultCallback triggered when to handle Nearby permissions check
    private class ErrorCheckingCallback implements ResultCallback<Status> {
        private final Runnable runOnSuccess;

        private ErrorCheckingCallback(@Nullable Runnable runOnSuccess) {
            this.runOnSuccess = runOnSuccess;
        }

        @Override
        public void onResult(@NonNull Status status) {
            if (status.isSuccess()) {
                Log.i(TAG, "Permission status succeeded.");
                if (runOnSuccess != null) {
                    runOnSuccess.run();
                }
            } else {
                // Currently, the only resolvable error is that the device is not opted
                // in to Nearby. Starting the resolution displays an opt-in dialog.
                if (status.hasResolution()) {
                    try {
                        status.startResolutionForResult(MainActivity.this,
                                REQUEST_RESOLVE_ERROR);
                    } catch (IntentSender.SendIntentException e) {
                        showToastAndLog(Log.ERROR, "Request failed with exception: " + e);
                    }
                } else {
                    showToastAndLog(Log.ERROR, "Request failed with : " + status);
                }
            }
        }
    }


    private void showToast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }

    private void showToastAndLog(int logLevel, String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        Log.println(logLevel, TAG, message);
    }
}

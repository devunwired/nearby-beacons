package com.example.android.nearbybeacons;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import com.google.android.gms.nearby.Nearby;
import com.google.android.gms.nearby.messages.Message;
import com.google.android.gms.nearby.messages.MessageListener;

import java.util.HashSet;

/**
 * Service triggered in response to background beacon subscriptions.
 * Displays a notification to the user to indicate new beacons were
 * discovered. Tracks the current set of discovered beacons since last
 * user interaction to show as a count.
 */
public class BeaconService extends Service {

    private static final String TAG = BeaconService.class.getSimpleName();

    // Action to track notification dismissal
    public static final String ACTION_DISMISS =
            "BeaconService.ACTION_DISMISS";

    private static final int NOTIFICATION_ID = 42;

    private NotificationManager mNotificationManager;
    private HashSet<OfferBeacon> mDetectedBeacons;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Background Scanning Service Created…");
        mNotificationManager =
                (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        mDetectedBeacons = new HashSet<>();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_NOT_STICKY;

        if (!ACTION_DISMISS.equals(intent.getAction())) {
            //Convert the incoming intent into a message
            Nearby.Messages.handleIntent(intent, mMessageListener);
        } else {
            mNotificationManager.cancel(NOTIFICATION_ID);
            stopSelf();
        }

        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "Background Scanning Service Destroyed…");
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    /* Handle user notifications */
    private void postScanResultNotification(int count) {

        Intent contentAction = new Intent(this, MainActivity.class);
        contentAction.setAction(ACTION_DISMISS);
        PendingIntent content = PendingIntent.getActivity(this, -1, contentAction, 0);

        Intent deleteAction = new Intent(this, BeaconService.class);
        deleteAction.setAction(ACTION_DISMISS);
        PendingIntent delete = PendingIntent.getService(this, -1, deleteAction, 0);

        Notification note = new Notification.Builder(this)
                .setContentTitle("Beacons Detected")
                .setContentText(String.format("%d New Beacons In Range", count))
                .setSmallIcon(R.drawable.ic_stat_scan)
                .setContentIntent(content)
                .setDeleteIntent(delete)
                .build();

        mNotificationManager.notify(NOTIFICATION_ID, note);
    }

    private MessageListener mMessageListener = new MessageListener() {
        @Override
        public void onFound(Message message) {
            Log.i(TAG, "Found Background Beacon: "
                    + new String(message.getContent()));
            boolean added = mDetectedBeacons.add(new OfferBeacon(message));
            if (added) {
                postScanResultNotification(mDetectedBeacons.size());
            }
        }

        @Override
        public void onLost(Message message) {
            Log.w(TAG, "Lost Background Beacon: "
                    + new String(message.getContent()));
            mDetectedBeacons.remove(new OfferBeacon(message));
        }
    };
}

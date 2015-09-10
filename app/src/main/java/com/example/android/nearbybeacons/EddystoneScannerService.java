package com.example.android.nearbybeacons;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.ParcelUuid;
import android.util.ArrayMap;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * Background service to scan for Eddystone beacons and notify the user of
 * proximity. This service exists because Nearby Messages should not (and
 * can not) be run from the background. We will fire up that API once the
 * user decides to engage with the application.
 */
public class EddystoneScannerService extends Service {
    private static final String TAG =
            EddystoneScannerService.class.getSimpleName();

    private static boolean sRunning;
    public static boolean isRunning() {
        return sRunning;
    }

    // Action to track notification dismissal
    public static final String ACTION_DISMISS =
            "EddystoneScannerService.ACTION_DISMISS";

    // …if you feel like making the log a bit noisier…
    private static boolean DEBUG_SCAN = false;

    // Eddystone service uuid (0xfeaa)
    private static final ParcelUuid UID_SERVICE =
            ParcelUuid.fromString("0000feaa-0000-1000-8000-00805f9b34fb");

    /**
     * ENTER ALL EDDYSTONE NAMESPACES YOU WANT TO SCAN HERE
     * e.g. "d89bed6e130ee5cf1ba1"
     */
    private static final String[] NAMESPACE_IDS = {
            "YOUR_NAMESPACES_HERE"
    };
    private static byte[] getNamespaceFilter(String namespaceId) {
        byte[] filter = new byte[18];

        int len = namespaceId.length();
        int index = 2; //Skip frame type + TX power bytes
        for (int i = 0; i < len; i += 2) {
            filter[index++] = (byte) ((Character.digit(namespaceId.charAt(i), 16) << 4)
                    + Character.digit(namespaceId.charAt(i+1), 16));
        }

        return filter;
    }

    // Filter that forces frame type and namespace id to match
    private static final byte[] NAMESPACE_FILTER_MASK = {
            (byte)0xFF,
            0x00,
            (byte)0xFF, (byte)0xFF, (byte)0xFF, (byte)0xFF, (byte)0xFF,
            (byte)0xFF, (byte)0xFF, (byte)0xFF, (byte)0xFF, (byte)0xFF,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00
    };

    private static String getNamespaceIdFromScan(byte[] scanData) {
        StringBuilder sb = new StringBuilder();
        for (int i=2; i < 12; i++) {
            sb.append(String.format("%02x", scanData[i]));
        }

        return sb.toString();
    }

    // Eddystone frame types
    private static final byte TYPE_UID = 0x00;
    private static final byte TYPE_URL = 0x10;
    private static final byte TYPE_TLM = 0x20;

    private static final int NOTIFICATION_ID = 42;

    private NotificationManager mNotificationManager;
    private BluetoothLeScanner mBluetoothLeScanner;
    private ArrayMap<String, Boolean> mDetectedBeacons;

    @Override
    public void onCreate() {
        super.onCreate();
        sRunning = true;

        mNotificationManager =
                (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        BluetoothManager manager =
                (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
        mBluetoothLeScanner = manager.getAdapter().getBluetoothLeScanner();

        mDetectedBeacons = new ArrayMap<>();

        startScanning();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (ACTION_DISMISS.equals(intent.getAction())) {
            //Mark all currently discovered beacons as "seen"
            markAllRead();
            //Hide the notification, if visible
            mNotificationManager.cancel(NOTIFICATION_ID);
        }
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        sRunning = false;

        stopScanning();
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

        Intent deleteAction = new Intent(this, EddystoneScannerService.class);
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

    /* Begin scanning for Eddystone advertisers */
    private void startScanning() {
        List<ScanFilter> filters = new ArrayList<>();
        //Filter on just our requested namespaces
        for (String namespace : NAMESPACE_IDS) {
            ScanFilter beaconFilter = new ScanFilter.Builder()
                    .setServiceUuid(UID_SERVICE)
                    .setServiceData(UID_SERVICE, getNamespaceFilter(namespace),
                            NAMESPACE_FILTER_MASK)
                    .build();
            filters.add(beaconFilter);
        }

        //Run in background mode
        ScanSettings settings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_POWER)
                .build();

        mBluetoothLeScanner.startScan(filters, settings, mScanCallback);
        if (DEBUG_SCAN) Log.d(TAG, "Scanning started…");
    }

    /* Terminate scanning */
    private void stopScanning() {
        mBluetoothLeScanner.stopScan(mScanCallback);
        if (DEBUG_SCAN) Log.d(TAG, "Scanning stopped…");
    }

    /* Handle UID packet discovery on the main thread */
    private void processUidPacket(String deviceAddress, int rssi, byte[] packet) {
        if (DEBUG_SCAN) {
            String id = getNamespaceIdFromScan(packet);
            Log.d(TAG, "Eddystone(" + deviceAddress + ") id = " + id);
        }

        if (!mDetectedBeacons.containsKey(deviceAddress)) {
            mDetectedBeacons.put(deviceAddress, false);
            int unreadCount = getUnreadCount();
            if (unreadCount > 0) {
                postScanResultNotification(unreadCount);
            }
        }
    }

    private void markAllRead() {
        for (String key : mDetectedBeacons.keySet()) {
            mDetectedBeacons.put(key, true);
        }
    }

    private int getUnreadCount() {
        int count = 0;
        for (Boolean marker : mDetectedBeacons.values()) {
            if (!marker) count++;
        }

        return count;
    }

    /* Process each unique BLE scan result */
    private ScanCallback mScanCallback = new ScanCallback() {
        private Handler mCallbackHandler =
                new Handler(Looper.getMainLooper());

        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            processResult(result);
        }

        @Override
        public void onScanFailed(int errorCode) {
            Log.w(TAG, "Scan Error Code: " + errorCode);
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            for (ScanResult result : results) {
                processResult(result);
            }
        }

        private void processResult(ScanResult result) {
            ScanRecord record = result.getScanRecord();
            if (record == null) {
                Log.w(TAG, "Invalid scan record.");
                return;
            }
            final byte[] data = record.getServiceData(UID_SERVICE);
            if (data == null) {
                Log.w(TAG, "Invalid Eddystone scan result.");
                return;
            }

            final String deviceAddress = result.getDevice().getAddress();
            final int rssi = result.getRssi();
            byte frameType = data[0];
            switch (frameType) {
                case TYPE_UID:
                    mCallbackHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            processUidPacket(deviceAddress, rssi, data);
                        }
                    });
                    break;
                case TYPE_TLM:
                case TYPE_URL:
                    //Do nothing, ignoring these
                    return;
                default:
                    Log.w(TAG, "Invalid Eddystone scan result.");
            }
        }
    };
}

package com.thecreativecafe.autoprint;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.os.*;
import androidx.core.app.NotificationCompat;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.*;
import java.util.*;
import java.util.concurrent.*;

public class OrderPrintService extends Service {
    private static final String CHANNEL = "cafe_auto_print";
    private FirebaseFirestore db;
    private ListenerRegistration listener;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Map<String, Map<String,Object>> pending = new ConcurrentHashMap<>();
    private final Set<String> printing = ConcurrentHashMap.newKeySet();
    private Runnable retry;

    @Override public void onCreate() {
        super.onCreate();
        createChannel();
        Notification n = new NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentTitle("The Creative Cafe Auto Print")
            .setContentText("નવા ઓર્ડરની રાહ જોઈ રહ્યું છે")
            .setOngoing(true).build();
        if (Build.VERSION.SDK_INT >= 29)
            startForeground(11, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);
        else startForeground(11, n);

        // First activation time prevents old test orders from printing on initial setup.
        SharedPreferences prefs = getSharedPreferences("printed_orders", MODE_PRIVATE);
        if (!prefs.contains("first_started_at"))
            prefs.edit().putLong("first_started_at", System.currentTimeMillis()).apply();
        final long firstStartedAt = prefs.getLong("first_started_at", System.currentTimeMillis());

        db = FirebaseFirestore.getInstance();
        listener = db.collection("orders").addSnapshotListener((snap, error) -> {
            if (error != null || snap == null) {
                if (error != null) updateNotification("Firebase જોડાણની ભૂલ: " + error.getMessage());
                return;
            }
            for (DocumentSnapshot doc : snap.getDocuments()) {
                String status = doc.getString("status");
                Timestamp created = doc.getTimestamp("createdAt");
                if ("New Order".equalsIgnoreCase(status) && created != null
                        && created.toDate().getTime() >= firstStartedAt && !isPrinted(doc.getId()))
                    pending.put(doc.getId(), doc.getData());
            }
            attemptPending();
        });
        retry = new Runnable() {
            @Override public void run() { attemptPending(); handler.postDelayed(this, 20000); }
        };
        handler.postDelayed(retry, 20000);
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel c = new NotificationChannel(CHANNEL, "Cafe Auto Print", NotificationManager.IMPORTANCE_LOW);
            getSystemService(NotificationManager.class).createNotificationChannel(c);
        }
    }

    private boolean isPrinted(String id) {
        return getSharedPreferences("printed_orders", MODE_PRIVATE).getBoolean(id, false);
    }

    private void attemptPending() {
        for (String id : new ArrayList<>(pending.keySet())) {
            if (!printing.add(id)) continue;
            Map<String,Object> data = pending.get(id);
            worker.execute(() -> {
                try {
                    String address = getSharedPreferences("cafe_print", MODE_PRIVATE).getString("printer_address", null);
                    if (address == null) return;
                    ReceiptPrinter.print(this, address, id, data);
                    // Save locally first so a Firestore listener update cannot enqueue this order again.
                    getSharedPreferences("printed_orders", MODE_PRIVATE).edit().putBoolean(id, true).apply();
                    pending.remove(id);
                    Map<String, Object> printUpdate = new HashMap<>();
                    printUpdate.put("printStatus", "Printed");
                    printUpdate.put("printedAt", FieldValue.serverTimestamp());
                    printUpdate.put("printError", FieldValue.delete());
                    db.collection("orders").document(id).update(printUpdate);
                    handler.post(() -> updateNotification("ઓર્ડર " + id.substring(0, Math.min(8, id.length())).toUpperCase() + " પ્રિન્ટ થયો"));
                } catch (Exception e) {
                    String message = e.getMessage() == null ? "Unknown print error" : e.getMessage();
                    Map<String, Object> failureUpdate = new HashMap<>();
                    failureUpdate.put("printStatus", "Failed");
                    failureUpdate.put("printError", message);
                    db.collection("orders").document(id).update(failureUpdate);
                    handler.post(() -> updateNotification("પ્રિન્ટ બાકી છે: " + message));
                } finally { printing.remove(id); }
            });
        }
    }

    private void updateNotification(String message) {
        Notification n = new NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentTitle("The Creative Cafe Auto Print")
            .setContentText(message).setOngoing(true).build();
        getSystemService(NotificationManager.class).notify(11, n);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) { return START_STICKY; }
    @Override public void onDestroy() {
        if (listener != null) listener.remove();
        if (retry != null) handler.removeCallbacks(retry);
        worker.shutdownNow();
        super.onDestroy();
    }
    @Override public android.os.IBinder onBind(Intent intent) { return null; }
}

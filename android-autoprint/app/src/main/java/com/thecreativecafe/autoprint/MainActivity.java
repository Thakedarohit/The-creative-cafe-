package com.thecreativecafe.autoprint;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.*;
import java.util.ArrayList;
import java.util.Set;

public class MainActivity extends Activity {
    private static final int PERMISSION_REQUEST = 44;
    private Spinner devices;
    private TextView status;
    private ArrayList<BluetoothDevice> paired = new ArrayList<>();
    private BluetoothAdapter adapter;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(28, 36, 28, 28);
        root.setBackgroundColor(0xFF101010);

        TextView title = new TextView(this);
        title.setText("THE CREATIVE CAFE\nAUTO PRINT");
        title.setTextColor(0xFFE8BD59);
        title.setTextSize(25);
        title.setPadding(0,0,0,24);
        root.addView(title);

        TextView info = new TextView(this);
        info.setText("58mm Bluetooth Printer Setup\nદરેક ગ્રાહકનો અલગ બિલ આપમેળે પ્રિન્ટ થશે.");
        info.setTextColor(0xFFFFFFFF);
        info.setTextSize(16);
        root.addView(info);

        devices = new Spinner(this);
        root.addView(devices, new LinearLayout.LayoutParams(-1, 60));

        Button refresh = new Button(this);
        refresh.setText("જોડાયેલા Bluetooth પ્રિન્ટર શોધો");
        root.addView(refresh);
        refresh.setOnClickListener(v -> loadDevices());

        Button save = new Button(this);
        save.setText("આ પ્રિન્ટર પસંદ કરો");
        root.addView(save);
        save.setOnClickListener(v -> saveDevice());

        Button start = new Button(this);
        start.setText("ઓટો પ્રિન્ટ ચાલુ કરો");
        root.addView(start);
        start.setOnClickListener(v -> startPrinting());

        Button stop = new Button(this);
        stop.setText("ઓટો પ્રિન્ટ બંધ કરો");
        root.addView(stop);
        stop.setOnClickListener(v -> stopService(new Intent(this, OrderPrintService.class)));

        status = new TextView(this);
        status.setTextColor(0xFFE8BD59);
        status.setTextSize(14);
        status.setPadding(0,24,0,0);
        root.addView(status, new LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(root);

        adapter = BluetoothAdapter.getDefaultAdapter();
        requestNeededPermissions();
        loadDevices();
    }

    private void requestNeededPermissions() {
        ArrayList<String> ask = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= 31 && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED)
            ask.add(Manifest.permission.BLUETOOTH_CONNECT);
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            ask.add(Manifest.permission.POST_NOTIFICATIONS);
        if (!ask.isEmpty()) requestPermissions(ask.toArray(new String[0]), PERMISSION_REQUEST);
    }

    private boolean canConnect() {
        return Build.VERSION.SDK_INT < 31 || checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
    }

    private void loadDevices() {
        if (adapter == null) { status.setText("આ મોબાઇલમાં Bluetooth ઉપલબ્ધ નથી."); return; }
        if (!canConnect()) { status.setText("Bluetooth permission આપો અને ફરી પ્રયાસ કરો."); return; }
        paired.clear();
        ArrayList<String> labels = new ArrayList<>();
        try {
            Set<BluetoothDevice> set = adapter.getBondedDevices();
            for (BluetoothDevice d : set) {
                paired.add(d);
                labels.add(d.getName() + " — " + d.getAddress());
            }
        } catch (SecurityException e) { status.setText("Bluetooth permission જરૂરી છે."); }
        if (labels.isEmpty()) labels.add("હજુ કોઈ paired printer નથી");
        devices.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, labels));
        status.setText("મોબાઇલમાં પહેલાં પ્રિન્ટર Pair કરો. પછી અહીં પસંદ કરો.");
    }

    private void saveDevice() {
        if (paired.isEmpty() || devices.getSelectedItemPosition() >= paired.size()) {
            status.setText("પહેલાં Android Bluetooth Settings માં પ્રિન્ટર Pair કરો."); return;
        }
        BluetoothDevice d = paired.get(devices.getSelectedItemPosition());
        getSharedPreferences("cafe_print", MODE_PRIVATE).edit().putString("printer_address", d.getAddress()).apply();
        status.setText("પ્રિન્ટર પસંદ થયું: " + d.getName());
    }

    private void startPrinting() {
        String address = getSharedPreferences("cafe_print", MODE_PRIVATE).getString("printer_address", null);
        if (address == null) { status.setText("પહેલાં Bluetooth પ્રિન્ટર પસંદ કરો."); return; }
        if (Build.VERSION.SDK_INT >= 31 && !canConnect()) { requestNeededPermissions(); return; }
        Intent i = new Intent(this, OrderPrintService.class);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i); else startService(i);
        status.setText("ઓટો પ્રિન્ટ સેવા શરૂ કરવાની વિનંતી મોકલી છે. સૂચના દેખાય તે તપાસો.");
    }
}

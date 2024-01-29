package de.testo.datalayersample_java;

import android.app.Activity;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import de.testo.datalayer.LibraryLoaderKt;
import de.testo.datalayer.bluetooth.BluetoothInfo;
import de.testo.datalayer.probes.MeasData;
import de.testo.datalayer.probes.MeasType;
import de.testo.datalayer.probes.Probe;
import de.testo.datalayer.probes.ProbeFactory;
import de.testo.datalayer.probes.ProbeType;
import kotlin.Unit;
import kotlin.jvm.functions.Function1;

public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LibraryLoaderKt.loadLibrary();
        m_factory = new ProbeFactory(getApplication());
        requestPermissionsForBluetooth(this);
        m_factory.startBtScan();
        setContentView(R.layout.activity_main);

        m_scrollView = findViewById(R.id.scrollView);
        m_lstProbes = new java.util.ArrayList<>();
        Button btnDiscover = findViewById(R.id.btnDiscover);
        btnDiscover.setOnClickListener(view -> onDiscoverClick());
        Button btnConnect = findViewById(R.id.btnConnect);
        btnConnect.setOnClickListener(view -> onConnectClick());
        Button btnBattery = findViewById(R.id.btnBattery);
        btnBattery.setOnClickListener(view -> onBatteryClick());
        Button btnDisconnect = findViewById(R.id.btnDisconnect);
        btnDisconnect.setOnClickListener(view -> onDisconnectClick());
        m_tvText = findViewById(R.id.text);
    }
    private void requestPermissionsForBluetooth(Activity requestingActivity) {
        if (requestingActivity != null) {
            if (m_factory != null) {
                ActivityCompat.requestPermissions(
                        requestingActivity, m_factory.getRequiredPermissions(), 1
                );
            }
        }
    }
    private void onDiscoverClick() {
        for (BluetoothInfo btInfo : m_factory.getConnectableDevices()) {
            appendText(btInfo.toString());
        }
    }
    private void onConnectClick() {
        for(BluetoothInfo btInfo : m_factory.getConnectableDevices()) {
            Thread connectThread = new Thread(() -> runOnUiThread(() -> {
                Probe probe = m_factory.create(btInfo);
                m_lstProbes.add(probe);
                appendText("connected to " + btInfo.toString());
                if(probe.getType() == ProbeType.MF_HANDLE || probe.getType() == ProbeType.QSR_HANDLE) {
                    probe.subscribeNotification(MeasType.OIL_QUALITY, this::receiveMeasValue);
                    probe.subscribeNotification(MeasType.TEMPERATURE, this::receiveMeasValue);
                }
                else if(probe.getType() == ProbeType.T104_IR_BT) {
                    probe.subscribeNotification(MeasType.SURFACE_TEMPERATURE, this::receiveMeasValue);
                    probe.subscribeNotification(MeasType.PLUNGE_TEMPERATURE, this::receiveMeasValue);
                }
            }));
            connectThread.start();
        }
        Button btnConnect = findViewById(R.id.btnConnect);
        btnConnect.setEnabled(false);
    }
    private void onBatteryClick() {
        appendText("battery level:");
        for (Probe probe : m_lstProbes) {
            appendText(String.format("  %s (%s): %.2f", probe.getDeviceId(), probe.getType().toString() ,probe.getBatteryLevel()));
        }
    }
    private void onDisconnectClick() {
        for (Probe probe : m_lstProbes) {
            appendText("disconnected from " + probe.getDeviceId());
            probe.disconnect();
        }
        m_lstProbes.clear();
        Button btnConnect = findViewById(R.id.btnConnect);
        btnConnect.setEnabled(true);
    }
    private void appendText(String text) {
        m_tvText.append(text + "\n");
    }
    private void clearText() {
        m_tvText.setText("");
    }
    private kotlin.Unit receiveMeasValue(MeasData measData) {
        appendText(String.format("%s %.2f %s", measData.getMeasType().toString(), measData.getProbeValue().getValue(), measData.getPhysicalUnit()));
        return null;
    }
    private ProbeFactory m_factory;
    private TextView m_tvText;
    private ScrollView m_scrollView;
    private ArrayList<Probe> m_lstProbes;
}
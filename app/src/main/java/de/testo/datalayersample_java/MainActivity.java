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
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import de.testo.datalayer.LibraryLoaderKt;
import de.testo.datalayer.bluetooth.BluetoothInfo;
import de.testo.datalayer.probes.MeasData;
import de.testo.datalayer.probes.MeasType;
import de.testo.datalayer.probes.Probe;
import de.testo.datalayer.probes.ProbeFactory;
import de.testo.datalayer.probes.ProbeType;

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
        m_lstProbes = Collections.synchronizedMap(new HashMap<String, Probe>());
        Button btnDiscover = findViewById(R.id.btnDiscover);
        btnDiscover.setOnClickListener(view -> onDiscoverClick());
        Button btnConnect = findViewById(R.id.btnConnect);
        btnConnect.setOnClickListener(view -> onConnectClick());
        Button btnBattery = findViewById(R.id.btnBattery);
        btnBattery.setOnClickListener(view -> onBatteryClick());
        Button btnDisconnect = findViewById(R.id.btnDisconnect);
        btnDisconnect.setOnClickListener(view -> onDisconnectClick());
        m_tvText = findViewById(R.id.text);
        m_checkThread = new Thread(() -> {
            while(true) {
                try {
                    for(Iterator<Map.Entry<String, Probe>> it = m_lstProbes.entrySet().iterator(); it.hasNext(); ) {
                        Map.Entry<String, Probe> entry = it.next();
                        Probe probe = entry.getValue();
                        if(!probe.isDeviceAvailable()) {
                            appendText("disconnected from " + entry.getKey());
                            probe.disconnect();
                            it.remove();
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                if(m_lstProbes.size() == 0) {
                    m_factory.startBtScan();
                }
                runOnUiThread(() -> {
                    boolean bEnable = true;
                    if(m_factory.getConnectableDevices().size() == 0) {
                        bEnable = false;
                    }
                    btnConnect.setEnabled(bEnable);
                });
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });
        m_checkThread.start();
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
        m_factory.startBtScan();
        if(m_factory.getConnectableDevices().size() == 0) {
            appendText("no devices found");
            return;
        } else {
            Button btnConnect = findViewById(R.id.btnConnect);
            btnConnect.setEnabled(true);
        }
        appendText("discovered devices:");
        for (BluetoothInfo btInfo : m_factory.getConnectableDevices()) {
            appendText(btInfo.toString());
        }
    }
    private void onConnectClick() {
        Thread connectThread = new Thread(() -> {
            try {
                for (BluetoothInfo btInfo : m_factory.getConnectableDevices()) {
                    Probe probe = m_factory.create(btInfo);
                    m_lstProbes.put(btInfo.getSerialNo(), probe);
                    appendText("connected to " + btInfo);
                    if (probe.getType() == ProbeType.MF_HANDLE || probe.getType() == ProbeType.QSR_HANDLE) {
                        probe.subscribeNotification(MeasType.OIL_QUALITY, this::receiveMeasValue);
                        probe.subscribeNotification(MeasType.TEMPERATURE, this::receiveMeasValue);
                    } else if (probe.getType() == ProbeType.T104_IR_BT) {
                        probe.subscribeNotification(MeasType.SURFACE_TEMPERATURE, this::receiveMeasValue);
                        probe.subscribeNotification(MeasType.PLUNGE_TEMPERATURE, this::receiveMeasValue);
                    }
                }
                m_factory.stopBtScan();
            } catch (Exception e) { e.printStackTrace(); }
        });
        connectThread.start();
    }
    private void onBatteryClick() {
        appendText("battery level:");
        Thread batteryThread = new Thread(() -> {
            for (Probe probe : m_lstProbes.values()) {
                appendText(String.format("  %s (%s): %.2f", probe.getDeviceId(), probe.getType().toString() ,probe.getBatteryLevel()));
            }
        });
        batteryThread.start();
    }
    private void onDisconnectClick() {
        try {
            for(Iterator<Map.Entry<String, Probe>> it = m_lstProbes.entrySet().iterator(); it.hasNext(); ) {
                Map.Entry<String, Probe> entry = it.next();
                appendText("disconnected from " + entry.getKey());
                entry.getValue().disconnect();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        m_lstProbes.clear();
        m_factory.startBtScan();
    }
    private void appendText(String text) {
        runOnUiThread(() -> {
            m_tvText.append(text + "\n");
        });
    }
    private void clearText() {
        runOnUiThread(() -> {
            m_tvText.setText("");
        });
    }
    private kotlin.Unit receiveMeasValue(MeasData measData) {
        appendText(String.format("%s %.2f %s", measData.getMeasType().toString(), measData.getProbeValue().getValue(), measData.getPhysicalUnit()));
        return null;
    }
    private ProbeFactory m_factory;
    private TextView m_tvText;
    private ScrollView m_scrollView;
    private Map<String, Probe> m_lstProbes;

    private Thread m_checkThread;
}
package com.mhandharbeni.androidusbhost;

import androidx.appcompat.app.AppCompatActivity;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;
import com.hoho.android.usbserial.util.SerialInputOutputManager;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity implements SerialInputOutputManager.Listener {
    private static final String TAG = MainActivity.class.getSimpleName();

    private static final String ACTION_USB_PERMISSION = "com.mhandharbeni.androidusbhost.USB_PERMISSION";

    private static final int WRITE_WAIT_MILLIS = 2000;
    private static final int READ_WAIT_MILLIS = 2000;

    private static final int CONFIG_BAUDRATE = 9600;
    private static final int CONFIG_DATABIT = 8;

    private final BroadcastReceiver usbReceiver = new BroadcastReceiver() {

        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action)) {
                synchronized (this) {
                    UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);

                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        if(device != null){
                            //call method to set up device communication
                        }
                    }
                    else {
                        Log.d(TAG, "permission denied for device " + device);
                    }
                }
            }
        }
    };

    private TextView textLog;
    private EditText inputCommand;
    private Button btnSend;


    private UsbManager manager;
    List<UsbSerialDriver> availableDrivers = new ArrayList<>();
    UsbSerialDriver driver;
    UsbDeviceConnection connection;

    PendingIntent permissionIntent;
    IntentFilter filter;

    UsbSerialPort port;
    SerialInputOutputManager usbIoManager;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        textLog = findViewById(R.id.textLog);
        inputCommand = findViewById(R.id.inputCommand);
        btnSend = findViewById(R.id.btnSend);

        btnSend.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                send(inputCommand.getText().toString());
            }
        });

        getAttachedDevices();
    }

    void getAttachedDevices(){
        // Find all available drivers from attached devices.
        manager = (UsbManager) getSystemService(Context.USB_SERVICE);
        availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(manager);
        if (!availableDrivers.isEmpty()) {
            openDevices();
        }
    }

    void openDevices(){
        driver = availableDrivers.get(0);
        connection = manager.openDevice(driver.getDevice());

        if (connection == null) {
            permissionIntent = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION), 0);
            filter = new IntentFilter(ACTION_USB_PERMISSION);
            registerReceiver(usbReceiver, filter);

            manager.requestPermission(driver.getDevice(), permissionIntent);
            return;
        }

        listenData();
    }

    void listenData(){
        try {
            port = driver.getPorts().get(0);
            port.open(connection);
            port.setParameters(CONFIG_BAUDRATE, CONFIG_DATABIT, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);

            usbIoManager = new SerialInputOutputManager(port, this);

            Executors.newSingleThreadExecutor().submit(usbIoManager);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    void writeData(){
        try {
        } catch (Exception e){}
    }

    void send(String str){
        try {
            byte[] data = (str + '\n').getBytes();
            SpannableStringBuilder spn = new SpannableStringBuilder();
            spn.append("send ").append(String.valueOf(data.length)).append(" bytes\n");
            spn.append(HexDump.dumpHexString(data)).append("\n");
            spn.setSpan(new ForegroundColorSpan(getResources().getColor(R.color.colorPrimary)), 0, spn.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            textLog.append(spn);
            port.write(data, WRITE_WAIT_MILLIS);
        } catch (Exception e) {
            onRunError(e);
        }
    }
    @Override
    public void onNewData(final byte[] data) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                textLog.setText(HexDump.dumpHexString(data));
            }
        });
    }

    @Override
    public void onRunError(final Exception e) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                textLog.setText(e.getMessage());
            }
        });
    }

    void disconnect(){
        try {
            if (usbIoManager != null){
                usbIoManager.stop();
            }
            port.close();

            usbIoManager = null;
            port = null;
        } catch (Exception e){}
    }

    @Override
    protected void onPause() {
        super.onPause();
        disconnect();
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        getAttachedDevices();
    }

    @Override
    protected void onResume() {
        super.onResume();
        getAttachedDevices();
    }
}
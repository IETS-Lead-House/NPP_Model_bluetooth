package ru.iets.npp_model_bluetooth;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.bluetooth.le.AdvertiseSettings;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.ScrollView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import ru.iets.npp_model_bluetooth.databinding.ActivityMainBinding;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {
    private final Disposable SENDER = Observable.interval(1500, TimeUnit.MILLISECONDS, Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(tick -> {
                if(socket != null) {
                    sendDataToActiveHost();
                }
            }, throwable -> log("thread", throwable));
    private ActivityMainBinding binding;
    private BluetoothAdapter bluetooth;
    private static BluetoothSocket socket = null;
    private EnumBluetoothStatus status = EnumBluetoothStatus.DISABLED;

    private BroadcastReceiver btrNew;

    private BroadcastReceiver btrStatus;

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(btrNew);
        unregisterReceiver(btrStatus);
        SENDER.dispose();
        closeSocket();
    }
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        if(!getApplicationContext().getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH)) {
            Toast.makeText(this, R.string.no_bluetooth, Toast.LENGTH_LONG).show();
            return;
        }

        bluetooth = BluetoothAdapter.getDefaultAdapter();

        BluetoothDeviceAdapter devicesAdapter = new BluetoothDeviceAdapter(this, bluetooth.getBondedDevices());
        btrNew = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if(BluetoothDevice.ACTION_FOUND.equals(intent.getAction())) {
                    BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    devicesAdapter.add(device);
                }
            }
        };
        this.registerReceiver(btrNew, new IntentFilter(BluetoothDevice.ACTION_FOUND));

        binding.devicesList.setAdapter(devicesAdapter);

        status = bluetooth.isEnabled() ? EnumBluetoothStatus.ENABLED : EnumBluetoothStatus.DISABLED;
        recolorButton();
        btrStatus = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if(BluetoothAdapter.ACTION_STATE_CHANGED.equals(intent.getAction())) {
                    switch(intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)) {
                        case BluetoothAdapter.STATE_OFF:
                            status = EnumBluetoothStatus.DISABLED;
                            Toast.makeText(getApplicationContext(), R.string.disable_established, Toast.LENGTH_LONG).show();
                        case BluetoothAdapter.STATE_TURNING_OFF: {
                            binding.bluetoothIcon.setColorFilter(getApplicationContext().getColor(R.color.colorDisabled));
                            binding.connectButton.setText(R.string.connect);
                            break;
                        }
                        default:
                        case BluetoothAdapter.STATE_ON:
                            status = EnumBluetoothStatus.ENABLED;
                            Toast.makeText(getApplicationContext(), R.string.enable_established, Toast.LENGTH_LONG).show();
                        case BluetoothAdapter.STATE_TURNING_ON: {
                            binding.bluetoothIcon.setColorFilter(getApplicationContext().getColor(R.color.colorEnabled));
                            binding.connectButton.setText(R.string.connect);
                            break;
                        }
                        case BluetoothAdapter.STATE_CONNECTED:
                            status = EnumBluetoothStatus.CONNECTED;
                            binding.connectButton.setText(R.string.disconnect);
                            Toast.makeText(getApplicationContext(), R.string.connect_established, Toast.LENGTH_LONG).show();
                        case BluetoothAdapter.STATE_CONNECTING: {
                            binding.bluetoothIcon.setColorFilter(getApplicationContext().getColor(R.color.colorConnected));
                            binding.connectButton.setText(R.string.disconnect);
                            break;
                        }
                        case BluetoothAdapter.STATE_DISCONNECTED:
                            Toast.makeText(getApplicationContext(), R.string.disable_established, Toast.LENGTH_LONG).show();
                        case BluetoothAdapter.STATE_DISCONNECTING: {
                            binding.connectButton.setText(R.string.connect);
                            closeSocket();
                            break;
                        }

                    }

                }
            }
        };
        registerReceiver(btrStatus, new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));
        binding.bluetoothIcon.setOnClickListener(
                View -> enableBluetooth()
        );
        binding.connectButton.setOnClickListener(
                View -> {
                    if(status == EnumBluetoothStatus.ENABLED) {
                        Object sus = binding.devicesList.getSelectedItem();
                        if(sus instanceof BluetoothDevice) {
                            makeChannel((BluetoothDevice) sus);
                        }
                        else {
                            Toast.makeText(this, R.string.no_device, Toast.LENGTH_LONG).show();
                        }
                    } else if (status == EnumBluetoothStatus.CONNECTED){
                        closeSocket();
                    }
                }
        );
    }

    private void enableBluetooth() {
        if(bluetooth == null) {
            return;
        }
        if(!bluetooth.isEnabled()) {
            startActivity(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE));
        } else {
            Toast msg;
            switch (status) {
                case ENABLED: {msg = Toast.makeText(this, R.string.enabled, Toast.LENGTH_LONG); break;}
                case CONNECTED: {msg = Toast.makeText(this, R.string.connected, Toast.LENGTH_LONG); break;}
                default: return;
            }
            msg.show();

        }
    }

    private void makeChannel(BluetoothDevice device) {
        // some dirt is going on there
        try {
            Method m = device.getClass().getMethod(
                    "createRfcommSocket", new Class[] {int.class});
            socket = (BluetoothSocket) m.invoke(device, 1);

            //socket = device.createInsecureRfcommSocketToServiceRecord(insecureUUID);//createRfcommSocketToServiceRecord(insecureUUID);
            socket.connect();
            status = EnumBluetoothStatus.CONNECTED;
            Toast.makeText(this, R.string.device_connected, Toast.LENGTH_LONG).show();
            recolorButton();
        } catch(IOException | InvocationTargetException | NoSuchMethodException | IllegalAccessException e) {
            closeSocket();
            log("Connection", e);
        } catch(NullPointerException e) {
            Toast.makeText(this, R.string.disconnect_established, Toast.LENGTH_LONG).show();
            closeSocket();
        }
    }

    private synchronized void sendDataToActiveHost() {
        try {
            OutputStream write = socket.getOutputStream();
            write.write(readControls());
            write.flush();
        } catch (IOException e) {
            closeSocket();
            log("Write", e);
        }

    }

    private byte[] readControls() {
        byte[] controls = new byte[]{
                17,
                19,
                (byte)(3 * (binding.leftStick.getNormalizedX() - 50) / 10),
                (byte)(3 * (50 - binding.leftStick.getNormalizedY()) / 10),
                (byte)(3 * (binding.rightStick.getNormalizedX() - 50) / 10),
                (byte)(3 * (50 - binding.rightStick.getNormalizedY()) / 10),
                (byte)((binding.leftButton.isPressed()? 1 : 0) + (binding.rightButton.isPressed()? 1 : 0) * 2), // not full
                0
        };
        controls[7] = (byte) ((
                cppMoment(controls[2])
                        + 7 * cppMoment(controls[3])
                        + 11 * cppMoment(controls[4])
                        + 13 * cppMoment(controls[5])
                        + 17 * cppMoment(controls[6])
        ) % 255);
        return controls;
    }

    private int cppMoment(int a) {
        return a < 0 ? a + 256 : a;
    }

    private void closeSocket() {
        if(socket != null) {
            try {
                socket.close();
            } catch (IOException e) {
                log("Closing", e);
            } finally {
                socket = null;
                status = bluetooth.isEnabled() ? EnumBluetoothStatus.ENABLED : EnumBluetoothStatus.DISABLED;
            }
        }
        recolorButton();
    }

    private void log(String where, Throwable e) {
        binding.log.append("\n" + where+ " failed with:" + e.getMessage());
        Log.e(where+" Channel", where+" failed");
        e.printStackTrace();
        binding.infoLayout.fullScroll(ScrollView.FOCUS_DOWN);
    }

    private void recolorButton() {
        int color = getApplicationContext().getColor(bluetooth.isEnabled() ? R.color.colorEnabled : R.color.colorDisabled);
        if(bluetooth.isEnabled()) {
            color = getApplicationContext().getColor(status == EnumBluetoothStatus.CONNECTED ? R.color.colorConnected : R.color.colorEnabled);
        }

        binding.bluetoothIcon.setColorFilter(color);
    }

    enum EnumBluetoothStatus {
        DISABLED, ENABLED, CONNECTED
    }

}
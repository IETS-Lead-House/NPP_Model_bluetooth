package ru.iets.npp_model_bluetooth;

import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Set;

class BluetoothDeviceAdapter extends ArrayAdapter<BluetoothDevice> {

    public BluetoothDeviceAdapter(@NonNull Context context, Set<BluetoothDevice> set) {
        // may be stupid solu
        super(context, 0, new ArrayList<>(set));
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        return initItem(position, convertView, parent);
    }

    @Override
    public View getDropDownView(int position, @Nullable @org.jetbrains.annotations.Nullable View convertView, @NonNull @NotNull ViewGroup parent) {
        return initItem(position, convertView, parent);
    }

    private View initItem(int position, View convertView, ViewGroup parent) {
        if(convertView == null) {
            convertView = LayoutInflater.from(getContext()).inflate(R.layout.bluetooth_device_item, parent, false);
        }

        TextView nameView = convertView.findViewById(R.id.bluetooth_device_item__name);
        TextView addressView = convertView.findViewById(R.id.bluetooth_device_item__address);
        BluetoothDevice currItem = getItem(position);

        if(currItem!= null) {
            nameView.setText(currItem.getName());
            addressView.setText(currItem.getAddress());
        }
        return convertView;
    }

}
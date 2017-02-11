package com.nicomazz.inline_speed_manager.Bluetooth;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Created by Nicolò Mazzucato (nicomazz97) on 10/02/17 23.16.
 */

public class BTReceiverManager {
    private BluetoothDevice BTReceiver;

    private OnTimeReceived timeReceivedListener;
    private BTStatusInterface btStatusListener;
    private Context context;

    private Activity activity;
    private BluetoothSocket btSocket = null;
    private boolean isBtConnected = false;
    private static final UUID myUUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    private String getReceiverName() {
        return "speed";
    }



    public BTReceiverManager(OnTimeReceived timeReceivedListener, BTStatusInterface btStatusListener, Activity activity) {
        this.activity = activity;
        this.timeReceivedListener = timeReceivedListener;
        this.context = activity;
        this.btStatusListener = btStatusListener;
    }

    private void init(Activity activity) {
        if (!isBtEnabled(activity))
            return;
        BTReceiver = getReceiverDevice();
        if (BTReceiver == null) return;
        new ConnectBT().execute();
    }

    public  boolean isBtEnabled(Activity activity) {
        BluetoothAdapter myBluetooth = BluetoothAdapter.getDefaultAdapter();
        if (myBluetooth == null) {
            log("Bluetooth Not Available");
            return false;
        }
        if (!myBluetooth.isEnabled()) {
            //Ask to the user turn the bluetooth on
            Intent turnBTon = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            activity.startActivityForResult(turnBTon, 1);
            return false;
        }
        return true;
    }

    private BluetoothDevice getReceiverDevice() {
        ArrayList<BluetoothDevice> speedReceiverDevices
                = getReceiverWithNameContains(getReceiverName().toLowerCase());

        if (speedReceiverDevices.size() == 0) {
            log("No bounded devices found.");
            return null;
        }
        if (speedReceiverDevices.size() > 1) {
            log("Too many speed devices. return one at random.");
        }
        return speedReceiverDevices.get(new Random().nextInt(speedReceiverDevices.size()));
    }

    private ArrayList<BluetoothDevice> getReceiverWithNameContains(String s) {
        ArrayList<BluetoothDevice> devices = new ArrayList<>();
        Set<BluetoothDevice> pairedDevices = BluetoothAdapter.getDefaultAdapter().getBondedDevices();
        for (BluetoothDevice device : pairedDevices)
            if (device.getName().toLowerCase().contains(s))
                devices.add(device);
        return devices;
    }

    //todo set this in preferences


    private class ConnectBT extends AsyncTask<Void, Void, Void> {
        private boolean ConnectSuccess = true;

        @Override
        protected void onPreExecute() {
            log("Connecting...");  //show a progress dialog
        }

        @Override
        protected Void doInBackground(Void... devices) {
            try {
                if (isConnected()) return null;
                btSocket = BTReceiver.createInsecureRfcommSocketToServiceRecord(myUUID);//create a RFCOMM (SPP) connection
                BluetoothAdapter.getDefaultAdapter().cancelDiscovery();
                btSocket.connect();//start connection
            } catch (IOException e) {
                ConnectSuccess = false;//if the try failed, you can check the exception here
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void result) //after the doInBackground, it checks if everything went fine
        {
            super.onPostExecute(result);
            if (!ConnectSuccess) {
                log("Connection failed.");
                return;
            }
            log("Connected");
            isBtConnected = true;

            new ReceiverThread(btSocket).start();
        }
    }

    public boolean isConnected() {
        return btSocket != null && isBtConnected;
    }


    private void disconnect() {
        if (!isConnected()) return; //If the btSocket is busy

        try {
            btSocket.close(); //close connection
        } catch (IOException e) {
            e.printStackTrace();
            log("Error in disconnecting");
        }
    }

    private void handleMessageReceived(String received) {
        final String[] ss = received.split("-");
        final ArrayList<Long> millisToSend = parseMillis(ss);
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                for (Long millis : millisToSend)
                    timeReceivedListener.onTimeReceived(millis);
            }
        });
    }

    private ArrayList<Long> parseMillis(String[] millisStr) {
        ArrayList<Long> millisToSend = new ArrayList<>();
        for (String s : millisStr) {
            try {
                Long millis = Long.parseLong(s);
                millisToSend.add(millis);
            } catch (Exception e) {
                log("ReceivedStrangeThings: "+s);
            }
        }
        return millisToSend;
    }

    //create new class for connect thread
    private class ReceiverThread extends Thread {
        private InputStream mmInStream;

        //creation of the connect thread
        public ReceiverThread(BluetoothSocket socket) {
            try {
                mmInStream = socket.getInputStream();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        public void run() {
            byte[] buffer = new byte[256];
            int bytes;

            // Keep looping to listen for received messages
            while (true) {
                try {
                    bytes = mmInStream.read(buffer);            //read bytes from input buffer
                    String readMessage = new String(buffer, 0, bytes);
                    handleMessageReceived(readMessage);
                } catch (IOException e) {
                    break;
                }
            }
        }
    }

    //write method
    public void write(String input) {
        if (!isConnected()) return;

        try {
            btSocket.getOutputStream().write(input.getBytes());
        } catch (IOException e) {
            Toast.makeText(context, "Connection Failure", Toast.LENGTH_LONG).show();
        }

    }

    public interface OnTimeReceived {
        void onTimeReceived(long time);
    }

    public interface BTStatusInterface {
        void onBtStatusUpdated(String s);
    }

    public void onPause() {
        disconnect();
    }

    public void onResume() {
        init(activity);
    }

    private void log(String s){
        btStatusListener.onBtStatusUpdated(s);
    }
}
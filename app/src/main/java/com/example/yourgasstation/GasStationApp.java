package com.example.yourgasstation;

import android.app.Application;

/**
 * Created by Lolzzzz on 04-Nov-16.
 */
public class GasStationApp extends Application {

    private static GasStationApp mInstance;

    @Override
    public void onCreate() {
        super.onCreate();

        mInstance = this;
    }

    public static synchronized GasStationApp getInstance() {
        return mInstance;
    }

    public void setConnectivityListener(ConnectivityReceiver.ConnectivityReceiverListener listener) {
        ConnectivityReceiver.connectivityReceiverListener = listener;
    }
}
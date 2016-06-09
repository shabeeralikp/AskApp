package com.example.shabeerali.askapp;

import android.os.StrictMode;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.widget.TextView;
import java.net.URL;
import java.net.HttpURLConnection;
import java.io.InputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.content.Context;
import android.app.AlertDialog;
import android.content.DialogInterface;
import org.json.JSONException;
import org.json.JSONObject;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.BroadcastReceiver;

public class AskAppActivity extends AppCompatActivity {
    MyHandler mHandler;
    TextView myView;
    BackGroundThread mThread;
    boolean appStarted;
    ConnectivityManager mCm;
    BroadcastReceiver networkStateReceiver;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        StrictMode.enableDefaults();
        super.onCreate(savedInstanceState);
        mCm = (ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);
        mHandler = null;
        mThread = null;
        setContentView(R.layout.activity_ask_app);
        myView = (TextView) findViewById(R.id.myTextView);


        networkStateReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if(!isInternetConnected()) {
                    showMessage("No Internet Connectivity", true);
                    onPause();
                }
            }
        };


        IntentFilter filter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
        registerReceiver(networkStateReceiver, filter);
    }

    private boolean isInternetConnected() {
        NetworkInfo activeNetwork = mCm.getActiveNetworkInfo();
        return activeNetwork != null &&
                activeNetwork.isConnected();
    }

    private void showMessage(String message, final boolean closeApp) {
        AlertDialog alertDialog = new AlertDialog.Builder(AskAppActivity.this).create();
        alertDialog.setTitle("No Internet connection");
        alertDialog.setMessage(message);
        alertDialog.setButton(AlertDialog.BUTTON_NEUTRAL, "OK",
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        if (closeApp)
                            finish();
                    }
                });
        alertDialog.show();
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (mHandler == null) {
            mHandler = new MyHandler();
        }

        if (mThread == null) {
            mThread = new BackGroundThread();
            mThread.start();
        } else {
            mThread.onResume();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if(mThread != null)
            mThread.onPause();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if(mThread != null)
            mThread.onStop();
        mThread = null;
        mHandler = null;
        if(networkStateReceiver != null) {
            unregisterReceiver(networkStateReceiver);
            networkStateReceiver = null;
        }
    }


    class MyHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            myView.setText(msg.obj.toString());
        }
    }

    class BackGroundThread extends Thread {

        URL url;
        HttpURLConnection urlConnection = null;
        InputStream in = null;
        String contentAsString = "";
        String joke = "";
        private boolean mFinished = false;
        private boolean mPaused = false;
        private Object mPauseLock;

        public BackGroundThread() {
            mPauseLock = new Object();
        }


        @Override
        public void run() {
            while (true) {
                synchronized (mPauseLock) {
                    if(mFinished) {
                        break;
                    }
                    while (mPaused) {
                        try {
                            mPauseLock.wait();
                        } catch (InterruptedException e) {
                        }
                    }
                }

                try {
                    url = new URL("http://api.icndb.com/jokes/random");
                    urlConnection = (HttpURLConnection) url.openConnection();
                    in = urlConnection.getInputStream();
                    if(in != null) {
                        contentAsString = readStream(in);

                        JSONObject mainObject = new JSONObject(contentAsString);
                        JSONObject uniObject = mainObject.getJSONObject("value");
                        joke = uniObject.getString("joke");

                        Message msg = Message.obtain();
                        msg.obj = joke;
                        if(mHandler != null) {
                            mHandler.sendMessage(msg);
                        }
                        in.close();
                    }
                } catch (IOException e) {
                    Log.e("AskApp", "IOException");
                    break;
                } catch (JSONException e) {
                    Log.e("AskApp", "JSONException");
                    break;
                } finally {
                    urlConnection.disconnect();
                }

                synchronized (mPauseLock) {
                    try {
                        // Waiting for 15 seconds
                        mPauseLock.wait(15000);
                    } catch (InterruptedException e) {
                        Log.e("AskApp", "InterruptedException");
                    }
                }
            }
        }


        public void onPause() {
            synchronized (mPauseLock) {
                mPaused = true;
            }
        }

        public void onResume() {
            synchronized (mPauseLock) {
                mPaused = false;
                mPauseLock.notifyAll();
            }

        }

        public void onStop() {
            synchronized (mPauseLock) {
                mFinished = true;
                mPaused = false;
                mPauseLock.notifyAll();
            }
        }


        private String readStream(InputStream is) throws IOException {
            StringBuilder sb = new StringBuilder();
            BufferedReader r = new BufferedReader(new InputStreamReader(is), 1000);
            for (String line = r.readLine(); line != null; line = r.readLine()) {
                sb.append(line);
            }
            is.close();
            return sb.toString();
        }
    }
}

package com.example.zebra;

import android.app.Activity;
import android.os.AsyncTask;
import android.util.Log;
import android.widget.CompoundButton;

import com.symbol.emdk.EMDKManager;
import com.symbol.emdk.EMDKResults;
import com.symbol.emdk.barcode.BarcodeManager;
import com.symbol.emdk.barcode.ScanDataCollection;
import com.symbol.emdk.barcode.Scanner;
import com.symbol.emdk.barcode.ScannerConfig;
import com.symbol.emdk.barcode.ScannerException;
import com.symbol.emdk.barcode.ScannerInfo;
import com.symbol.emdk.barcode.ScannerResults;
import com.symbol.emdk.barcode.StatusData;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class EMDKWrapper implements EMDKManager.EMDKListener, Scanner.DataListener, Scanner.StatusListener, BarcodeManager.ScannerConnectionListener, CompoundButton.OnCheckedChangeListener {


    // zebra
    private EMDKManager mEmdkManager = null;
    private BarcodeManager mBarcodeManager = null;
    private Scanner mScanner = null;
    private boolean mBContinuousMode = true;

    private List<ScannerInfo> mDeviceList = null;
    private int mScannerIndex = 0; // Keep the selected mScanner
    private int mDefaultIndex = 0; // Keep the default mScanner
    private int mDataLength = 0;
    private String mStatusString = "";

    @Override
    public void onOpened(EMDKManager emdkManager) {
        this.mEmdkManager = emdkManager;
        // Acquire the barcode manager resources
        mBarcodeManager = (BarcodeManager) emdkManager.getInstance(EMDKManager.FEATURE_TYPE.BARCODE);

        // Add connection listener
        if (mBarcodeManager != null) {
            mBarcodeManager.addConnectionListener(this);
        }
        // Enumerate mScanner devices
        enumerateScannerDevices();
        // Set default mScanner
        getZebraScanner();

    }

    @Override
    public void onClosed() {
        if (mEmdkManager != null) {
            // Remove connection listener
            if (mBarcodeManager != null) {
                mBarcodeManager.removeConnectionListener(this);
                mBarcodeManager = null;
            }

            // Release all the resources
            mEmdkManager.release();
            mEmdkManager = null;
        }
        //   Log.w("Status: ", "EMDK closed unexpectedly! Please close and restart the application.");
    }

    @Override
    public void onData(ScanDataCollection scanDataCollection) {

        if ((scanDataCollection != null) && (scanDataCollection.getResult() == ScannerResults.SUCCESS)) {
            ArrayList<ScanDataCollection.ScanData> scanData = scanDataCollection.getScanData();
            for (ScanDataCollection.ScanData data : scanData) {
                String dataString = data.getData();
                new AsyncDataUpdate().execute(dataString);
            }
        }
    }

    @Override
    public void onStatus(StatusData statusData) {

        StatusData.ScannerStates state = statusData.getState();
        switch (state) {
            case IDLE:
                mStatusString = statusData.getFriendlyName() + " is enabled and idle...";
                new AsyncStatusUpdate().execute(mStatusString);
                if (mBContinuousMode) {
                    try {
                        // An attempt to use the mScanner continuously and rapidly (with a delay < 100 ms between scans)
                        // may cause the mScanner to pause momentarily before resuming the scanning.
                        // Hence add some delay (>= 100ms) before submitting the next read.
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        mScanner.read();
                    } catch (ScannerException e) {
                        mStatusString = e.getMessage();
                        new AsyncStatusUpdate().execute(mStatusString);
                    }
                }

                break;
            case WAITING:
                mStatusString = "Scanner is waiting for trigger press...";
                new AsyncStatusUpdate().execute(mStatusString);
                break;
            case SCANNING:
                mStatusString = "Scanning...";
                new AsyncStatusUpdate().execute(mStatusString);
                break;
            case DISABLED:
                mStatusString = statusData.getFriendlyName() + " is disabled.";
                new AsyncStatusUpdate().execute(mStatusString);
                break;
            case ERROR:
                mStatusString = "An error has occurred.";
                new AsyncStatusUpdate().execute(mStatusString);
                break;
            default:
                break;
        }
    }


    public void enumerateScannerDevices() {

        if (mBarcodeManager != null) {

            List<String> friendlyNameList = new ArrayList<String>();
            int spinnerIndex = 0;

            mDeviceList = mBarcodeManager.getSupportedDevicesInfo();

            if ((mDeviceList != null) && (mDeviceList.size() != 0)) {

                Iterator<ScannerInfo> it = mDeviceList.iterator();
                while (it.hasNext()) {
                    ScannerInfo scnInfo = it.next();
                    friendlyNameList.add(scnInfo.getFriendlyName());
                    if (scnInfo.isDefaultScanner()) {
                        mDefaultIndex = spinnerIndex;
                    }
                    ++spinnerIndex;
                }
            } else {
                Log.w("Status: ", "Failed to get the list of supported mScanner devices! Please close and restart the application.");
            }


        }
    }


    public void setTrigger() {
        if (mScanner == null) {
            initScanner();
        }
        if (mScanner != null) {
            mScanner.triggerType = Scanner.TriggerType.HARD;
        }
    }

    public void setDecoders() {
        if (mScanner == null) {
            initScanner();
        }
        if ((mScanner != null) && (mScanner.isEnabled())) {
            try {
                ScannerConfig config = mScanner.getConfig();
                // Set EAN8
                config.decoderParams.ean8.enabled = true;
                config.decoderParams.ean13.enabled = true;
                config.decoderParams.code39.enabled = true;
                config.decoderParams.code128.enabled = true;
                mScanner.setConfig(config);

            } catch (ScannerException e) {
                Log.w("Status: ", e.getMessage());
            }
        }
    }


    public void initScanner() {
        if (mScanner == null) {

            if ((mDeviceList != null) && (mDeviceList.size() != 0)) {
                mScanner = mBarcodeManager.getDevice(mDeviceList.get(mScannerIndex));
            } else {
                Log.w("Status: ", "Failed to get the specified mScanner device! Please close and restart the application.");
                return;
            }
            if (mScanner != null) {
                mScanner.addDataListener(this);
                mScanner.addStatusListener(this);
                try {
                    mScanner.enable();
                } catch (ScannerException e) {
                }
            } else {
                Log.w("Status: ", "Failed to initialize the mScanner device.");
            }
        }
    }

    public void deInitScanner() {

        if (mScanner != null) {
            try {
                mScanner.cancelRead();
                mScanner.disable();
            } catch (Exception e) {
            }
            try {
                mScanner.removeDataListener(this);
                mScanner.removeStatusListener(this);

            } catch (Exception e) {
            }
            try {
                mScanner.release();
            } catch (Exception e) {
            }
            mScanner = null;
        }
    }

    public class AsyncDataUpdate extends AsyncTask<String, Void, String> {

        @Override
        protected String doInBackground(String... params) {
            return params[0];
        }

        protected void onPostExecute(String result) {
            if (result != null) {
                if (mDataLength++ > 100) { //Clear the cache after 100 scans
                    mDataLength = 0;
                }
//                if (mScannerFragment != null) {
//                    mScannerFragment.callScannerBarcodeService(result);
//                }
                Log.d("maheshdebug","barcode is : "+result);
            }
        }
    }

    public class AsyncStatusUpdate extends AsyncTask<String, Void, String> {

        @Override
        protected String doInBackground(String... params) {
            return params[0];
        }

        @Override
        protected void onPostExecute(String result) {
            //      Log.w("Status: ", result);
        }
    }


    @Override
    public void onCheckedChanged(CompoundButton arg0, boolean arg1) {
        setDecoders();
    }

    @Override
    public void onConnectionChange(ScannerInfo scannerInfo, BarcodeManager.ConnectionState connectionState) {

        String status;
        String scannerName = "";

        String statusExtScanner = connectionState.toString();
        String scannerNameExtScanner = scannerInfo.getFriendlyName();

        if (mDeviceList.size() != 0) {
            scannerName = mDeviceList.get(0).getFriendlyName();
        }

        if (scannerName.equalsIgnoreCase(scannerNameExtScanner)) {

            switch (connectionState) {
                case CONNECTED:
                    deInitScanner();
                    initScanner();
                    setTrigger();
                    setDecoders();
                    break;
                case DISCONNECTED:
                    deInitScanner();

                    break;
            }
            status = scannerNameExtScanner + ":" + statusExtScanner;
            new AsyncStatusUpdate().execute(status);
        } else {
            status = mStatusString + " " + scannerNameExtScanner + ":" + statusExtScanner;
            new AsyncStatusUpdate().execute(status);
        }
    }


    public void getZebraScanner() {
        // The application is in foreground
        // Acquire the barcode manager resources
        if (mEmdkManager != null) {
            mBarcodeManager = (BarcodeManager) mEmdkManager.getInstance(EMDKManager.FEATURE_TYPE.BARCODE);

            // Add connection listener
            if (mBarcodeManager != null) {
                mBarcodeManager.addConnectionListener(this);
            }
            // Enumerate mScanner devices
            enumerateScannerDevices();
            // Initialize mScanner
            initScanner();
            setTrigger();
            setDecoders();
            startScaning();
        }
    }


    public void startScaning() {

        if (mScanner == null) {
            initScanner();
        }
        if (mScanner != null) {
            try {
                if (mScanner.isEnabled()) {
                    // Submit a new read.
                    mScanner.read();

                } else {
                }

            } catch (ScannerException e) {

            }
        }
    }

    public void OnCreateZebra(Activity activity) {
        mDeviceList = new ArrayList<>();
        EMDKResults results = EMDKManager.getEMDKManager(activity, this);
        if (results.statusCode != EMDKResults.STATUS_CODE.SUCCESS) {
            return;
        }
        if (mScanner == null) {
            enumerateScannerDevices();
            initScanner();
            setTrigger();
            setDecoders();
        }
    }

    public void onPauseZebra() {
        // The application is in background
        // De-initialize mScanner
        deInitScanner();
        // Remove connection listener
        if (mBarcodeManager != null) {
            mBarcodeManager.removeConnectionListener(this);
            mBarcodeManager = null;
            mDeviceList = null;
        }
        // Release the barcode manager resources
        if (mEmdkManager != null) {
            mEmdkManager.release(EMDKManager.FEATURE_TYPE.BARCODE);
        }
    }


    public void onDestroyZebra() {
        // De-initialize mScanner
        deInitScanner();
        // Remove connection listener
        if (mBarcodeManager != null) {
            mBarcodeManager.removeConnectionListener(this);
            mBarcodeManager = null;
        }
        // Release all the resources
        if (mEmdkManager != null) {
            mEmdkManager.release();
            mEmdkManager = null;
        }
    }


}

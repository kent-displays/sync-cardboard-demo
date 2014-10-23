/*******************************************************************************
 Copyright © 2014 Kent Displays, Inc.

 Permission is hereby granted, free of charge, to any person obtaining a copy
 of this software and associated documentation files (the "Software"), to deal
 in the Software without restriction, including without limitation the rights
 to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 copies of the Software, and to permit persons to whom the Software is
 furnished to do so, subject to the following conditions:

 The above copyright notice and this permission notice shall be included in
 all copies or substantial portions of the Software.

 THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 THE SOFTWARE.
 ******************************************************************************/

package com.kentdisplays.synccardboarddemo;

import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.improvelectronics.sync.android.SyncFtpListener;
import com.improvelectronics.sync.android.SyncFtpService;
import com.improvelectronics.sync.obex.OBEXFtpFolderListingItem;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public class FileDownloadService extends Service implements SyncFtpListener {

    private static final String TAG = FileDownloadService.class.getSimpleName();
    private SyncFtpService mFtpService;
    private boolean mFtpServiceBound, mConnectedToFtp;

    // Broadcasting.
    private static final String ACTION_BASE = "com.kentdisplays.synccardboarddemo.FileDownloadService.action";
    public static final String SAVED_NEW_FILE = ACTION_BASE + ".SAVED_NEW_FILE";

    @Override
    public void onCreate() {
        super.onCreate();

        mConnectedToFtp = false;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!mFtpServiceBound) {
            bindToFtpService();
        }
        return Service.START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (mFtpServiceBound) {
            // Disconnect from FTP if still connected.
            if(mConnectedToFtp) mFtpService.disconnect();

            // Remove self as a listener for ftp connections.
            mFtpService.removeListener(this);

            unbindService(mFtpConnection);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void bindToFtpService() {
        Intent ftpServiceIntent = new Intent(this, SyncFtpService.class);
        bindService(ftpServiceIntent, mFtpConnection, Context.BIND_AUTO_CREATE);
    }

    private final ServiceConnection mFtpConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName name, IBinder service) {
            // Set up the service.
            mFtpServiceBound = true;
            SyncFtpService.SyncFtpBinder binder = (SyncFtpService.SyncFtpBinder) service;
            mFtpService = binder.getService();
            mFtpService.addListener(FileDownloadService.this);

            // If the ftp service isn't connected then die.
            if (mFtpService.getState() == SyncFtpService.STATE_CONNECTED) {
                startDownload();
            } else {
                stopSelf();
            }
        }

        public void onServiceDisconnected(ComponentName name) {
            mFtpService = null;
            mFtpServiceBound = false;
            mConnectedToFtp = false;

            stopDownload();
        }
    };

    @Override
    public void onFtpDeviceStateChange(int oldState, int newState) {
        if (newState == SyncFtpService.STATE_DISCONNECTED) {
            mConnectedToFtp = false;
            stopSelf();
        }
    }

    @Override
    public void onConnectComplete(int result) {
        if(result == SyncFtpService.RESULT_OK) {
            mConnectedToFtp = true;
            mFtpService.changeFolder("");
        }
    }

    @Override
    public void onDisconnectComplete(int result) {
        mConnectedToFtp = false;
    }

    @Override
    public void onFolderListingComplete(List<OBEXFtpFolderListingItem> items, int result) {
        if (result == SyncFtpService.RESULT_OK) {
            downloadNewestItem(items);
        } else {
            stopDownload();
        }
    }

    @Override
    public void onChangeFolderComplete(Uri uri, int result) {
        if (result == SyncFtpService.RESULT_OK) {
            // Changed to the root folder.
            if (uri.getPath().equals("/")) {
                navigateToSavedFolder();
            }

            // Changed to the SAVED folder.
            else if (uri.getPath().contains("SAVED")) {
                mFtpService.listFolder();
            }
        } else {
            stopDownload();
        }
    }

    @Override
    public void onDeleteComplete(OBEXFtpFolderListingItem file, int result) {

    }

    @Override
    public void onGetFileComplete(OBEXFtpFolderListingItem file, int result) {
        if (result == SyncFtpService.RESULT_OK) {
            saveDownloadedFile(file);
        } else {
            stopDownload();
        }
    }

    private void startDownload() {
        Log.d(TAG, "starting download of file");

        if(!mFtpService.connect()) {
            Log.d(TAG, "connection request was not successful, stopping");
            stopSelf();
        }
    }

    private void navigateToSavedFolder() {
        Log.d(TAG, "Navigating to SAVED folder for download.");
        mFtpService.changeFolder("SAVED");
    }

    private void downloadNewestItem(List<OBEXFtpFolderListingItem> items) {
        if (items == null || items.size() == 0) {
            Log.d(TAG, "Download directory did not contain any files, stopping download.");
            stopDownload();
            return;
        }

        // Check to see if we already have that file.
        OBEXFtpFolderListingItem item = items.get(0);
        if(!Arrays.asList(fileList()).contains(item.getName())) {
            mFtpService.getFile(item);
        } else {
            Log.d(TAG, "Already downloaded file");
            stopDownload();
        }
    }

    private void saveDownloadedFile(OBEXFtpFolderListingItem file) {
        if (file != null && file.getData() != null) {
            try {
                FileOutputStream fos = openFileOutput(file.getName(), Context.MODE_PRIVATE);
                fos.write(file.getData());
                fos.close();
                Log.d(TAG, "Saved new file");
                sendSavedNewFileBroadcast();
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            Log.e(TAG, "Downloaded file does not exist.");
        }

        stopDownload();
    }

    private void stopDownload() {
        Log.d(TAG, "stopping download");
        stopSelf();
    }

    private void sendSavedNewFileBroadcast() {
        Intent intent = new Intent(SAVED_NEW_FILE);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }
}
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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.improvelectronics.sync.android.SyncFtpService;
import com.improvelectronics.sync.android.SyncStreamingService;

public class DownloadReceiver extends BroadcastReceiver {

    private static final String TAG = DownloadReceiver.class.getSimpleName();

    @Override
    public void onReceive(Context context, Intent intent) {
        String action  = intent.getAction();

        if(action == null) return;

        if(action.equals(SyncStreamingService.ACTION_BUTTON_PUSHED)) {
            // If the save button pushed, start up the download service.
            int button = intent.getIntExtra(SyncStreamingService.EXTRA_BUTTON_PUSHED, -1);
            if(button == SyncStreamingService.SAVE_BUTTON) {
                Log.d(TAG, "save button pushed, starting download");

                Intent autoDownloadIntent = new Intent(context, FileDownloadService.class);
                context.startService(autoDownloadIntent);
            }
        } else if(action.equals(SyncFtpService.ACTION_STATE_CHANGED)) {
            int state = intent.getIntExtra(SyncFtpService.EXTRA_STATE, -1);
            int previousState = intent.getIntExtra(SyncFtpService.EXTRA_PREVIOUS_STATE, -1);
            if(state == SyncFtpService.STATE_CONNECTED && previousState == SyncFtpService.STATE_CONNECTING) {
                Log.d(TAG, "connected to ftp, starting download");

                Intent autoDownloadIntent = new Intent(context, FileDownloadService.class);
                context.startService(autoDownloadIntent);
            }
        }
    }
}
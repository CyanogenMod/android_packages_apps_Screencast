/*
 * Copyright (C) 2013 The CyanogenMod Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.cyanogenmod.screencast;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

public class MainActivity extends Activity {

    Button mStartScreencastButton;
    Button mStopScreencastButton;
    Button mSettingsButton;
    TextView mStartDescription;
    View mNoAudioWarning;

    boolean mHasAudioPermission = false;

    private static final int REQUEST_AUDIO_PERMS = 654;

    private boolean hasPermissions() {
        int res = checkCallingOrSelfPermission(Manifest.permission.RECORD_AUDIO);
        return (res == PackageManager.PERMISSION_GRANTED);
    }

    private void requestNecessaryPermissions() {
        String[] permissions = new String[] {
                Manifest.permission.RECORD_AUDIO,
        };
        requestPermissions(permissions, REQUEST_AUDIO_PERMS);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                           int[] grantResults) {
        if (requestCode == REQUEST_AUDIO_PERMS) {
            mHasAudioPermission = true;
            for (int res : grantResults) {
                mHasAudioPermission &= (res == PackageManager.PERMISSION_GRANTED);
            }
            refreshState();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        if (!hasPermissions()) {
            requestNecessaryPermissions();
        } else {
            mHasAudioPermission = true;
        }

        mStartDescription = (TextView) findViewById(R.id.start_description);
        mNoAudioWarning = findViewById(R.id.no_audio_warning);

        mStartScreencastButton = (Button) findViewById(R.id.start_screencast);
        mStartScreencastButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                getSharedPreferences(ScreencastService.PREFS, 0).edit().putBoolean(ScreencastService.KEY_RECORDING, true).apply();
                startService(new Intent("org.cyanogenmod.ACTION_START_SCREENCAST")
                        .setClass(MainActivity.this, ScreencastService.class));
                finish();
            }
        });

        mStopScreencastButton = (Button) findViewById(R.id.stop_screencast);
        mStopScreencastButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                getSharedPreferences(ScreencastService.PREFS, 0).edit().putBoolean(ScreencastService.KEY_RECORDING, false).apply();
                startService(new Intent("org.cyanogenmod.ACTION_STOP_SCREENCAST")
                        .setClass(MainActivity.this, ScreencastService.class));
                refreshState();
            }
        });

        mSettingsButton = (Button) findViewById(R.id.settings);
        mSettingsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                goToSettings();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        mHasAudioPermission = hasPermissions();
        refreshState();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        refreshState();
    }

    private void refreshState() {
        final boolean recording = getSharedPreferences(ScreencastService.PREFS, 0).getBoolean(ScreencastService.KEY_RECORDING, false);
        if (mStartScreencastButton != null) {
            mStartScreencastButton.setEnabled(!recording);
        }
        if (mStopScreencastButton != null) {
            mStopScreencastButton.setEnabled(recording);
        }

        int visibility = mHasAudioPermission ? View.GONE : View.VISIBLE;
        if (mSettingsButton != null) {
            mSettingsButton.setVisibility(visibility);
        }
        if (mNoAudioWarning != null) {
            mNoAudioWarning.setVisibility(visibility);
        }

        if (mStartDescription != null) {
            mStartDescription.setText(
                mHasAudioPermission
                    ? R.string.start_description
                    : R.string.start_description_no_audio
            );
        }
    }

    private void goToSettings() {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.addCategory(Intent.CATEGORY_DEFAULT);
        intent.setData(Uri.parse("package:" + getPackageName()));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
        intent.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
        startActivity(intent);
    }
}
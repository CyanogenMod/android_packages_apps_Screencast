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
import android.animation.Animator;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.ViewAnimationUtils;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.ImageButton;
import android.widget.TextView;

public class MainActivity extends Activity {

    private ImageButton mScreencastButton;
    private TextView mText;
    private TextView mAudioText;
    private CheckBox mChkWithAudio;
    private boolean mHasAudioPermission;
    private boolean mAudioPermissionRequired;
    private static final int REQUEST_AUDIO_PERMS = 654;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        mScreencastButton = (ImageButton) findViewById(R.id.screencast);
        mText = (TextView) findViewById(R.id.hint);
        mAudioText = (TextView) findViewById(R.id.audio_warning);
        mChkWithAudio = (CheckBox) findViewById(R.id.with_audio);

        mScreencastButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                animateFAB(v);
                if (getSharedPreferences(ScreencastService.PREFS, 0)
                        .getBoolean(ScreencastService.KEY_RECORDING, false)) {
                    // stop
                    getSharedPreferences(ScreencastService.PREFS, 0)
                            .edit()
                            .putBoolean(ScreencastService.KEY_RECORDING, false)
                            .apply();
                    startService(new Intent("org.cyanogenmod.ACTION_STOP_SCREENCAST")
                            .setClass(MainActivity.this, ScreencastService.class));
                    refreshState();
                } else {
                    // record
                    getSharedPreferences(ScreencastService.PREFS, 0)
                            .edit()
                            .putBoolean(ScreencastService.KEY_RECORDING, true)
                            .apply();
                    Intent intent = new Intent("org.cyanogenmod.ACTION_START_SCREENCAST");
                    intent.putExtra(ScreencastService.EXTRA_WITHAUDIO, mChkWithAudio.isChecked());
                    startService(intent.setClass(MainActivity.this, ScreencastService.class));
                    finish();
                }
            }
        });

        refreshState();
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

    private boolean hasPermissions() {
        int res = checkSelfPermission(Manifest.permission.RECORD_AUDIO);
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
            for (int res : grantResults) {
                mHasAudioPermission &= (res == PackageManager.PERMISSION_GRANTED);
            }
            refreshState();
        }
        mAudioPermissionRequired = true;
    }

    private void refreshState() {
        final boolean recording = getSharedPreferences(ScreencastService.PREFS, 0)
                .getBoolean(ScreencastService.KEY_RECORDING, false);
        if (recording) {
            mScreencastButton.setImageResource(R.drawable.stop);
            mText.setText(R.string.stop_description);
            mAudioText.setVisibility(View.GONE);
            mChkWithAudio.setVisibility(View.GONE);
        } else {
            mScreencastButton.setImageResource(R.drawable.record);
            mText.setText(R.string.start_description);
            if (!mHasAudioPermission) {
                mChkWithAudio.setChecked(false);
                if (mAudioPermissionRequired &&
                        !shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)) {
                    mAudioText.setText(getResources().getString(R.string.no_audio_setting_warning));
                    mAudioText.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                            intent.addCategory(Intent.CATEGORY_DEFAULT);
                            intent.setData(Uri.parse("package:" + getPackageName()));
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
                            intent.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
                            startActivity(intent);
                        }
                        });
                    mAudioText.setVisibility(View.VISIBLE);
                    mChkWithAudio.setVisibility(View.GONE);
                } else {
                    mAudioText.setVisibility(View.GONE);
                    mChkWithAudio.setOnCheckedChangeListener(new OnCheckedChangeListener() {
                        @Override
                        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                            if (isChecked) {
                                requestNecessaryPermissions();
                            }
                        }
                        });
                    mChkWithAudio.setVisibility(View.VISIBLE);
                }
            } else {
                mChkWithAudio.setChecked(true);
                mChkWithAudio.setVisibility(View.VISIBLE);
                mChkWithAudio.setOnCheckedChangeListener(null);
                mAudioText.setVisibility(View.GONE);
            }
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

    private void animateFAB(View view) {
        int centerX = (view.getLeft() + view.getRight()) / 2;
        int centerY = (view.getTop() + view.getBottom()) / 2;
        int startRadius = 0;
        int endRadius = (int) Math.hypot(view.getWidth(), view.getHeight());

        Animator anim = ViewAnimationUtils.createCircularReveal(
                view, centerX, centerY, startRadius, endRadius);

        anim.setDuration(800);
        anim.start();
    }
}

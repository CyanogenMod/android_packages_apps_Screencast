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
import android.os.Bundle;
import android.view.View;
import android.view.ViewAnimationUtils;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.TextView;

public class MainActivity extends Activity {

    private ImageButton mScreencastButton;
    private TextView mText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        mScreencastButton = (ImageButton) findViewById(R.id.screencast);
        mText = (TextView) findViewById(R.id.hint);

        mScreencastButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                animateFAB(v);
                if (getSharedPreferences(ScreencastService.PREFS, 0).getBoolean(ScreencastService.KEY_RECORDING, false)) {
                    // stop
                    getSharedPreferences(ScreencastService.PREFS, 0).edit().putBoolean(ScreencastService.KEY_RECORDING, false).apply();
                    startService(new Intent("org.cyanogenmod.ACTION_STOP_SCREENCAST")
                            .setClass(MainActivity.this, ScreencastService.class));
                    refreshState();
                } else {
                    // record
                    getSharedPreferences(ScreencastService.PREFS, 0).edit().putBoolean(ScreencastService.KEY_RECORDING, true).apply();
                    startService(new Intent("org.cyanogenmod.ACTION_START_SCREENCAST")
                            .setClass(MainActivity.this, ScreencastService.class));
                    finish();
                }
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshState();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        refreshState();
    }

    private void refreshState() {
        final boolean recording = getSharedPreferences(ScreencastService.PREFS, 0).getBoolean(ScreencastService.KEY_RECORDING, false);
        if (recording) {
            mScreencastButton.setImageResource(R.drawable.stop);
            mText.setText(R.string.stop_description);
        } else {
            mScreencastButton.setImageResource(R.drawable.record);
            mText.setText(R.string.start_description);
        }
    }

    private void animateFAB(View view) {
        int centerX = (view.getLeft() + view.getRight()) / 2;
        int centerY = (view.getTop() + view.getBottom()) / 2;
        int startRadius = 0;
        int endRadius =
            (int) Math.hypot(view.getWidth(), view.getHeight());

        Animator anim = ViewAnimationUtils.createCircularReveal(
            view, centerX, centerY, startRadius, endRadius);

        anim.setDuration(800);
        anim.start();
    }

}

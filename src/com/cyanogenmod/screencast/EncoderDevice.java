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

package com.cyanogenmod.screencast;

import android.content.Context;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.util.Log;
import android.view.Surface;

public abstract class EncoderDevice {
    final String LOGTAG = getClass().getSimpleName();
    private MediaCodec venc;
    int width;
    int height;
    private VirtualDisplay virtualDisplay;

    public VirtualDisplay registerVirtualDisplay(Context context, String name, int width, int height, int densityDpi) {
        assert virtualDisplay == null;
        DisplayManager dm = (DisplayManager)context.getSystemService(Context.DISPLAY_SERVICE);
        Surface surface = createDisplaySurface();
        if (surface == null)
            return null;
        return virtualDisplay = dm.createVirtualDisplay(name, width, height, 1, surface, DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC | DisplayManager.VIRTUAL_DISPLAY_FLAG_SECURE);
    }

    public EncoderDevice(int width, int height) {
        this.width = width;
        this.height = height;
    }

    public void stop() {
        if (venc != null) {
            try {
                venc.signalEndOfInputStream();
            }
            catch (Exception e) {
            }
            venc = null;
        }
        if (virtualDisplay != null) {
            virtualDisplay.release();
            virtualDisplay = null;
        }
    }

    private void destroyDisplaySurface(MediaCodec venc) {
        if (venc == null)
            return;
        // release this surface
        try {
            venc.stop();
            venc.release();
        }
        catch (Exception e) {
        }
        // see if this device is still in use
        if (this.venc != venc)
            return;
        // display is done, kill it
        this.venc = null;

        if (virtualDisplay != null) {
            virtualDisplay.release();
            virtualDisplay = null;
        }
    }

    abstract class EncoderRunnable implements Runnable {
        MediaCodec venc;
        public EncoderRunnable(MediaCodec venc) {
            this.venc = venc;
        }

        abstract void encode() throws Exception;
        protected void cleanup() {
            destroyDisplaySurface(venc);
            venc = null;
        }

        @Override
        final public void run() {
            try {
                encode();
            }
            catch (Exception e) {
                Log.e(LOGTAG, "Encoder error", e);
            }
            cleanup();
            Log.i(LOGTAG, "=======ENCODING COMPELTE=======");
        }
    }

    protected abstract EncoderRunnable onSurfaceCreated(MediaCodec venc);

    public final Surface createDisplaySurface() {
        if (venc != null) {
            // signal any old crap to end
            try {
                venc.signalEndOfInputStream();
            }
            catch (Exception e) {
            }
            venc = null;
        }

        // TODO: choose proper bit rate and width/height
        MediaFormat video = MediaFormat.createVideoFormat("video/avc", width, height);
        int bitrate;
        if (width >= 1080 || height >= 1080)
            bitrate = 4000000;
        else
            bitrate = 2000000;

        bitrate = 6000000;

        video.setInteger(MediaFormat.KEY_BIT_RATE, bitrate);

        video.setInteger(MediaFormat.KEY_FRAME_RATE, 30);
        video.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        video.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 10);

        // create a surface from the encoder
        Log.i(LOGTAG, "Starting encoder");
        venc = MediaCodec.createEncoderByType("video/avc");
        venc.configure(video, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        Surface surface = venc.createInputSurface();
        venc.start();

        EncoderRunnable runnable = onSurfaceCreated(venc);
        new Thread(runnable, "Encoder").start();
        return surface;
    }
}

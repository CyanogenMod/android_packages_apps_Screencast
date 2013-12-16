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
import android.media.CamcorderProfile;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.text.TextUtils;
import android.util.Log;
import android.view.Surface;

import org.xml.sax.Attributes;

import java.io.File;
import java.io.FileInputStream;
import java.io.StringReader;
import java.util.ArrayList;

import safesax.Element;
import safesax.ElementListener;
import safesax.Parsers;
import safesax.RootElement;

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

    private static class VideoEncoderCap {
        int maxFrameWidth;
        int maxFrameHeight;
        int maxBitRate;
        public VideoEncoderCap(Attributes attributes) {
            maxFrameWidth = Integer.valueOf(attributes.getValue("maxFrameWidth"));
            maxFrameHeight = Integer.valueOf(attributes.getValue("maxFrameHeight"));
            maxBitRate = Integer.valueOf(attributes.getValue("maxBitRate"));
        }
    }

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

        int maxWidth;
        int maxHeight;
        int bitrate;

        try {
            File mediaProfiles = new File("/system/etc/media_profiles.xml");
            FileInputStream fin = new FileInputStream(mediaProfiles);
            byte[] bytes = new byte[(int)mediaProfiles.length()];
            fin.read(bytes);
            String xml = new String(bytes);
            RootElement root = new RootElement("MediaSettings");
            Element encoder = root.requireChild("VideoEncoderCap");
            final ArrayList<VideoEncoderCap> encoders = new ArrayList<VideoEncoderCap>();
            encoder.setElementListener(new ElementListener() {
                @Override
                public void end() {
                }

                @Override
                public void start(Attributes attributes) {
                    if (!TextUtils.equals(attributes.getValue("name"), "h264"))
                        return;
                    encoders.add(new VideoEncoderCap(attributes));
                }
            });
            Parsers.parse(new StringReader(xml), root.getContentHandler());
            if (encoders.size() != 1)
                throw new Exception("derp");

            VideoEncoderCap v = encoders.get(0);
            maxWidth = v.maxFrameWidth;
            maxHeight = v.maxFrameHeight;
            bitrate = v.maxBitRate;
        }
        catch (Exception e) {
            CamcorderProfile profile = CamcorderProfile.get(CamcorderProfile.QUALITY_1080P);

            if (profile == null)
                profile = CamcorderProfile.get(CamcorderProfile.QUALITY_720P);

            if (profile == null) {
                maxWidth = 640;
                maxHeight = 480;
                bitrate = 2000000;
            }
            else {
                maxWidth = profile.videoFrameWidth;
                maxHeight = profile.videoFrameHeight;
                bitrate = profile.videoBitRate;
            }
        }

        int max = Math.max(maxWidth, maxHeight);
        int min = Math.min(maxWidth, maxHeight);

        // see if we need to resize
        if (width > height) {
            if (width > max) {
                double ratio = (double)max / (double)width;
                width = max;
                height = (int)(height * ratio);
            }
            if (height > min) {
                double ratio = (double)min / (double)height;
                height = min;
                width = (int)(width * ratio);
            }
        }
        else {
            if (height > max) {
                double ratio = (double)max / (double)height;
                height = max;
                width = (int)(width * ratio);
            }
            if (width > min) {
                double ratio = (double)min / (double)width;
                width = min;
                height = (int)(height * ratio);
            }
        }

        MediaFormat video = MediaFormat.createVideoFormat("video/avc", width, height);

        video.setInteger(MediaFormat.KEY_BIT_RATE, bitrate);

        video.setInteger(MediaFormat.KEY_FRAME_RATE, 30);
        video.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        video.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 3);

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

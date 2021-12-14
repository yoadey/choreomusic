package de.yoadey.choreomusic.utils;


import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import linc.com.amplituda.Amplituda;

/**
 * Helper class to extract the amplitudes of a song if the fast way using Amplituda binary
 * does not work (crashes in some cases).
 */
public class AmplitudesHelper {

    private static final int SAMPLES_PER_FRAME = 1024;

    public static int[] extractAmplitudes(Context context, File localFile) {
        int[] sample;
        Amplituda amplituda = new Amplituda(context);
        try {
            sample = amplituda.processAudio(localFile.getPath())
                    .get().amplitudesAsList().stream().mapToInt(i -> i).toArray();
        } catch (Exception e) {
            Log.w("AmplitudesHelper", "Could not extract waveform data from default method, fallback to other method");
            try {
                sample = extractAmplitudes(localFile);
            } catch (IOException e2) {
                sample = new int[4096];
                Arrays.fill(sample, 1);
            }
        }
        return sample;
    }

    private static int[] extractAmplitudes(File inputFile)
            throws
            java.io.IOException, IllegalArgumentException {

        MediaExtractor extractor = new MediaExtractor();
        MediaFormat format = null;
        int i, j = 0, k = 0;
        int gain = 0, value = 0;

        // Member variables representing frame data
        extractor.setDataSource(inputFile.getPath());
        int numTracks = extractor.getTrackCount();
        // find and select the first audio track present in the file.
        for (i = 0; i < numTracks; i++) {
            format = extractor.getTrackFormat(i);
            if (format.getString(MediaFormat.KEY_MIME).startsWith("audio/")) {
                extractor.selectTrack(i);
                break;
            }
        }
        if (i == numTracks) {
            throw new IllegalArgumentException("No audio track found in " + inputFile);
        }
        int mChannels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
        int mSampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
        // Expected total number of samples per channel.
        int expectedNumSamples =
                (int) ((format.getLong(MediaFormat.KEY_DURATION) / 1000000.f) * mSampleRate + 0.5f);

        MediaCodec codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME));
        codec.configure(format, null, null, 0);
        codec.start();

        int decodedSamplesSize = 0;  // size of the output buffer containing decoded samples.
        byte[] decodedSamples = null;
        int sample_size;
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        long presentation_time;
        boolean done_reading = false;

        // Raw audio data
        boolean firstSampleData = true;
        ByteBuffer mDecodedBytes = ByteBuffer.allocate(decodedSamplesSize);
        List<Integer> mFrameGains = new ArrayList<>(expectedNumSamples);
        do {
            // read data from file and feed it to the decoder input buffers.
            int inputBufferIndex = codec.dequeueInputBuffer(100);
            if (!done_reading && inputBufferIndex >= 0) {
                ByteBuffer inputBuffer = codec.getInputBuffer(inputBufferIndex);
                sample_size = extractor.readSampleData(inputBuffer, 0);
                if (firstSampleData
                        && format.getString(MediaFormat.KEY_MIME).equals("audio/mp4a-latm")
                        && sample_size == 2) {
                    // For some reasons on some devices (e.g. the Samsung S3) you should not
                    // provide the first two bytes of an AAC stream, otherwise the MediaCodec will
                    // crash. These two bytes do not contain music data but basic info on the
                    // stream (e.g. channel configuration and sampling frequency), and skipping them
                    // seems OK with other devices (MediaCodec has already been configured and
                    // already knows these parameters).
                    extractor.advance();
                } else if (sample_size < 0) {
                    // All samples have been read.
                    codec.queueInputBuffer(
                            inputBufferIndex, 0, 0, -1, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                    done_reading = true;
                } else {
                    presentation_time = extractor.getSampleTime();
                    codec.queueInputBuffer(inputBufferIndex, 0, sample_size, presentation_time, 0);
                    extractor.advance();
                }
                firstSampleData = false;
            }

            // Get decoded stream from the decoder output buffer.
            int outputBufferIndex = codec.dequeueOutputBuffer(info, 100);
            if (outputBufferIndex >= 0 && info.size > 0) {
                // Conversion of byte buffer to short buffer
                ByteBuffer outputBuffer = codec.getOutputBuffer(outputBufferIndex);
                if (decodedSamplesSize < info.size) {
                    // We need a bigger buffer for conversion
                    decodedSamplesSize = info.size;
                    decodedSamples = new byte[decodedSamplesSize];
                    mDecodedBytes = ByteBuffer.allocate(decodedSamplesSize);
                }
                outputBuffer.get(decodedSamples, 0, info.size);
                outputBuffer.clear();

                mDecodedBytes.put(decodedSamples, 0, info.size);
                mDecodedBytes.rewind();
                mDecodedBytes.order(ByteOrder.LITTLE_ENDIAN);
                ShortBuffer mDecodedSamples = mDecodedBytes.asShortBuffer();

                // Calculate the amplitudes over multiple buffers with minimal heap
                while (mDecodedSamples.remaining() > 0) {
                    value += Math.abs(mDecodedSamples.get());
                    k++;
                    if (k >= mChannels) {
                        value /= mChannels;
                        if (gain < value) {
                            gain = value;
                        }
                        value = 0;
                        j++;
                        k = 0;
                        if (j >= SAMPLES_PER_FRAME) {
                            mFrameGains.add((int) Math.sqrt(gain));
                            j = 0;
                            gain = -1;
                        }
                    }
                }

                mDecodedBytes.clear();

                codec.releaseOutputBuffer(outputBufferIndex, false);
            }

        } while ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) == 0);
        return mFrameGains.stream().mapToInt(frameGain -> frameGain).toArray();
    }

}

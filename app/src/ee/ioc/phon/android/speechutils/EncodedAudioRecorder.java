/*
 * Copyright 2015, Institute of Cybernetics at Tallinn University of Technology
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

// Based on https://android.googlesource.com/platform/cts/+/jb-mr2-release/tests/tests/media/src/android/media/cts/EncoderTest.java
// Android v4.1 / API 16 / JELLY_BEAN
package ee.ioc.phon.android.speechutils;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.os.Build;
import android.text.TextUtils;
import android.util.Pair;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import ee.ioc.phon.android.speechutils.utils.AudioUtils;

public class EncodedAudioRecorder extends AbstractAudioRecorder {

    private List<byte[]> mBufferList = new ArrayList<>();

    // TODO: ???
    private static final long kTimeoutUs = 10000;

    public EncodedAudioRecorder(int audioSource, int sampleRate) {
        super(audioSource, sampleRate);
    }


    public EncodedAudioRecorder(int sampleRate) {
        this(DEFAULT_AUDIO_SOURCE, sampleRate);
    }


    public EncodedAudioRecorder() {
        this(DEFAULT_AUDIO_SOURCE, DEFAULT_SAMPLE_RATE);
    }

    public String getWsArgs() {
        // TODO: for encoded data we do not need to specify the content type such as "audio/amr-wb"
        //return "?foo=bar";
        return "?content-type=audio/x-raw,+layout=(string)interleaved,+rate=(int)" + getSampleRate() + ",+format=(string)S16LE,+channels=(int)1";
    }

    /**
     * Returns the recorded bytes since the last call, and resets the recording.
     * <p/>
     * TODO: return encoded audio
     *
     * @return bytes that have been recorded since this method was last called
     */
    public synchronized byte[] consumeRecordingAndTruncate() {
        int len = getConsumedLength();
        byte[] bytes = getCurrentRecording(len);
        setRecordedLength(0);
        setConsumedLength(0);
        return bytes;
    }

    @Override
    public synchronized byte[] getEncodedRecording() {
        Log.i("Concatenating encoded buffers: " + mBufferList.size());
        return AudioUtils.concatenateBuffers(mBufferList);
    }

    public List<String> getAvailableEncoders() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            List<String> encoders = new ArrayList<>();
            MediaFormat format = getFormatFlac(getSampleRate());
            MediaCodecList mcl = new MediaCodecList(MediaCodecList.REGULAR_CODECS);
            String encoderAsStr = mcl.findEncoderForFormat(format);
            for (MediaCodecInfo info : mcl.getCodecInfos()) {
                if (info.isEncoder()) {
                    if (info.getName().equals(encoderAsStr)) {
                        encoders.add("*** " + info.getName() + ": " + TextUtils.join(", ", info.getSupportedTypes()));
                    } else {
                        encoders.add(info.getName() + ": " + TextUtils.join(", ", info.getSupportedTypes()));
                    }
                }
            }
            return encoders;
        }
        return Collections.emptyList();
    }

    // TODO: find out why this currently crashes
    public void testFlacEncoder() {
        testEncoderWithFormat("audio/flac", getFormatFlac(getSampleRate()));
    }

    public void testAMRWBEncoder() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            MediaFormat format = new MediaFormat();
            format.setString(MediaFormat.KEY_MIME, "audio/amr-wb");
            format.setInteger(MediaFormat.KEY_SAMPLE_RATE, getSampleRate());
            format.setInteger(MediaFormat.KEY_CHANNEL_COUNT, 1);
            format.setInteger(MediaFormat.KEY_BIT_RATE, 6600);
            testEncoderWithFormat("audio/amr-wb", format);
        }
    }

    public void testAMRWBEncoders() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            LinkedList<MediaFormat> formats = new LinkedList<>();
            final int kBitRates[] =
                    {6600, 8850, 12650, 14250, 15850, 18250, 19850, 23050, 23850};
            for (int j = 0; j < kBitRates.length; ++j) {
                MediaFormat format = new MediaFormat();
                format.setString(MediaFormat.KEY_MIME, "audio/amr-wb");
                format.setInteger(MediaFormat.KEY_SAMPLE_RATE, getSampleRate());
                format.setInteger(MediaFormat.KEY_CHANNEL_COUNT, 1);
                format.setInteger(MediaFormat.KEY_BIT_RATE, kBitRates[j]);
                formats.push(format);
            }
            testEncoderWithFormats("audio/amr-wb", formats);
        }
    }

    void recorderLoop(SpeechRecord speechRecord) {
        mBufferList = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            MediaFormat format = new MediaFormat();
            format.setString(MediaFormat.KEY_MIME, "audio/amr-wb");
            format.setInteger(MediaFormat.KEY_SAMPLE_RATE, getSampleRate());
            format.setInteger(MediaFormat.KEY_CHANNEL_COUNT, 1);
            format.setInteger(MediaFormat.KEY_BIT_RATE, 23050);
            List<String> componentNames = getEncoderNamesForType("audio/amr-wb");
            for (String componentName : componentNames) {
                Log.i("testing component '" + componentName + "'");
                Log.i("  testing format '" + format + "'");
                Pair<Integer, Integer> pair = recorderEncoderLoop(createCodec(componentName, format), speechRecord);
                showMetrics(format, pair.first, pair.second);
                break; // TODO: we use the first one that is suitable
            }
        }
    }

    private static MediaFormat getFormatFlac(int sampleRate) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            MediaFormat format = new MediaFormat();
            format.setString(MediaFormat.KEY_MIME, "audio/flac");
            format.setString(MediaFormat.KEY_FRAME_RATE, null);
            format.setInteger(MediaFormat.KEY_BIT_RATE, 64000);
            format.setInteger(MediaFormat.KEY_SAMPLE_RATE, sampleRate);
            format.setInteger(MediaFormat.KEY_CHANNEL_COUNT, 1);
            return format;
        }
        return null;
    }

    private void testEncoderWithFormat(String mime, MediaFormat format) {
        List<String> componentNames = getEncoderNamesForType(mime);
        for (String componentName : componentNames) {
            Log.i("testing component '" + componentName + "'");
            Log.i("  testing format '" + format + "'");
            Pair<Integer, Integer> pair = recorderEncoderLoop(createCodec(componentName, format), null);
            showMetrics(format, pair.first, pair.second);
            break; // TODO: we use the first one that is suitable
        }
    }

    private void testEncoderWithFormats(String mime, List<MediaFormat> formats) {
        List<String> componentNames = getEncoderNamesForType(mime);
        for (String componentName : componentNames) {
            Log.i("testing component '" + componentName + "'");
            for (MediaFormat format : formats) {
                Log.i("  testing format '" + format + "'");
                Pair<Integer, Integer> pair = recorderEncoderLoop(createCodec(componentName, format), null);
                showMetrics(format, pair.first, pair.second);
            }
        }
    }

    /**
     * Maps the given mime type to a list of names of suitable codecs.
     * Only OMX-codecs are considered.
     */
    private static List<String> getEncoderNamesForType(String mime) {
        LinkedList<String> names = new LinkedList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            int n = MediaCodecList.getCodecCount();
            for (int i = 0; i < n; ++i) {
                MediaCodecInfo info = MediaCodecList.getCodecInfoAt(i);
                if (!info.isEncoder()) {
                    continue;
                }
                if (!info.getName().startsWith("OMX.")) {
                    // Unfortunately for legacy reasons, "AACEncoder", a
                    // non OMX component had to be in this list for the video
                    // editor code to work... but it cannot actually be instantiated
                    // using MediaCodec.
                    Log.i("skipping '" + info.getName() + "'.");
                    continue;
                }
                String[] supportedTypes = info.getSupportedTypes();
                for (int j = 0; j < supportedTypes.length; ++j) {
                    if (supportedTypes[j].equalsIgnoreCase(mime)) {
                        names.push(info.getName());
                        break;
                    }
                }
            }
        }
        // Return an empty list if API is too old
        // TODO: maybe return null or throw exception
        return names;
    }

    /**
     * TODO:
     * The buffer size in RawAudioRecorder is new byte[mFramePeriod * RESOLUTION_IN_BYTES * CHANNELS],
     * but here we use the buffer given by the encoder.
     *
     * @param size
     * @param speechRecord
     * @return
     */
    private byte[] getBytes(int size, SpeechRecord speechRecord) {
        byte[] buffer = new byte[size];
        if (speechRecord != null) {
            // public int read (byte[] audioData, int offsetInBytes, int sizeInBytes)
            int numberOfBytes = speechRecord.read(buffer, 0, buffer.length); // Fill buffer
            int status = 0;

            // Some error checking
            if (numberOfBytes == SpeechRecord.ERROR_INVALID_OPERATION) {
                Log.e("The SpeechRecord object was not properly initialized");
                status = -1;
            } else if (numberOfBytes == SpeechRecord.ERROR_BAD_VALUE) {
                Log.e("The parameters do not resolve to valid data and indexes.");
                status = -2;
            } else if (numberOfBytes > buffer.length) {
                Log.e("Read more bytes than is buffer length:" + numberOfBytes + ": " + buffer.length);
                status = -3;
            } else if (numberOfBytes == 0) {
                Log.e("Read zero bytes");
                status = -4;
            }

            if (status < 0) {
                handleError("status = " + status);
                return null;
            }
            // Everything seems to be OK, adding the buffer to the recording.
            add(buffer);
        }
        return buffer;
    }


    private int queueInputBuffer(MediaCodec codec, ByteBuffer[] inputBuffers, int index, SpeechRecord speechRecord) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            ByteBuffer buffer = inputBuffers[index];
            buffer.clear();
            int size = buffer.limit();
            byte[] bytes = getBytes(size, speechRecord);
            // TODO: improve error handling
            if (bytes == null) {
                return -1;
            }
            buffer.put(bytes);
            codec.queueInputBuffer(index, 0 /* offset */, size, 0 /* timeUs */, 0);
            return size;
        }
        return -1;
    }

    private void dequeueOutputBuffer(MediaCodec codec, ByteBuffer[] outputBuffers, int index, MediaCodec.BufferInfo info) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {

            ByteBuffer buffer = outputBuffers[index];
            final byte[] bufferCopied = new byte[info.size];
            buffer.get(bufferCopied);
            // TODO: do we need to clear?
            //buf.clear();
            // TODO: store the encoded data
            //add(bufferCopied);
            mBufferList.add(bufferCopied);

            codec.releaseOutputBuffer(index, false /* render */);
        }
    }

    private static MediaCodec createCodec(String componentName, MediaFormat format) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            try {
                MediaCodec codec = MediaCodec.createByCodecName(componentName);
                codec.configure(
                        format,
                        null /* surface */,
                        null /* crypto */,
                        MediaCodec.CONFIGURE_FLAG_ENCODE);
                return codec;
            } catch (IllegalStateException e) {
                Log.e("codec '" + componentName + "' failed configuration.");
            } catch (IOException e) {
                Log.e("codec '" + componentName + "' failed configuration.");
            }
        }
        return null;
    }

    /**
     * Synchronous Processing using Buffer Arrays (deprecated)
     */
    private Pair<Integer, Integer> recorderEncoderLoop(MediaCodec codec, SpeechRecord speechRecord) {
        if (codec == null) {
            return new Pair(-1, -1);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            codec.start();
            // Getting some buffers (e.g. 4 of each) to communicate with the codec
            ByteBuffer[] codecInputBuffers = codec.getInputBuffers();
            ByteBuffer[] codecOutputBuffers = codec.getOutputBuffers();
            Log.i("input buffers " + codecInputBuffers.length + "; output buffers: " + codecOutputBuffers.length);
            int numBytesSubmitted = 0;
            boolean doneSubmittingInput = false;
            int numBytesDequeued = 0;
            // TODO: the first disjunct is a temporary hack to allow this method to be called
            // without SpeechRecord
            while (speechRecord == null || speechRecord.getRecordingState() == SpeechRecord.RECORDSTATE_RECORDING) {
                int index;
                if (!doneSubmittingInput) {
                    index = codec.dequeueInputBuffer(kTimeoutUs /* timeoutUs */);
                    if (index != MediaCodec.INFO_TRY_AGAIN_LATER) {
                        int size = queueInputBuffer(codec, codecInputBuffers, index, speechRecord);
                        if (size == -1) {
                            Log.i("Error while reading audio");
                            codec.queueInputBuffer(
                                    index,
                                    0 /* offset */,
                                    0 /* size */,
                                    0 /* timeUs */,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            Log.i("queued input EOS.");
                            doneSubmittingInput = true;
                        } else {
                            numBytesSubmitted += size;
                            //Log.i("queued " + size + " bytes of input data.");
                        }
                    }
                }
                MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
                index = codec.dequeueOutputBuffer(info, kTimeoutUs /* timeoutUs */);
                if (index == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    // TODO
                } else if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    // TODO
                } else if (index == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                    codecOutputBuffers = codec.getOutputBuffers();
                } else {
                    dequeueOutputBuffer(codec, codecOutputBuffers, index, info);
                    numBytesDequeued += info.size;
                    if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        Log.i("dequeued output EOS.");
                        break;
                    }
                    //Log.i("dequeued " + info.size + " bytes of output data.");
                }
            }
            codec.release();
            return new Pair(numBytesSubmitted, numBytesDequeued);
        }
        return new Pair(-1, -1);
    }


    private static void showMetrics(MediaFormat format, int numBytesSubmitted, int numBytesDequeued) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            Log.i("queued a total of " + numBytesSubmitted + " bytes, " + "dequeued " + numBytesDequeued + " bytes.");
            int sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
            int channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
            int inBitrate = sampleRate * channelCount * 16;  // bit/sec
            int outBitrate = format.getInteger(MediaFormat.KEY_BIT_RATE);
            float desiredRatio = (float) outBitrate / (float) inBitrate;
            float actualRatio = (float) numBytesDequeued / (float) numBytesSubmitted;
            Log.i("desiredRatio = " + desiredRatio + ", actualRatio = " + actualRatio);
        }
    }
}

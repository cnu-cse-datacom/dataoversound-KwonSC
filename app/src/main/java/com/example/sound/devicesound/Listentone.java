package com.example.sound.devicesound;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;
import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.transform.*;
import static java.lang.Math.*;
import java.util.ArrayList;


public class Listentone {

    int HANDSHAKE_START_HZ = 4096;
    int HANDSHAKE_END_HZ = 5120 + 1024;

    int START_HZ = 1024;
    int STEP_HZ = 256;
    int BITS = 4;

    int FEC_BYTES = 4;

    private int mAudioSource = MediaRecorder.AudioSource.MIC;
    private int mSampleRate = 44100;
    private int mChannelCount = AudioFormat.CHANNEL_IN_MONO;
    private int mAudioFormat = AudioFormat.ENCODING_PCM_16BIT;
    private float interval = 0.1f;

    private int mBufferSize = AudioRecord.getMinBufferSize(mSampleRate, mChannelCount, mAudioFormat);

    public AudioRecord mAudioRecord = null;
    int audioEncodig;
    boolean startFlag;
    FastFourierTransformer transform;

    public Listentone(){
        transform = new FastFourierTransformer(DftNormalization.STANDARD);
        startFlag = false;
        mAudioRecord = new AudioRecord(mAudioSource, mSampleRate, mChannelCount, mAudioFormat, mBufferSize);
        mAudioRecord.startRecording();
    }

    public void PreRequest() {
        int numframe = findPowerSize((int)(long)Math.round(interval/2*mSampleRate));
        short[] buffer = new short[numframe];
        ArrayList<Double> packet = new ArrayList<>();
        ArrayList<Integer> byte_stream;
        while (true) {
            int bufferedReadResult = mAudioRecord.read(buffer, 0, numframe);
            double[] chunk = new double[numframe];
            if(bufferedReadResult<0){
                continue;
            }
            for(int i = 0; i< numframe; i++){
                chunk[i] = buffer[i];
            }
            double dom = findFrequency(chunk);
            Log.d("Listentone","freq=" + dom);
            if (startFlag && match(dom, HANDSHAKE_END_HZ)) {
                byte_stream = extract_packet(packet);
                StringBuffer data = new StringBuffer();
                for(int i = 0; i< byte_stream.size()-4; i++){
                    data.append((char)(byte_stream.get(i).intValue()));
                }
                Log.d("Listentone", "result= " + data);
                packet.clear();
                startFlag = false;
            }
            else if (startFlag) {
                packet.add(dom);
            }
            else if (match(dom, HANDSHAKE_START_HZ)) {
                startFlag = true;
            }
        }
    }


    private ArrayList<Integer> extract_packet(ArrayList<Double> freqs) {
        ArrayList<Integer> bit_chunks = new ArrayList<>();
        ArrayList<Integer> bit_chunks2 = new ArrayList<>();
        for(int i = 0; i< freqs.size(); i++){
            bit_chunks.add((int)Math.round((freqs.get(i)-START_HZ) / STEP_HZ));
        }
        for(int i = 1; i< bit_chunks.size(); i++){
            int c = bit_chunks.get(i);
            if((c >= 0) && (c < Math.pow(2,BITS))){
                bit_chunks2.add(c);
            }
        }
        return decode_bitchunks(BITS, bit_chunks2);
    }

    private ArrayList<Integer> decode_bitchunks(int chunk_bits, ArrayList<Integer> chunks) {
        ArrayList<Integer> out_bytes = new ArrayList<>();
        int next_read_chunk = 0;
        int next_read_bit = 0;
        int bytes = 0;
        int bits_left = 8;

        while (next_read_chunk < chunks.size()) {
            int can_fill = chunk_bits - next_read_bit;
            int to_fill = min(bits_left, can_fill);
            int offset = chunk_bits - next_read_bit - to_fill;
            bytes <<= to_fill;
            int shifted = chunks.get(next_read_chunk) & (((1<< to_fill)-1) << offset);
            bytes |= shifted >> offset;
            bits_left -= to_fill;
            next_read_bit += to_fill;
            if(bits_left <= 0) {
                out_bytes.add(bytes);
                bytes = 0;
                bits_left = 8;
            }
            if(next_read_bit >= chunk_bits) {
                next_read_chunk += 1;
                next_read_bit -= chunk_bits;
            }
        }
        return out_bytes;
    }


    private double findFrequency(double[] toTransform) {
        int len = toTransform.length;
        double[] real = new double[len];
        double[] img = new double[len];
        double realNum;
        double imgNum;
        double[] mag = new double[len];

        Complex[] complx = transform.transform(toTransform, TransformType.FORWARD);
        Double[] freq = this.fftfreq(complx.length, 1);

        for(int i = 0; i<complx.length; i++) {
            realNum = complx[i].getReal();
            imgNum = complx[i].getImaginary();
            mag[i] = Math.sqrt((realNum * realNum) + (imgNum * imgNum));
        }
        double peak_coeff = 0;
        int temp = 0;
        for(int i = 0; i< complx.length; i++){
            if(peak_coeff< mag[i]){
                peak_coeff = mag[i];
                temp = i;
            }
        }
        Double peak_freq = freq[temp];
        return Math.abs(peak_freq*mSampleRate);

    }

    private boolean match(double freq1, double freq2){
        return Math.abs(freq1-freq2) < 20;
    }

    private int findPowerSize(int num) {
        for (int i = 1; true; i++) {
            int two = (int)Math.pow(2,i);
            if (two >= num) {
                return two;
            }
        }
    }

    private Double[] fftfreq(int n, double d){
        double val = 1.0 / (n * d);
        int[] results = new int[n];
        int N = (n-1) / 2 + 1;

        for(int i = 0; i<N; i++){
            results[i] = i;
        }
        int p2 = -(n/2);
        for(int i = N; i<n; i++){
            results[i] = p2;
            p2++;
        }
        Double[] resultMul = new Double[n];
        for(int i = 0; i<n; i++){
            resultMul[i] = results[i] * val;
        }
        return resultMul;
    }

}


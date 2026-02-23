package art;

import javax.sound.sampled.*;

/**
 * Captures microphone input via javax.sound.sampled and runs FFT
 * to produce smoothed frequency-band values (bass, mids, highs).
 * <p>
 * Audio is read on a daemon thread into a lock-free ring buffer.
 * The main thread calls {@link #update()} once per frame to run
 * FFT and refresh the smoothed outputs.
 */
public class AudioAnalyzer {

    private static final float SAMPLE_RATE = 44100f;
    private static final int FFT_SIZE = 1024;

    private static final float SMOOTHING = 0.82f;
    private static final float BEAT_SENSITIVITY = 1.4f;
    private static final float BEAT_DECAY = 0.98f;
    private static final float BEAT_MIN_ENERGY = 0.005f;

    private final FFT fft;
    private final float[] fftInput;

    private final float[] ring;
    private final int ringMask;
    private volatile int writePos;

    private final int bassBinLo, bassBinHi;
    private final int midsBinLo, midsBinHi;
    private final int highsBinLo, highsBinHi;

    private float bass, mids, highs, amplitude;
    private float bassRunningAvg;
    private boolean beat;

    private TargetDataLine line;
    private Thread captureThread;
    private volatile boolean running;
    private volatile boolean paused;

    public AudioAnalyzer() {
        int ringSize = FFT_SIZE * 4;
        if ((ringSize & (ringSize - 1)) != 0) {
            ringSize = Integer.highestOneBit(ringSize) << 1;
        }
        ring = new float[ringSize];
        ringMask = ringSize - 1;

        fft = new FFT(FFT_SIZE);
        fftInput = new float[FFT_SIZE];

        bassBinLo  = fft.frequencyToBin(20f,    SAMPLE_RATE);
        bassBinHi  = fft.frequencyToBin(250f,   SAMPLE_RATE);
        midsBinLo  = fft.frequencyToBin(250f,   SAMPLE_RATE);
        midsBinHi  = fft.frequencyToBin(2000f,  SAMPLE_RATE);
        highsBinLo = fft.frequencyToBin(2000f,  SAMPLE_RATE);
        highsBinHi = fft.frequencyToBin(16000f, SAMPLE_RATE);
    }

    /**
     * Opens the default microphone and begins capturing audio
     * on a background daemon thread.
     */
    public void start() {
        AudioFormat fmt = new AudioFormat(
                SAMPLE_RATE, 16, 1, true, false);

        DataLine.Info info = new DataLine.Info(TargetDataLine.class, fmt);
        if (!AudioSystem.isLineSupported(info)) {
            System.err.println("[AudioAnalyzer] Microphone line not supported");
            return;
        }

        try {
            line = (TargetDataLine) AudioSystem.getLine(info);
            line.open(fmt, FFT_SIZE * 4);
            line.start();
        } catch (LineUnavailableException e) {
            System.err.println("[AudioAnalyzer] Could not open mic: " + e.getMessage());
            return;
        }

        running = true;
        captureThread = new Thread(this::captureLoop, "AudioCapture");
        captureThread.setDaemon(true);
        captureThread.start();
    }

    /**
     * Stops audio capture and releases the microphone.
     */
    public void stop() {
        running = false;
        if (captureThread != null) {
            try {
                captureThread.join(500);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }
        if (line != null) {
            line.stop();
            line.close();
        }
    }

    /**
     * Call once per frame from {@code draw()}. Copies the latest
     * samples from the ring buffer, runs FFT, extracts band
     * energies, and applies exponential smoothing.
     */
    public void update() {
        if (paused || line == null) return;

        int wp = writePos;
        for (int i = 0; i < FFT_SIZE; i++) {
            fftInput[i] = ring[(wp - FFT_SIZE + i) & ringMask];
        }

        fft.forward(fftInput);

        float rawBass = fft.getBandRMS(bassBinLo, bassBinHi);
        float rawMids = fft.getBandRMS(midsBinLo, midsBinHi);
        float rawHighs = fft.getBandRMS(highsBinLo, highsBinHi);

        float sumSq = 0f;
        for (int i = 0; i < FFT_SIZE; i++) {
            sumSq += fftInput[i] * fftInput[i];
        }
        float rawAmp = (float) Math.sqrt(sumSq / FFT_SIZE);

        float a = 1f - SMOOTHING;
        bass      += a * (rawBass  - bass);
        mids      += a * (rawMids  - mids);
        highs     += a * (rawHighs - highs);
        amplitude += a * (rawAmp   - amplitude);

        bassRunningAvg = bassRunningAvg * BEAT_DECAY + rawBass * (1f - BEAT_DECAY);
        beat = rawBass > bassRunningAvg * BEAT_SENSITIVITY
                && rawBass > BEAT_MIN_ENERGY;
    }

    // ---------------------------------------------------------------
    //  Background capture thread
    // ---------------------------------------------------------------

    private void captureLoop() {
        byte[] buf = new byte[FFT_SIZE * 2]; // 2 bytes per 16-bit sample
        while (running) {
            int bytesRead = line.read(buf, 0, buf.length);
            if (bytesRead <= 0) continue;

            int samples = bytesRead / 2;
            int wp = writePos;
            for (int i = 0; i < samples; i++) {
                int lo = buf[2 * i] & 0xFF;
                int hi = buf[2 * i + 1];
                ring[wp & ringMask] = (short) (lo | (hi << 8)) / 32768f;
                wp = (wp + 1) & ringMask;
            }
            writePos = wp;
        }
    }

    // ---------------------------------------------------------------
    //  Public API
    // ---------------------------------------------------------------

    public float getBass()       { return bass; }
    public float getMids()       { return mids; }
    public float getHighs()      { return highs; }
    public float getAmplitude()  { return amplitude; }
    public boolean isBeat()      { return beat; }

    public void togglePause()    { paused = !paused; }
    public boolean isPaused()    { return paused; }

    public float[] getSpectrum() { return fft.getSpectrum(); }
    public int getFFTSize()      { return FFT_SIZE; }
    public float getSampleRate() { return SAMPLE_RATE; }
}

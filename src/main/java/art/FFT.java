package art;

/**
 * Lightweight radix-2 Cooley-Tukey FFT for real-time frequency analysis.
 * <p>
 * Pre-computes bit-reversal permutation, twiddle factors, and a Hann window
 * at construction time so that per-frame work is minimal. Designed to be
 * called once per audio frame (~1024 samples at 44100 Hz).
 */
public class FFT {

    private final int size;
    private final int halfSize;
    private final int log2Size;

    private final int[] bitReversed;
    private final float[] window;
    private final float[] cos;
    private final float[] sin;

    private final float[] real;
    private final float[] imag;
    private final float[] spectrum;

    public FFT(int size) {
        if (size < 2 || (size & (size - 1)) != 0) {
            throw new IllegalArgumentException("FFT size must be a power of 2, got " + size);
        }

        this.size = size;
        this.halfSize = size / 2;
        this.log2Size = Integer.numberOfTrailingZeros(size);

        this.bitReversed = new int[size];
        this.window = new float[size];
        this.cos = new float[halfSize];
        this.sin = new float[halfSize];

        this.real = new float[size];
        this.imag = new float[size];
        this.spectrum = new float[halfSize];

        buildBitReversalTable();
        buildHannWindow();
        buildTwiddleFactors();
    }

    /**
     * Runs the forward FFT on real-valued time-domain samples.
     * The input array must have exactly {@link #getSize()} elements.
     * After this call, the magnitude spectrum is available via
     * {@link #getSpectrum()}, {@link #getMagnitude(int)}, and
     * {@link #getBandRMS(int, int)}.
     */
    public void forward(float[] timeData) {
        if (timeData.length < size) {
            throw new IllegalArgumentException(
                    "Input length " + timeData.length + " is less than FFT size " + size);
        }

        for (int i = 0; i < size; i++) {
            real[bitReversed[i]] = timeData[i] * window[i];
            imag[bitReversed[i]] = 0f;
        }

        for (int stage = 0; stage < log2Size; stage++) {
            int blockLen = 1 << stage;          // half the butterfly span
            int stride = halfSize >> stage;     // step through twiddle table

            for (int block = 0; block < size; block += blockLen << 1) {
                int twiddleIdx = 0;
                for (int j = 0; j < blockLen; j++) {
                    int even = block + j;
                    int odd = even + blockLen;

                    float tRe = cos[twiddleIdx] * real[odd] - sin[twiddleIdx] * imag[odd];
                    float tIm = cos[twiddleIdx] * imag[odd] + sin[twiddleIdx] * real[odd];

                    real[odd] = real[even] - tRe;
                    imag[odd] = imag[even] - tIm;
                    real[even] += tRe;
                    imag[even] += tIm;

                    twiddleIdx += stride;
                }
            }
        }

        float invSize = 1f / size;
        for (int i = 0; i < halfSize; i++) {
            spectrum[i] = (float) Math.sqrt(real[i] * real[i] + imag[i] * imag[i]) * invSize;
        }
    }

    /**
     * Returns the full magnitude spectrum (bins 0 .. size/2 - 1).
     * Only valid after a call to {@link #forward(float[])}.
     * The returned array is owned by this FFT instance -- do not modify it.
     */
    public float[] getSpectrum() {
        return spectrum;
    }

    /**
     * Returns the magnitude of a single frequency bin.
     */
    public float getMagnitude(int bin) {
        return spectrum[bin];
    }

    /**
     * Computes the RMS energy across a contiguous range of bins
     * (inclusive on both ends). Useful for measuring energy in a
     * frequency band (bass, mids, highs).
     */
    public float getBandRMS(int lowBin, int highBin) {
        lowBin = Math.max(0, lowBin);
        highBin = Math.min(halfSize - 1, highBin);
        if (lowBin > highBin) return 0f;

        float sum = 0f;
        for (int i = lowBin; i <= highBin; i++) {
            sum += spectrum[i] * spectrum[i];
        }
        return (float) Math.sqrt(sum / (highBin - lowBin + 1));
    }

    /**
     * Maps a frequency in Hz to the nearest FFT bin index.
     *
     * @param freqHz     the target frequency
     * @param sampleRate the audio sample rate (e.g. 44100)
     * @return bin index (clamped to valid range)
     */
    public int frequencyToBin(float freqHz, float sampleRate) {
        int bin = Math.round(freqHz * size / sampleRate);
        return Math.max(0, Math.min(halfSize - 1, bin));
    }

    public int getSize() {
        return size;
    }

    public int getHalfSize() {
        return halfSize;
    }

    // ---------------------------------------------------------------
    //  Pre-computation done once at construction
    // ---------------------------------------------------------------

    private void buildBitReversalTable() {
        for (int i = 0; i < size; i++) {
            int reversed = 0;
            int value = i;
            for (int bit = 0; bit < log2Size; bit++) {
                reversed = (reversed << 1) | (value & 1);
                value >>= 1;
            }
            bitReversed[i] = reversed;
        }
    }

    private void buildHannWindow() {
        double twoPiOverN = 2.0 * Math.PI / (size - 1);
        for (int i = 0; i < size; i++) {
            window[i] = (float) (0.5 * (1.0 - Math.cos(twoPiOverN * i)));
        }
    }

    private void buildTwiddleFactors() {
        for (int i = 0; i < halfSize; i++) {
            double angle = -2.0 * Math.PI * i / size;
            cos[i] = (float) Math.cos(angle);
            sin[i] = (float) Math.sin(angle);
        }
    }
}

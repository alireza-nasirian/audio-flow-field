package art;

import processing.core.PApplet;
import processing.core.PVector;

/**
 * 2D grid of angle vectors computed from layered Perlin noise,
 * continuously modulated by audio-reactive parameters.
 * <p>
 * Each cell stores a single float angle. Particles look up their grid cell
 * to obtain a steering direction. Audio bands smoothly drive the field's
 * noise scale (turbulence), time evolution speed, bass warp, and
 * treble rotation offset.
 */
public class FlowField {

    private final PApplet app;
    private final int cols;
    private final int rows;
    private final int resolution;
    private final float[][] angles;

    private static final float BASE_NOISE_SCALE = 0.06f;
    private static final float BASE_TIME_SPEED  = 0.004f;
    private static final float ANGLE_RANGE      = PApplet.TWO_PI * 2f;

    private static final float AMP_SCALE_GAIN  = 0.12f;
    private static final float BASS_WARP_GAIN  = 3.5f;
    private static final float MIDS_SPEED_GAIN = 0.018f;
    private static final float HIGHS_ROT_GAIN  = PApplet.PI * 0.5f;

    private static final float PARAM_LERP = 0.12f;

    private float noiseScale     = BASE_NOISE_SCALE;
    private float timeSpeed      = BASE_TIME_SPEED;
    private float warpIntensity  = 0f;
    private float rotationOffset = 0f;
    private float timeOffset     = 0f;

    private float targetNoiseScale = BASE_NOISE_SCALE;
    private float targetTimeSpeed  = BASE_TIME_SPEED;
    private float targetWarp       = 0f;
    private float targetRotation   = 0f;

    public FlowField(PApplet app, int resolution) {
        this.app = app;
        this.resolution = resolution;
        this.cols = app.width / resolution + 1;
        this.rows = app.height / resolution + 1;
        this.angles = new float[cols][rows];
    }

    /**
     * Recomputes the entire angle grid for the current frame.
     * Audio band values drive the field parameters; all parameters
     * are exponentially smoothed so visuals flow rather than jitter.
     *
     * @param amplitude overall signal amplitude  → noise scale (turbulence)
     * @param bass      low-frequency energy      → warp intensity
     * @param mids      mid-frequency energy      → time evolution speed
     * @param highs     high-frequency energy     → rotation offset
     */
    public void update(float amplitude, float bass, float mids, float highs) {
        targetNoiseScale = BASE_NOISE_SCALE + amplitude * AMP_SCALE_GAIN;
        targetTimeSpeed  = BASE_TIME_SPEED  + mids * MIDS_SPEED_GAIN;
        targetWarp       = bass * BASS_WARP_GAIN;
        targetRotation   = highs * HIGHS_ROT_GAIN;

        noiseScale     = PApplet.lerp(noiseScale,     targetNoiseScale, PARAM_LERP);
        timeSpeed      = PApplet.lerp(timeSpeed,      targetTimeSpeed,  PARAM_LERP);
        warpIntensity  = PApplet.lerp(warpIntensity,  targetWarp,       PARAM_LERP);
        rotationOffset = PApplet.lerp(rotationOffset, targetRotation,   PARAM_LERP);

        timeOffset += timeSpeed;

        for (int col = 0; col < cols; col++) {
            for (int row = 0; row < rows; row++) {
                float angle = app.noise(
                        col * noiseScale,
                        row * noiseScale,
                        timeOffset) * ANGLE_RANGE;

                if (warpIntensity > 0.001f) {
                    float warpNoise = app.noise(
                            col * noiseScale * 0.5f + 300f,
                            row * noiseScale * 0.5f + 300f,
                            timeOffset * 0.6f);
                    angle += (warpNoise - 0.5f) * warpIntensity;
                }

                angle += rotationOffset;
                angles[col][row] = angle;
            }
        }
    }

    /**
     * Returns the flow angle at the given pixel coordinates.
     */
    public float getAngle(float x, float y) {
        int col = PApplet.constrain((int) (x / resolution), 0, cols - 1);
        int row = PApplet.constrain((int) (y / resolution), 0, rows - 1);
        return angles[col][row];
    }

    /**
     * Returns a unit-length direction vector at the given pixel coordinates.
     */
    public PVector getForce(float x, float y) {
        return PVector.fromAngle(getAngle(x, y));
    }

    public float getTimeSpeed()      { return timeSpeed; }
    public float getWarpIntensity()  { return warpIntensity; }
    public int   getCols()           { return cols; }
    public int   getRows()           { return rows; }
    public int   getResolution()     { return resolution; }
}

package art;

import processing.core.PApplet;

/**
 * Audio-reactive flow field: particles steered by Perlin noise,
 * modulated in real time by microphone frequency bands.
 * <p>
 * Bass warps the field, mids drive speed and warmth, treble spawns bursts.
 * Color palette sweeps blue→purple→red→orange as energy rises.
 */
public class AudioFlowField extends PApplet {

    private static final int PARTICLE_COUNT = 4000;
    private static final int FIELD_RESOLUTION = 20;
    private static final int TRAIL_ALPHA = 18;
    private static final int BEAT_BURST = 80;

    private static final float AMP_SCALE  = 12f;
    private static final float BASS_SCALE = 18f;
    private static final float MIDS_SCALE = 20f;
    private static final float HIGH_SCALE = 25f;

    private static final float BASE_SPEED    = 0.8f;
    private static final float MAX_SPEED_ADD = 3.0f;

    private AudioAnalyzer audio;
    private FlowField field;
    private Particle[] particles;

    private boolean showBass  = true;
    private boolean showMids  = true;
    private boolean showHighs = true;

    private float paletteShift = 0f;

    public static void main(String[] args) {
        PApplet.main(AudioFlowField.class);
    }

    @Override
    public void settings() {
        size(1200, 900);
    }

    @Override
    public void setup() {
        colorMode(HSB, 360, 100, 100, 255);
        background(0);

        audio = new AudioAnalyzer();
        audio.start();

        field = new FlowField(this, FIELD_RESOLUTION);

        particles = new Particle[PARTICLE_COUNT];
        for (int i = 0; i < PARTICLE_COUNT; i++) {
            particles[i] = new Particle(this);
        }

        println("Audio-Reactive Flow Field");
        println("  S = save frame   R = reset particles   SPACE = pause audio");
        println("  1 = toggle bass  2 = toggle mids       3 = toggle highs");
    }

    @Override
    public void draw() {
        audio.update();

        float amp   = constrain(audio.getAmplitude() * AMP_SCALE,  0, 1);
        float bass  = showBass  ? constrain(audio.getBass()  * BASS_SCALE, 0, 1) : 0;
        float mids  = showMids  ? constrain(audio.getMids()  * MIDS_SCALE, 0, 1) : 0;
        float highs = showHighs ? constrain(audio.getHighs() * HIGH_SCALE, 0, 1) : 0;
        boolean beat = audio.isBeat();

        field.update(amp, bass, mids, highs);

        // Trail fade: semi-transparent black rectangle over previous frame
        colorMode(RGB, 255);
        noStroke();
        fill(0, TRAIL_ALPHA);
        rect(0, 0, width, height);
        colorMode(HSB, 360, 100, 100, 255);

        if (beat) {
            paletteShift += random(15, 40);
        }
        paletteShift *= 0.995f;

        float speedMult = BASE_SPEED + mids * MAX_SPEED_ADD;
        float energy = (amp + mids + bass) / 3f;

        for (Particle p : particles) {
            p.col = particleColor(p, energy);

            float angle = field.getAngle(p.pos.x, p.pos.y);
            p.follow(angle, speedMult, bass);
            p.update();

            if (p.isDead()) {
                p.respawn();
            } else {
                p.wrapEdges();
            }

            p.display();
        }

        if (beat) {
            int burst = min(BEAT_BURST, PARTICLE_COUNT);
            for (int i = 0; i < burst; i++) {
                particles[(int) random(PARTICLE_COUNT)].respawn();
            }
        }

        drawHUD(amp, bass, mids, highs);
    }

    /**
     * Hue sweeps 240 (blue) → 300 (purple) → 360/0 (red) → 20 (orange)
     * as energy rises. Spatial noise adds local variation so nearby
     * particles shimmer rather than all sharing the same exact hue.
     */
    private int particleColor(Particle p, float energy) {
        float hue = 240f + energy * 140f + paletteShift;

        float spatial = noise(p.pos.x * 0.005f, p.pos.y * 0.005f,
                              frameCount * 0.003f);
        hue += spatial * 30f - 15f;
        hue = ((hue % 360f) + 360f) % 360f;

        float sat = lerp(55, 95, energy);
        float bri = lerp(55, 100, energy * 0.5f + spatial * 0.5f);

        return color(hue, sat, bri);
    }

    private void drawHUD(float amp, float bass, float mids, float highs) {
        colorMode(RGB, 255);
        fill(255, 100);
        noStroke();
        textSize(11);
        textAlign(LEFT, TOP);

        String info = String.format("FPS %.0f  AMP %.2f  BASS %.2f  MIDS %.2f  HIGHS %.2f",
                                    frameRate, amp, bass, mids, highs);
        if (audio.isPaused()) info += "  [PAUSED]";
        text(info, 10, 10);

        colorMode(HSB, 360, 100, 100, 255);
    }

    @Override
    public void keyPressed() {
        switch (key) {
            case 's': case 'S':
                saveFrame("audio-flow-field-######.png");
                println("Frame saved.");
                break;
            case 'r': case 'R':
                for (Particle p : particles) p.respawn();
                println("Particles reset.");
                break;
            case ' ':
                audio.togglePause();
                println(audio.isPaused() ? "Audio paused." : "Audio resumed.");
                break;
            case '1':
                showBass = !showBass;
                println("Bass " + (showBass ? "ON" : "OFF"));
                break;
            case '2':
                showMids = !showMids;
                println("Mids " + (showMids ? "ON" : "OFF"));
                break;
            case '3':
                showHighs = !showHighs;
                println("Highs " + (showHighs ? "ON" : "OFF"));
                break;
            default:
                break;
        }
    }

    @Override
    public void dispose() {
        if (audio != null) {
            audio.stop();
        }
        super.dispose();
    }
}

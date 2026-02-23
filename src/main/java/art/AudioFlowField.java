package art;

import processing.core.PApplet;

/**
 * Audio-reactive flow field: particles steered by Perlin noise,
 * modulated in real time by microphone frequency bands.
 */
public class AudioFlowField extends PApplet {

    public static void main(String[] args) {
        PApplet.main(AudioFlowField.class);
    }

    @Override
    public void settings() {
        size(1200, 900);
    }

    @Override
    public void setup() {
        // TODO: initialize AudioAnalyzer, FlowField, Particle pool
    }

    @Override
    public void draw() {
        // TODO: update audio, flow field, particles; render trails
    }

    @Override
    public void keyPressed() {
        // TODO: S = save, R = reset, SPACE = pause, 1-3 = toggle bands
    }
}

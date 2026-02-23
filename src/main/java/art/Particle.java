package art;

import processing.core.PApplet;
import processing.core.PVector;

public class Particle {

    private final PApplet p;

    PVector pos;
    PVector prevPos;
    PVector vel;

    float lifespan;
    float maxLifespan;
    int col;
    float strokeW;

    private float maxSpeed = 3f;

    public Particle(PApplet p) {
        this.p = p;
        vel = new PVector(0, 0);
        respawn();
    }

    /**
     * Reset the particle to a random position with fresh lifespan.
     * Called on construction and whenever the particle dies or leaves bounds.
     */
    public void respawn() {
        pos = new PVector(p.random(p.width), p.random(p.height));
        prevPos = pos.copy();
        vel.set(0, 0);
        maxLifespan = p.random(120, 350);
        lifespan = maxLifespan;
        strokeW = 1f;
    }

    /**
     * Steer toward the flow field angle at the particle's current position,
     * then integrate velocity into position.
     *
     * @param fieldAngle the desired angle (radians) from the FlowField at this particle's location
     * @param speedMult  multiplier from audio mids (1.0 = normal)
     * @param bassMult   multiplier from audio bass (affects stroke weight)
     */
    public void follow(float fieldAngle, float speedMult, float bassMult) {
        PVector desired = PVector.fromAngle(fieldAngle);
        desired.mult(maxSpeed * speedMult);

        PVector steer = PVector.sub(desired, vel);
        float maxForce = 0.4f;
        steer.limit(maxForce);

        vel.add(steer);
        vel.limit(maxSpeed * speedMult);

        strokeW = PApplet.lerp(0.6f, 3.5f, bassMult);
    }

    public void update() {
        prevPos.set(pos);
        pos.add(vel);
        lifespan--;
    }

    public boolean isDead() {
        return lifespan <= 0;
    }

    public boolean isOutOfBounds() {
        return pos.x < 0 || pos.x > p.width || pos.y < 0 || pos.y > p.height;
    }

    /**
     * Draw a line segment from previous position to current position.
     * Alpha fades as the particle ages.
     */
    public void display() {
        float ageFraction = lifespan / maxLifespan;
        float alpha = PApplet.map(ageFraction, 0, 1, 0, 200);
        // Fade in briefly at birth and fade out near death
        if (ageFraction > 0.9f) {
            alpha *= PApplet.map(ageFraction, 0.9f, 1f, 1f, 0f);
        }

        p.stroke(col, alpha);
        p.strokeWeight(strokeW);
        p.line(prevPos.x, prevPos.y, pos.x, pos.y);
    }

    /**
     * Wrap the particle's position around edges so it re-enters from the
     * opposite side. Updates prevPos to avoid drawing a line across the canvas.
     */
    public void wrapEdges() {
        boolean wrapped = false;

        if (pos.x < 0) { pos.x = p.width; wrapped = true; }
        else if (pos.x > p.width) { pos.x = 0; wrapped = true; }

        if (pos.y < 0) { pos.y = p.height; wrapped = true; }
        else if (pos.y > p.height) { pos.y = 0; wrapped = true; }

        if (wrapped) {
            prevPos.set(pos);
        }
    }
}

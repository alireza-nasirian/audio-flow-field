# Technical Report: Audio-Reactive Flow Field

**Technology:** Java 21, Processing 4.5.0, Maven

---

## 1. Introduction

This project is a real-time generative artwork in which 4,000 particles navigate an invisible vector field derived from Perlin noise. The field is continuously reshaped by live microphone audio: bass frequencies warp the field geometry, mid frequencies drive particle speed and color warmth, high frequencies rotate the field, and detected beats trigger particle bursts and palette shifts. The result is a visual system where sound becomes the sculptor of form, color, and motion.

The core artistic premise is that audio should not merely change surface-level properties like color. Instead, it should reshape the **physics** of the entire system — the turbulence, curvature, density, and dynamics of the particles — so that the visuals feel genuinely *driven by* the sound rather than simply reacting to it.

---

## 2. System Architecture

The application follows a pipeline architecture with four main components:

```
Microphone
    │
    ▼
AudioAnalyzer ──── FFT (frequency decomposition)
    │
    │  outputs: amplitude, bass, mids, highs, beat
    │
    ├──────────────────┐
    ▼                  ▼
FlowField          Particle[] (4000 instances)
(angle grid)       (position, velocity, lifespan, color)
    │                  │
    └──► getAngle() ◄──┘
              │
              ▼
         PApplet.draw()  (trail-buffer rendering, HUD)
```

Each component is implemented as a separate Java class under the `art` package:

| Class               | Responsibility                                            |
|---------------------|-----------------------------------------------------------|
| `AudioFlowField`    | Main Processing sketch — orchestrates the pipeline        |
| `AudioAnalyzer`     | Microphone capture, FFT, frequency band extraction        |
| `FFT`               | Custom radix-2 Cooley–Tukey FFT implementation            |
| `FlowField`         | 2D grid of angle vectors derived from layered Perlin noise|
| `Particle`          | Individual particle with position, velocity, and lifespan |

---

## 3. Audio Capture and Frequency Analysis

### 3.1 Microphone Capture

Audio is captured using `javax.sound.sampled.TargetDataLine`, the JDK's built-in audio API. No external audio libraries are required. The microphone is opened at **44,100 Hz**, 16-bit, mono, little-endian signed PCM:

```java
AudioFormat fmt = new AudioFormat(44100f, 16, 1, true, false);
```

A **daemon thread** continuously reads raw bytes from the microphone and converts them to normalized floating-point samples (range [-1, 1]) in a lock-free ring buffer:

```java
ring[wp & ringMask] = (short)(lo | (hi << 8)) / 32768f;
```

The ring buffer size is 4,096 samples (a power of two for efficient masking). The main thread reads from this buffer every frame without any locking — the single `volatile int writePos` variable provides sufficient synchronization for a single-producer/single-consumer scenario.

### 3.2 FFT Implementation

The `FFT` class implements a **radix-2 Cooley–Tukey** Fast Fourier Transform from scratch. At construction time, it pre-computes three tables to minimize per-frame work:

1. **Bit-reversal permutation table** — reorders input samples for the butterfly operations.
2. **Hann window** — `w[i] = 0.5 × (1 − cos(2πi / (N−1)))` — applied to the time-domain signal to reduce spectral leakage at buffer boundaries.
3. **Twiddle factors** — pre-computed `cos` and `sin` values for the butterfly multiplications.

The FFT size is **1,024 samples**, yielding 512 frequency bins with a resolution of approximately 43 Hz per bin (44100 / 1024).

### 3.3 Frequency Band Extraction

After the FFT, the magnitude spectrum is split into three bands using RMS (root-mean-square) energy:

| Band     | Frequency Range | FFT Bins (approx.) |
|----------|----------------|---------------------|
| **Bass** | 20 – 250 Hz   | 0 – 5               |
| **Mids** | 250 – 2,000 Hz| 6 – 46              |
| **Highs**| 2,000 – 16,000 Hz| 46 – 371          |

The RMS for a band [lo, hi] is computed as:

$$E = \sqrt{\frac{1}{n}\sum_{i=lo}^{hi} |X_i|^2}$$

All four outputs (amplitude, bass, mids, highs) are passed through **exponential smoothing** with factor α = 0.18 (i.e., `1 − 0.82`) to prevent jittery visuals:

$$y_t = y_{t-1} + \alpha \cdot (x_t - y_{t-1})$$

### 3.4 Beat Detection

Beat detection uses a running-average comparator on the bass band. A beat is triggered when the instantaneous bass energy exceeds the running average by a factor of 1.4×, and is above a minimum energy threshold:

```java
beat = rawBass > bassRunningAvg * 1.4f && rawBass > 0.005f;
```

The running average decays at 0.98 per frame, allowing it to adapt to the overall loudness level of the audio.

---

## 4. Flow Field

### 4.1 Perlin Noise Grid

The canvas (1200×900 pixels) is divided into a grid with a resolution of 20 pixels per cell, yielding a grid of 61 columns × 46 rows. Each cell stores a single floating-point **angle** (in radians) that represents the flow direction at that location.

Angles are computed from Processing's built-in 3D Perlin noise function:

```java
angle = noise(col × noiseScale, row × noiseScale, timeOffset) × 4π
```

The third noise dimension (`timeOffset`) advances each frame, causing the field to evolve smoothly over time. The output is scaled to [0, 4π] to allow full directional coverage including wrap-around.

### 4.2 Audio Modulation of the Field

Each of the four audio outputs drives a distinct field parameter:

| Audio Signal   | Field Parameter    | Effect on Field                            |
|----------------|--------------------|--------------------------------------------|
| **Amplitude**  | `noiseScale`       | Higher amplitude → larger scale → tighter, more turbulent swirls |
| **Bass**       | `warpIntensity`    | A second layer of Perlin noise is added to the angle, displacing particle paths |
| **Mids**       | `timeSpeed`        | Field evolves faster in time, making patterns shift more rapidly  |
| **Highs**      | `rotationOffset`   | A constant angle offset is added to all cells, rotating the entire field  |

All parameters are **exponentially smoothed** toward their target values with a lerp factor of 0.12 per frame:

```java
noiseScale = lerp(noiseScale, targetNoiseScale, 0.12);
```

This smoothing is critical: it ensures that sudden audio spikes produce flowing visual transitions rather than abrupt jumps.

**Bass warp** deserves special attention. When bass energy is present, a second Perlin noise lookup at half the frequency and with a phase offset of 300 units is computed. This second noise value is scaled by the warp intensity and added to the base angle:

```java
float warpNoise = noise(col × noiseScale × 0.5 + 300, row × noiseScale × 0.5 + 300, timeOffset × 0.6);
angle += (warpNoise − 0.5) × warpIntensity;
```

The result is that bass creates **large-scale flowing distortions** in the field — a visual analog of the physical sensation of bass vibrations.

---

## 5. Particle System

### 5.1 Particle Properties

Each of the 4,000 particles maintains:

- **Position** (`PVector pos`) — current location on the canvas
- **Previous position** (`PVector prevPos`) — for trail line drawing
- **Velocity** (`PVector vel`) — current movement vector
- **Lifespan** — frames remaining before death; randomly initialized between 120 and 350 frames
- **Stroke weight** — line thickness, driven by bass
- **Color** — computed per frame based on energy and spatial noise

### 5.2 Steering (Reynolds-Style)

Each frame, a particle queries the flow field for the angle at its current position. It then computes a steering force using a simplified Craig Reynolds flocking model:

```java
PVector desired = PVector.fromAngle(fieldAngle).mult(maxSpeed × speedMult);
PVector steer   = PVector.sub(desired, vel);
steer.limit(0.4);              // max steering force
vel.add(steer);
vel.limit(maxSpeed × speedMult);
```

The `speedMult` is driven by mids: base speed of 0.8 plus up to 3.0 additional when mids are maximal. This means particles don't instantly snap to the field direction — they gradually curve toward it, producing the smooth, organic trajectories visible on screen.

### 5.3 Lifecycle and Edge Wrapping

- When a particle's lifespan reaches zero, it **respawns** at a random location with a fresh lifespan.
- If a particle exits one edge of the canvas, it **wraps** to the opposite edge. The previous position is reset to prevent a line being drawn across the entire canvas.
- On beat detection, **80 random particles** are forcibly respawned, creating a visible burst of new trajectories.

### 5.4 Display

Each particle is rendered as a **line segment** from its previous position to its current position:

```java
line(prevPos.x, prevPos.y, pos.x, pos.y);
```

Alpha fades with age: full alpha mid-life, fading in briefly at birth and fading out near death. This avoids abrupt visual pops when particles appear or disappear.

---

## 6. Color System

The color model uses **HSB** (Hue, Saturation, Brightness) with ranges H:[0,360], S:[0,100], B:[0,100].

### 6.1 Energy-Driven Hue

The base hue sweeps from **240 (blue)** through **purple** and **red** to **380/20 (orange)** as total energy increases:

$$hue = 240 + energy \times 140 + paletteShift$$

where `energy = (amplitude + mids + bass) / 3`.

### 6.2 Beat-Triggered Palette Shift

When a beat is detected, `paletteShift` jumps by a random value between 15 and 40. It then decays multiplicatively at 0.995× per frame. This causes the entire color palette to suddenly shift on each beat and then gradually relax back.

### 6.3 Spatial Shimmer

A per-particle spatial noise term adds ±15 hue units based on the particle's (x, y) position and the current frame count:

```java
float spatial = noise(pos.x × 0.005, pos.y × 0.005, frameCount × 0.003);
hue += spatial × 30 − 15;
```

This prevents nearby particles from sharing the exact same color, creating a shimmering, iridescent quality across the canvas.

### 6.4 Saturation and Brightness

Both saturation and brightness increase with energy, ensuring that loud passages produce vivid, bright colors while quiet passages remain muted and subdued:

$$sat = \text{lerp}(55, 95, energy)$$
$$bri = \text{lerp}(55, 100, energy \times 0.5 + spatial \times 0.5)$$

---

## 7. Trail Rendering

The trail effect is achieved through a **semi-transparent overlay** technique rather than storing particle history. Each frame, before drawing particles, a black rectangle with alpha = 18 is drawn over the entire canvas:

```java
fill(0, 18);
rect(0, 0, width, height);
```

This means older particle positions are never explicitly erased — they simply fade over approximately 50–60 frames (255 / 18 ≈ 14 full passes, but the fade is non-linear due to blending). The result is a glowing phosphor-trail aesthetic with zero memory cost for trail storage.

---

## 8. Threading Model

The application uses two threads:

1. **Main thread** (Processing's animation thread) — runs `draw()` at ~60 FPS, reads the ring buffer, runs FFT, updates the flow field, and renders all particles.
2. **Capture thread** (daemon) — blocks on `TargetDataLine.read()`, converts bytes to floats, and writes to the ring buffer.

Synchronization is achieved through a single `volatile int writePos` variable. This is safe because:
- There is exactly one writer (capture thread) and one reader (main thread).
- The ring buffer is large enough (4,096 samples ≈ 93 ms of audio) that the reader never catches the writer at 60 FPS frame intervals (~16.7 ms).
- Java's `volatile` guarantees visibility of writes across threads.

---

## 9. Performance Considerations

| Aspect | Choice | Rationale |
|--------|--------|-----------|
| FFT pre-computation | Bit-reversal, window, twiddle tables built once | Reduces per-frame FFT to pure arithmetic |
| Grid resolution | 20 px/cell (61×46 = 2,806 cells) | Fine enough for smooth steering, coarse enough for fast recomputation |
| Particle count | 4,000 | Balanced between visual density and frame rate |
| Trail rendering | Overlay rectangle | O(1) memory vs. O(n × history) for stored trails |
| Ring buffer | Power-of-two size with bitmask indexing | Avoids modulo operations in the hot audio capture loop |
| Smoothing | Single-pole IIR filter (exponential) | One multiply-add per parameter per frame |

---

## 10. Dependencies and Build

The project uses **Maven** for dependency management. The only external dependency is Processing Core:

```xml
<dependency>
    <groupId>org.processing</groupId>
    <artifactId>core</artifactId>
    <version>4.5.0</version>
</dependency>
```

Audio capture uses `javax.sound.sampled`, which is part of the JDK, no additional audio libraries are needed. The FFT is implemented from scratch in the `FFT` class.

To build and run:

```bash
cd audio-flow-field
mvn compile exec:java
```

---

## 11. Conclusion

This project demonstrates how algorithmic art can bridge the gap between auditory and visual experience. By decomposing sound into frequency bands and mapping them onto the physical parameters of a noise-driven particle system, the artwork creates a form of computational synesthesia, an immersive, never-repeating visual performance shaped entirely by the sonic environment. Every aspect of the implementation, from the custom FFT to the lock-free audio pipeline to the exponential smoothing, serves the goal of making the connection between sound and image feel immediate, organic, and alive.

# Audio-Reactive Flow Field (Java + Processing)

A real-time generative art piece where thousands of particles flow through a Perlin noise field that is continuously shaped by live microphone audio. Bass frequencies warp the field, mids control speed and warmth, treble spawns bursts — the art literally dances to sound.

---

## Key Features

- Real-time audio reactivity via microphone input
- Perlin noise flow field modulated by frequency bands (bass, mids, highs)
- ~3000–5000 particles with semi-transparent trail rendering
- Beat detection triggers color shifts and particle bursts
- Warm-to-cool color palette driven by audio intensity
- No extra dependencies beyond Processing core (audio via `javax.sound.sampled`)

---

## How It Works

### Audio → Visuals Mapping

| Audio Band    | Visual Parameter                   | Effect                                       |
| ------------- | ---------------------------------- | -------------------------------------------- |
| **Amplitude** | Field turbulence (noise scale)     | Louder = more chaotic swirls                 |
| **Bass**      | Field warp intensity + particle size | Deep sounds = large flowing movements      |
| **Mids**      | Particle speed + color warmth      | Melody drives pace and hue                   |
| **Highs**     | Spawn rate + field rotation offset | Crisp sounds = bursts of new particles       |
| **Beat**      | Color palette shift + particle burst | On each beat, a wave of new particles erupts |

### Architecture

```
Microphone → AudioAnalyzer (FFT + band split)
                ↓
         bass, mids, highs, amplitude
           ↓                ↓
      FlowField         Particles
   (Perlin noise)    (spawn, size, speed)
           ↓                ↓
         angle vectors → Particle steering
                ↓
        PApplet draw() (trail buffer rendering)
```

---

## Keyboard Controls

- **S** — Save current frame as PNG
- **R** — Reset all particles
- **SPACE** — Pause / resume audio capture
- **1 / 2 / 3** — Toggle frequency band visibility

---

## Technology Stack

- **Language:** Java 21
- **Graphics Framework:** Processing 4.5.0
- **Audio Capture:** `javax.sound.sampled` (JDK built-in)
- **FFT:** Custom lightweight implementation
- **Build System:** Maven

---

## Running

```bash
cd audio-flow-field
mvn compile exec:java
```

The sketch opens a window and immediately begins reacting to microphone input.

---

## Project Structure

```
audio-flow-field/
├── pom.xml
├── README.md
└── src/main/java/art/
    ├── AudioFlowField.java   — Main PApplet sketch
    ├── AudioAnalyzer.java    — Mic capture + FFT band splitting
    ├── FlowField.java        — Noise-based vector field
    ├── Particle.java         — Individual flowing particle
    └── FFT.java              — Lightweight FFT implementation
```

---

## License

This project is provided for educational purposes.

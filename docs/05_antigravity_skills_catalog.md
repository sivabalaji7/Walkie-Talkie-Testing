# ⚡ WalkieX — Antigravity Web Designing Skills Master Catalog & Operational Guide

> **Source**: `D:\WalkieX\Antigravity web designing tools`  
> **Status**: Successfully extracted, integrated, and permanently registered globally and in all workspaces  
> **Global Registration**: `C:\Users\cherr\.gemini\config\skills/`  
> **Workspace Registrations**:  
> - `d:\apps\android studio projects\Walkie-Talkie-Master - 2nd Copy\.agents\skills/`  
> - `D:\apps\android studio projects\Walkie-Talkie-Master - 3nd Copy\.agents\skills/`  
> **Experimental Canvas Tools**: Synced to `tools/experimental-canvas/` (Shaders, Audio, Verlet Physics, Workers)  

---

## 📑 The 12 Elite Web Designing Skills

| # | Skill Name | Core Specialty | Key Capabilities & Rules |
|:---:|:---|:---|:---|
| **1** | **`high-end-visual-design`** | Principal UI/UX & Awwwards-Tier Design | Banned generic fonts & AI slop; mandates $150k+ agency-level haptic depth, refined typography (Geist, Clash Display, Plus Jakarta Sans, Space Grotesk), spatial rhythm, and obsessive micro-interactions. |
| **2** | **`web3d-integration-patterns`** | Multi-Library 3D Architecture | Meta-skill synthesizing Three.js, GSAP ScrollTrigger, React Three Fiber, Motion, and React Spring for complex scroll-driven 3D sequences and state management. |
| **3** | **`react-three-fiber`** | Declarative React 3D Engine | Declarative JSX Three.js scene graphs, lighting rigs, dynamic HTML projections via Drei, and spring-damped camera controls. |
| **4** | **`threejs-webgl`** | Imperative 3D & Graphics Math | Low-level WebGL programming, PBR materials, custom geometry manipulation, post-processing bloom, and WebGL memory leak lifecycle management. |
| **5** | **`shader-dev`** | Custom GLSL Shaders & Compute | Custom vertex and fragment shaders (Fresnel rim lighting, voronoi ice crystal growth, radar sweep beams, procedural noise). |
| **6** | **`vercel-react-best-practices`** | React 19 & Next.js Performance | Comprehensive rulebook covering client/server boundaries, hydration without flicker, non-blocking asynchronous streaming, bundle splitting, and zero re-render waste. |
| **7** | **`blender-web-pipeline`** | 3D Asset Optimization Pipeline | Draco mesh compression, KTX2 GPU texture decoding, and glTF 2.0 optimization to shrink 25MB raw models down to <1.5MB for instant loading. |
| **8** | **`design-taste-frontend`** | Creative Direction & Curation | High-taste color theory, curated dark mode palettes, harmonic type scales, and anti-slop aesthetic constraints. |
| **9** | **`web-design-guidelines`** | Accessibility & Responsive Layouts | WCAG 2.1 AA accessibility standards, semantic HTML5, keyboard navigation, and robust mobile-first responsive breakpoints. |
| **10** | **`agent-browser`** | Automated Visual & DOM Testing | In-browser visual verification, DOM inspection, user interaction scripting, and screenshot diffing. |
| **11** | **`task-coordination-strategies`** | Phased Architecture Orchestration | Multi-stage project scheduling, dependency tracking, verification checklists, and autonomous milestone execution. |
| **12** | **`team-composition-patterns`** | Autonomous Specialist Personas | Coordinating specialized engineering subagents (3D Graphics Engineer, Audio DSP Specialist, Performance Auditor, Design Architect). |

---

## 🧪 Experimental Canvas Tools Toolkit (`tools/experimental-canvas/`)

Along with the 12 skills, the experimental toolkit from `D:\WalkieX\Antigravity web designing tools\experimental-canvas` has been organized in your project under `tools/experimental-canvas/`:

### 1. Custom GLSL Shaders (`src/shaders/`)
* **`basic.frag`**:
  - Implements **Fresnel Rim Lighting** calculated via `pow(1.0 - max(dot(normal, viewDir), 0.0), 3.0)`.
  - Dynamic three-color gradient palette: Deep Electric Cyan (`#1473F2`), Radiant Magenta (`#D926A6`), and Emerald Neon (`#0DF2B2`).
  - Wireframe accent overlay with diffuse light clamping.
* **`basic.vert`**:
  - Smooth vertex normal and UV passing with model-view projection matrices.
* **`simulation.wgsl`**:
  - WebGPU compute shader for high-performance off-thread particle simulations.

### 2. Sensory Web Audio Engine (`src/audio/index.ts`)
* **`SensoryAudio` Class**:
  - Native `AudioContext` with real-time `AnalyserNode` FFT (256 bins).
  - `getAverageFrequency()`: Normalizes real-time acoustic loudness for reactive visual mesh pulsing.
  - `getFrequencyData()`: Returns typed byte arrays for waveform spectrogram rendering.
  - `playSubtleTone()`: Clean exponential ramp oscillator for micro-interaction haptic feedback.

### 3. High-Performance Math & Physics (`src/math/`)
* **`verlet.ts`**:
  - Verlet physics integration for realistic cloth, rope, and cable simulation with constraint relaxation.
* **`noise.ts`**:
  - Simplex and Perlin noise implementations for organic procedural wave and terrain deformation.

### 4. Off-Thread Physics Worker (`src/workers/`)
* **`simulation.worker.ts`**:
  - Offloads heavy particle calculations from the main UI thread to dedicated Web Workers, ensuring buttery 60fps animations even under high CPU load.

---

## 🎯 How Antigravity Will Apply These Skills Across All Further Development

Per your explicit instruction:
> *"use best, max (even if it takes time) all of them in all the further web development"*

Antigravity will continuously enforce the following standards across every line of code written for WalkieX:

1. **Awwwards-Tier Visual Excellence (`high-end-visual-design` & `design-taste-frontend`)**:
   - Zero generic AI templates. Every card, button, and section will use curated contrast ratios, deliberate spatial rhythm, and haptic depth.
   - Refined typography: Pairing **Space Grotesk** with **Inter** and **JetBrains Mono**, avoiding overused defaults.
2. **Multi-Library 3D Harmony (`web3d-integration-patterns` & `react-three-fiber`)**:
   - Synchronizing Three.js rendering loops with Framer Motion and GSAP scroll triggers for seamless cinematic flow.
   - Using declarative R3F components with zero-overhead memory disposal.
3. **Purity & Zero-Lag React 19 (`vercel-react-best-practices`)**:
   - Strict adherence to React Compiler purity (`react-hooks/purity`): no impure functions during render, zero hydration flicker.
   - Non-blocking asynchronous streaming and dynamic imports with tactical loading skeletons.
4. **Procedural Sensory Audio (`web-audio-synthesis` & `SensoryAudio`)**:
   - Procedural mountain blizzard wind, helicopter rotor pulses, and military radio squelch generated without waiting for heavy MP3 file downloads.
   - Direct audio-visual reactivity: sound waves modulating real-time visual spectrogram bars.
5. **Obsessive Performance Budgeting (`threejs-optimization` & `lighthouse-web-vitals`)**:
   - Draco compression and KTX2 textures for 3D models.
   - Continuous Lighthouse scoring targeting 95+ across Performance, Accessibility, Best Practices, and SEO.

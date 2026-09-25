# 🛠️ WalkieX — Next-Level Antigravity Skills, Plugins & Development Toolchain

> **Objective**: Equip Google Antigravity with elite-level skills, MCP plugins, and developer workflows to author, optimize, debug, and deploy world-class 3D WebGL and acoustic web applications.

---

## 📑 Table of Contents

1. [Architectural Overview of Antigravity Customizations](#1-architectural-overview-of-antigravity-customizations)
2. [Skill 1: `webgl-shader-craft`](#2-skill-1-webgl-shader-craft)
3. [Skill 2: `threejs-optimization`](#3-skill-2-threejs-optimization)
4. [Skill 3: `web-audio-synthesis`](#4-skill-3-web-audio-synthesis)
5. [Skill 4: `gsap-scroll-choreography`](#5-skill-4-gsap-scroll-choreography)
6. [Skill 5: `lighthouse-web-vitals`](#6-skill-5-lighthouse-web-vitals)
7. [MCP Server Integrations (Supabase, Vercel, 3D Pipeline)](#7-mcp-server-integrations-supabase-vercel-3d-pipeline)
8. [IDE Extensions for Android Studio & Antigravity IDE](#8-ide-extensions-for-android-studio--antigravity-ide)
9. [Automated NPM Toolchain Scripts](#9-automated-npm-toolchain-scripts)

---

## 1. Architectural Overview of Antigravity Customizations

Antigravity operates with a hierarchical customization architecture:
* **Global Customizations**: Stored at `C:\Users\cherr\.gemini\config\` (available across all workspaces).
* **Workspace Customizations**: Stored at `<workspace>/.agents/` (local to the specific project).
* **Custom Skills Structure**: Every skill is stored in `skills/<skill_name>/SKILL.md` with YAML frontmatter specifying its name, version, and trigger rules.

---

## 2. Skill 1: `webgl-shader-craft`

### Specification
```yaml
name: webgl-shader-craft
description: Authoring, debugging, and compiling custom GLSL vertex and fragment shaders for Three.js and React Three Fiber.
triggers:
  - "create shader"
  - "frost effect"
  - "radar sweep"
  - "holographic bloom"
```

### Core Implementation Guidelines
* **Frost & Ice Crystal Shader**:
  - Uses Voronoi cell noise in the fragment shader to grow crystalline frost patterns along screen edges based on an incoming `uTime` and `uFrostIntensity` uniform.
  ```glsl
  uniform float uTime;
  uniform float uFrost;
  varying vec2 vUv;

  float voronoi(vec2 p) {
    vec2 n = floor(p);
    vec2 f = fract(p);
    float md = 5.0;
    for (int j = -1; j <= 1; j++) {
      for (int i = -1; i <= 1; i++) {
        vec2 g = vec2(float(i), float(j));
        vec2 o = fract(sin(vec2(dot(n + g, vec2(127.1, 311.7)), dot(n + g, vec2(269.5, 183.3)))) * 43758.5453);
        vec2 r = g - f + o;
        float d = dot(r, r);
        if (d < md) md = d;
      }
    }
    return sqrt(md);
  }
  ```
* **Radar Sweep Shader**:
  - Calculates angle via `atan(vUv.y - 0.5, vUv.x - 0.5)` and creates a rotating phosphor green beam with smooth trailing exponential decay.

---

## 3. Skill 2: `threejs-optimization`

### Specification
```yaml
name: threejs-optimization
description: Optimizing WebGL scenes, Draco mesh compression, KTX2 texture decoding, draw-call minimization, and memory leak cleanup.
triggers:
  - "optimize 3d model"
  - "compress glb"
  - "reduce draw calls"
  - "fix webgl memory leak"
```

### Core Implementation Guidelines
* **Draco & KTX2 Optimization Pipeline**:
  ```bash
  # Shrink 25MB raw phone models to under 1.5MB:
  npx @gltf-transform/cli optimize input.glb output.glb \
    --compress draco \
    --texture-compress ktx2 \
    --slots-specular
  ```
* **React Compiler / Purity Guidelines**:
  - Never call `Math.random()` inside `useMemo` or during render in React 19.
  - Pre-generate static arrays with deterministic linear congruential generators (LCGs) or compute inside `useEffect` buffers.
* **Draw Call Batching**:
  - Replace individual Three.js particle meshes with `THREE.InstancedMesh` or `THREE.BufferGeometry` attributes to render 1,000+ nodes in a single draw call.

---

## 4. Skill 3: `web-audio-synthesis`

### Specification
```yaml
name: web-audio-synthesis
description: Zero-dependency procedural audio design using the native browser Web Audio API AudioContext.
triggers:
  - "procedural sound"
  - "howling wind"
  - "rotor sound"
  - "radio squelch"
```

### Core Implementation Guidelines
* **Procedural Brown Noise Generator**:
  - Generates random walk noise arrays to produce deep, organic low-frequency mountain wind rumbles.
* **Resonant Filter Modulation**:
  - Uses `BiquadFilterNode` (`type: "lowpass"`, `Q: 4.0`) connected to an `OscillatorNode` LFO (`0.25Hz`) to synthesize realistic whistling wind gusts over mountain ridges.
* **Tactical Radio Bandpass Envelope**:
  - Standard telephone audio bandwidth (300Hz to 3,400Hz) created via a bandpass filter with high Q factor (`1.2`), paired with a 1,200Hz sine burst for military roger beeps.

---

## 5. Skill 4: `gsap-scroll-choreography`

### Specification
```yaml
name: gsap-scroll-choreography
description: Engineering cinematic scroll-driven camera movements, 3D section pinning, and Lenis smooth scrolling.
triggers:
  - "cinematic scroll"
  - "camera choreography"
  - "door zoom transition"
```

### Core Implementation Guidelines
* **Scroll-Driven Camera Orbit**:
  - Links Three.js camera position `(x, y, z)` to scroll progression using GSAP timelines with `scrub: 1`.
* **Perspective Door Zoom Dive**:
  - Scales door containers (`scale(2.5)`) and animates CSS perspective depth (`perspective: 1200px`) to create the illusion of walking through a physical door frame.

---

## 6. Skill 5: `lighthouse-web-vitals`

### Specification
```yaml
name: lighthouse-web-vitals
description: Automated audit and enforcement of Google Core Web Vitals (LCP, CLS, INP) for heavy 3D and canvas websites.
triggers:
  - "optimize lighthouse"
  - "fix lcp"
  - "reduce cls"
```

### Core Implementation Guidelines
* **Zero Initial Blocking**:
  - All WebGL canvases must be dynamically imported via Next.js `dynamic(() => import(...), { ssr: false })`.
  - Provide a lightweight tactical CSS skeleton during initial WebGL shader compilation.
* **Layout Stability (CLS = 0)**:
  - All canvas containers must have explicit aspect ratios or fixed heights (`h-[650px]`) so layout does not shift when WebGL initializes.

---

## 7. MCP Server Integrations (Supabase, Vercel, 3D Pipeline)

### 1. `supabase` MCP Server (Connected)
* Manages database migrations for user squad channels, room members, and live WebSocket edge connection health.

### 2. `vercel` MCP / CLI Integration
* **Installation**: `npm install -g vercel`
* **Automated Workflow**:
  - Run preview builds on every branch commit.
  - Automatically enforce production security headers and 3D asset caching defined in `vercel.json`.

---

## 8. IDE Extensions for Android Studio & Antigravity IDE

| Extension | Platform | Benefit for WalkieX |
|:---|:---|:---|
| **Tailwind CSS v4 IntelliSense** | Android Studio / VS Code | Real-time autocomplete for custom Tactical Dark color tokens and HUD utilities. |
| **Shader languages support / GLSL** | Android Studio / VS Code | Full syntax highlighting, error checking, and code formatting for custom WebGL shaders. |
| **glTF Viewer / 3D Model Inspector** | Android Studio / VS Code | Preview `.glb` models, inspect polycounts, and verify PBR textures directly inside the IDE. |
| **Error Lens** | VS Code / IDE | Flags React 19 purity warnings and TypeScript generic errors inline as you type. |

---

## 9. Automated NPM Toolchain Scripts

Add these commands to `walkiex-web/package.json` to empower Antigravity:

```json
{
  "scripts": {
    "dev": "next dev",
    "build": "next build",
    "start": "next start",
    "lint": "eslint",
    "audit:bundle": "ANALYZE=true next build",
    "audit:types": "tsc --noEmit",
    "optimize:models": "gltf-transform optimize public/models/*.glb public/models/optimized/ --compress draco"
  }
}
```

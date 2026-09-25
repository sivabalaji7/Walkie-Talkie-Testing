import * as THREE from 'three';
import gsap from 'gsap';
import './style.css';

import vertexShader from './shaders/basic.vert';
import fragmentShader from './shaders/basic.frag';

import { CanvasRenderer, EventDispatcher, RenderLoop } from './core';
import { ProceduralNoise, VerletPhysicsSystem } from './math';
import { SensoryAudio } from './audio';
import { WorkerBridge } from './workers';

// 1. Core Systems Initialization
const events = new EventDispatcher();
const canvasRenderer = new CanvasRenderer();
const loop = new RenderLoop();
const noise = new ProceduralNoise();
const verlet = new VerletPhysicsSystem();
const audio = new SensoryAudio();
const workerBridge = new WorkerBridge();

// Update HUD telemetry values
const hudFps = document.getElementById('hud-fps');
const hudDpr = document.getElementById('hud-dpr');
const hudRenderer = document.getElementById('hud-renderer');

if (hudDpr) {
    hudDpr.textContent = `${Math.min(window.devicePixelRatio, 2).toFixed(1)}x`;
}
if (hudRenderer) {
    hudRenderer.textContent = canvasRenderer.renderer.capabilities.isWebGL2 ? 'WebGL 2.0' : 'WebGL 1.0';
}

// 2. Custom Shader Geometry Verification
const geometry = new THREE.IcosahedronGeometry(1.2, 64);
const uniforms = {
    uTime: { value: 0 },
    uResolution: { value: new THREE.Vector2(window.innerWidth, window.innerHeight) },
    uMouse: { value: new THREE.Vector2(0, 0) },
};

const shaderMaterial = new THREE.ShaderMaterial({
    vertexShader,
    fragmentShader,
    uniforms,
    wireframe: false,
});

const mesh = new THREE.Mesh(geometry, shaderMaterial);
canvasRenderer.scene.add(mesh);

// Background ambient wireframe accent
const wireGeometry = new THREE.IcosahedronGeometry(1.55, 8);
const wireMaterial = new THREE.MeshBasicMaterial({
    color: 0x38bdf8,
    wireframe: true,
    transparent: true,
    opacity: 0.12,
});
const wireMesh = new THREE.Mesh(wireGeometry, wireMaterial);
canvasRenderer.scene.add(wireMesh);

// 3. GSAP Interactive Interpolation
events.on('pointermove', (pointer) => {
    gsap.to(uniforms.uMouse.value, {
        x: pointer.ndcX,
        y: pointer.ndcY,
        duration: 0.8,
        ease: 'power2.out',
    });

    gsap.to(mesh.rotation, {
        x: pointer.ndcY * 0.4,
        y: pointer.ndcX * 0.4,
        duration: 1.2,
        ease: 'power3.out',
    });
});

// Interactive audio on click
window.addEventListener('click', () => {
    if (!audio.active) {
        audio.init();
        audio.playSubtleTone(440, 400);
    }
}, { once: true });

// 4. Resize Handling
events.on('resize', ({ width, height, dpr }) => {
    canvasRenderer.handleResize();
    uniforms.uResolution.value.set(width, height);
    if (hudDpr) {
        hudDpr.textContent = `${dpr.toFixed(1)}x`;
    }
});

// 5. Central Render Loop Execution (60 - 144 FPS verification)
loop.add((delta, elapsed, fps) => {
    // Uniform updates
    uniforms.uTime.value = elapsed;

    // Procedural noise kinetic pulsation
    const noisePulse = noise.sample2D(elapsed * 0.5, 0) * 0.05;
    mesh.scale.setScalar(1.0 + noisePulse);

    // Kinetic rotations
    mesh.rotation.z = elapsed * 0.08;
    wireMesh.rotation.x = -elapsed * 0.05;
    wireMesh.rotation.y = elapsed * 0.06;

    // Optional off-thread worker step call
    workerBridge.requestSimulationStep(delta, 500);

    // Physics step
    verlet.update(delta);

    // Telemetry display update
    if (hudFps) {
        hudFps.textContent = `${fps} FPS`;
    }

    // Render Scene
    canvasRenderer.render();
});

// Start loop
loop.start();

console.log('[Experimental Canvas] Initialized successfully. WebGL Context & Shaders active.');

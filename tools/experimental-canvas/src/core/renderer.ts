import * as THREE from 'three';

export interface RendererOptions {
    canvas?: HTMLCanvasElement;
    antialias?: boolean;
    alpha?: boolean;
}

export class CanvasRenderer {
    public renderer: THREE.WebGLRenderer;
    public scene: THREE.Scene;
    public camera: THREE.PerspectiveCamera;
    public canvas: HTMLCanvasElement;

    constructor(options: RendererOptions = {}) {
        this.scene = new THREE.Scene();
        this.scene.background = new THREE.Color(0x05070f);

        this.camera = new THREE.PerspectiveCamera(
            55,
            window.innerWidth / window.innerHeight,
            0.1,
            1000
        );
        this.camera.position.set(0, 0, 4);

        if (options.canvas) {
            this.canvas = options.canvas;
        } else {
            this.canvas = document.createElement('canvas');
            this.canvas.id = 'webgl-canvas';
            document.body.appendChild(this.canvas);
        }

        this.renderer = new THREE.WebGLRenderer({
            canvas: this.canvas,
            antialias: options.antialias ?? true,
            alpha: options.alpha ?? false,
            powerPreference: 'high-performance'
        });

        this.renderer.toneMapping = THREE.ACESFilmicToneMapping;
        this.renderer.toneMappingExposure = 1.1;

        this.handleResize();
    }

    public handleResize(): void {
        const width = window.innerWidth;
        const height = window.innerHeight;
        const dpr = Math.min(window.devicePixelRatio, 2);

        this.camera.aspect = width / height;
        this.camera.updateProjectionMatrix();

        this.renderer.setPixelRatio(dpr);
        this.renderer.setSize(width, height, false);
    }

    public render(): void {
        this.renderer.render(this.scene, this.camera);
    }

    public destroy(): void {
        this.renderer.dispose();
        this.canvas.remove();
    }
}

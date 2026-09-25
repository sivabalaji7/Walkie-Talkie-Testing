export type TickCallback = (delta: number, elapsed: number, fps: number) => void;

export class RenderLoop {
    private isRunning: boolean = false;
    private callbacks: Set<TickCallback> = new Set();
    private lastTime: number = 0;
    private startTime: number = 0;
    private frameCount: number = 0;
    private lastFpsLogTime: number = 0;
    private currentFps: number = 60;
    private rafId: number = 0;

    constructor() {
        this.tick = this.tick.bind(this);
    }

    public add(callback: TickCallback): () => void {
        this.callbacks.add(callback);
        return () => this.callbacks.delete(callback);
    }

    public start(): void {
        if (this.isRunning) return;
        this.isRunning = true;
        this.startTime = performance.now();
        this.lastTime = this.startTime;
        this.lastFpsLogTime = this.startTime;
        this.frameCount = 0;
        this.rafId = requestAnimationFrame(this.tick);
    }

    public stop(): void {
        this.isRunning = false;
        cancelAnimationFrame(this.rafId);
    }

    private tick(now: number): void {
        if (!this.isRunning) return;

        const delta = Math.min((now - this.lastTime) * 0.001, 0.1); // clamp delta
        const elapsed = (now - this.startTime) * 0.001;
        this.lastTime = now;
        this.frameCount++;

        // Periodic FPS calculation and logging every 1000ms
        const timeSinceLog = now - this.lastFpsLogTime;
        if (timeSinceLog >= 1000) {
            this.currentFps = Math.round((this.frameCount * 1000) / timeSinceLog);
            console.log(`[RenderLoop] Performance: ${this.currentFps} FPS (frame delta: ${(delta * 1000).toFixed(2)}ms)`);
            this.frameCount = 0;
            this.lastFpsLogTime = now;
        }

        // Execute callbacks
        this.callbacks.forEach(cb => cb(delta, elapsed, this.currentFps));

        this.rafId = requestAnimationFrame(this.tick);
    }

    public get fps(): number {
        return this.currentFps;
    }
}

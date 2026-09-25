/**
 * Worker Manager for offloading computational simulations
 */
export class WorkerBridge {
    private worker: Worker | null = null;
    private isBusy: boolean = false;

    constructor() {
        try {
            // Vite handles new URL(...) with type module for Web Workers
            this.worker = new Worker(
                new URL('./simulation.worker.ts', import.meta.url),
                { type: 'module' }
            );

            this.worker.onmessage = (e) => {
                this.isBusy = false;
                if (e.data?.type === 'step_completed') {
                    // Handled results
                }
            };

            this.worker.postMessage({ type: 'init', count: 2000 });
        } catch (err) {
            console.warn('[WorkerBridge] Web Worker init skipped or unsupported in environment:', err);
        }
    }

    public requestSimulationStep(delta: number, count: number = 2000): void {
        if (!this.worker || this.isBusy) return;
        this.isBusy = true;
        this.worker.postMessage({ type: 'step', count, delta });
    }

    public terminate(): void {
        this.worker?.terminate();
        this.worker = null;
    }
}

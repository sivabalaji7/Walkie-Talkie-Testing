// Dedicated Web Worker for off-thread physics & heavy math simulations

export interface WorkerPayload {
    type: 'step' | 'init';
    count?: number;
    delta?: number;
    buffer?: SharedArrayBuffer | Float32Array;
}

interface WorkerScope {
    onmessage: ((event: MessageEvent<WorkerPayload>) => void) | null;
    postMessage(message: unknown, transfer?: Transferable[]): void;
}

const ctx = self as unknown as WorkerScope;

ctx.onmessage = (event: MessageEvent<WorkerPayload>) => {
    const { type, count = 1000, delta = 0.016 } = event.data;

    if (type === 'init') {
        ctx.postMessage({ status: 'ready', count });
        return;
    }

    if (type === 'step') {
        // Compute load running off the main thread
        const positions = new Float32Array(count * 3);
        const time = performance.now() * 0.001;

        for (let i = 0; i < count; i++) {
            const i3 = i * 3;
            positions[i3] = Math.cos(time + i * 0.01) * 2.0;
            positions[i3 + 1] = Math.sin(time + i * 0.01) * 2.0;
            positions[i3 + 2] = Math.sin(time * 0.5 + i * 0.05) * 0.5;
        }

        ctx.postMessage({
            type: 'step_completed',
            positions: positions.buffer,
            delta
        }, [positions.buffer]);
    }
};

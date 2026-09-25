type EventCallback<T = any> = (data: T) => void;

export interface PointerState {
    x: number;       // Pixel X
    y: number;       // Pixel Y
    ndcX: number;    // Normalized device coords [-1, 1]
    ndcY: number;    // Normalized device coords [-1, 1]
    isDown: boolean;
}

export class EventDispatcher {
    private listeners: Map<string, Set<EventCallback>> = new Map();
    public pointer: PointerState = { x: 0, y: 0, ndcX: 0, ndcY: 0, isDown: false };

    constructor() {
        this.bindWindowEvents();
    }

    private bindWindowEvents(): void {
        window.addEventListener('resize', () => {
            this.emit('resize', {
                width: window.innerWidth,
                height: window.innerHeight,
                dpr: Math.min(window.devicePixelRatio, 2)
            });
        });

        window.addEventListener('pointermove', (e) => {
            this.pointer.x = e.clientX;
            this.pointer.y = e.clientY;
            this.pointer.ndcX = (e.clientX / window.innerWidth) * 2 - 1;
            this.pointer.ndcY = -(e.clientY / window.innerHeight) * 2 + 1;
            this.emit('pointermove', this.pointer);
        });

        window.addEventListener('pointerdown', (e) => {
            this.pointer.isDown = true;
            this.emit('pointerdown', e);
        });

        window.addEventListener('pointerup', (e) => {
            this.pointer.isDown = false;
            this.emit('pointerup', e);
        });
    }

    public on<T = any>(event: string, callback: EventCallback<T>): () => void {
        if (!this.listeners.has(event)) {
            this.listeners.set(event, new Set());
        }
        this.listeners.get(event)!.add(callback);
        return () => this.off(event, callback);
    }

    public off(event: string, callback: EventCallback): void {
        this.listeners.get(event)?.delete(callback);
    }

    public emit<T = any>(event: string, data?: T): void {
        this.listeners.get(event)?.forEach(cb => {
            try {
                cb(data);
            } catch (err) {
                console.error(`[EventDispatcher] Error in listener for "${event}":`, err);
            }
        });
    }
}

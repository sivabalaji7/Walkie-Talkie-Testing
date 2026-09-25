/**
 * Audio Context and Sensory Analyzer Toolkit
 */
export class SensoryAudio {
    private ctx: AudioContext | null = null;
    private analyser: AnalyserNode | null = null;
    private frequencyData: Uint8Array<ArrayBuffer> | null = null;
    private isInitialized: boolean = false;

    public init(): AudioContext {
        if (!this.ctx) {
            const AudioContextClass = window.AudioContext || (window as unknown as { webkitAudioContext: typeof AudioContext }).webkitAudioContext;
            this.ctx = new AudioContextClass();
            this.analyser = this.ctx.createAnalyser();
            this.analyser.fftSize = 256;
            this.frequencyData = new Uint8Array(new ArrayBuffer(this.analyser.frequencyBinCount));
            this.isInitialized = true;
        }

        if (this.ctx.state === 'suspended') {
            this.ctx.resume();
        }

        return this.ctx;
    }

    public getAverageFrequency(): number {
        if (!this.analyser || !this.frequencyData) return 0;
        this.analyser.getByteFrequencyData(this.frequencyData);
        let sum = 0;
        for (let i = 0; i < this.frequencyData.length; i++) {
            sum += this.frequencyData[i];
        }
        return sum / this.frequencyData.length / 255;
    }

    public getFrequencyData(): Uint8Array<ArrayBuffer> | null {
        if (!this.analyser || !this.frequencyData) return null;
        this.analyser.getByteFrequencyData(this.frequencyData);
        return this.frequencyData;
    }

    public playSubtleTone(freq: number = 220, durationMs: number = 300): void {
        const ctx = this.init();
        const osc = ctx.createOscillator();
        const gain = ctx.createGain();

        osc.type = 'sine';
        osc.frequency.setValueAtTime(freq, ctx.currentTime);

        gain.gain.setValueAtTime(0.08, ctx.currentTime);
        gain.gain.exponentialRampToValueAtTime(0.0001, ctx.currentTime + durationMs / 1000);

        osc.connect(gain);
        if (this.analyser) {
            gain.connect(this.analyser);
        }
        gain.connect(ctx.destination);

        osc.start();
        osc.stop(ctx.currentTime + durationMs / 1000);
    }

    public get active(): boolean {
        return this.isInitialized && this.ctx?.state === 'running';
    }
}

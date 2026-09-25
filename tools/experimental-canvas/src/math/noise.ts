import { createNoise2D, createNoise3D } from 'simplex-noise';

export class ProceduralNoise {
    private noise2D = createNoise2D();
    private noise3D = createNoise3D();

    public sample2D(x: number, y: number, frequency: number = 1.0): number {
        return this.noise2D(x * frequency, y * frequency);
    }

    public sample3D(x: number, y: number, z: number, frequency: number = 1.0): number {
        return this.noise3D(x * frequency, y * frequency, z * frequency);
    }

    /**
     * Fractal Brownian Motion (fBm) multi-octave noise
     */
    public fbm2D(x: number, y: number, octaves: number = 4, persistence: number = 0.5, lacunarity: number = 2.0): number {
        let total = 0;
        let frequency = 1;
        let amplitude = 1;
        let maxValue = 0;

        for (let i = 0; i < octaves; i++) {
            total += this.noise2D(x * frequency, y * frequency) * amplitude;
            maxValue += amplitude;
            amplitude *= persistence;
            frequency *= lacunarity;
        }

        return total / maxValue;
    }
}

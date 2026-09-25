/**
 * Verlet Integration Particle & Constraint Structure
 */

export interface Particle3D {
    x: number;
    y: number;
    z: number;
    oldX: number;
    oldY: number;
    oldZ: number;
    accX: number;
    accY: number;
    accZ: number;
    mass: number;
    pinned?: boolean;
}

export interface DistanceConstraint {
    p1: Particle3D;
    p2: Particle3D;
    length: number;
    stiffness: number;
}

export class VerletPhysicsSystem {
    public particles: Particle3D[] = [];
    public constraints: DistanceConstraint[] = [];
    public drag: number = 0.99;

    public createParticle(x: number, y: number, z: number, mass: number = 1.0, pinned: boolean = false): Particle3D {
        const p: Particle3D = {
            x, y, z,
            oldX: x, oldY: y, oldZ: z,
            accX: 0, accY: 0, accZ: 0,
            mass,
            pinned
        };
        this.particles.push(p);
        return p;
    }

    public addConstraint(p1: Particle3D, p2: Particle3D, length?: number, stiffness: number = 0.8): DistanceConstraint {
        const dist = length ?? Math.hypot(p2.x - p1.x, p2.y - p1.y, p2.z - p1.z);
        const c: DistanceConstraint = { p1, p2, length: dist, stiffness };
        this.constraints.push(c);
        return c;
    }

    public update(dt: number): void {
        const dtSq = dt * dt;

        // Step 1: Verlet Integration
        for (let i = 0; i < this.particles.length; i++) {
            const p = this.particles[i];
            if (p.pinned) continue;

            const vx = (p.x - p.oldX) * this.drag;
            const vy = (p.y - p.oldY) * this.drag;
            const vz = (p.z - p.oldZ) * this.drag;

            p.oldX = p.x;
            p.oldY = p.y;
            p.oldZ = p.z;

            p.x += vx + p.accX * dtSq;
            p.y += vy + p.accY * dtSq;
            p.z += vz + p.accZ * dtSq;

            // Reset instantaneous accelerations
            p.accX = 0;
            p.accY = 0;
            p.accZ = 0;
        }

        // Step 2: Relaxation / Constraint solving (multiple iterations for stiffness)
        for (let iter = 0; iter < 3; iter++) {
            for (let i = 0; i < this.constraints.length; i++) {
                const { p1, p2, length, stiffness } = this.constraints[i];
                const dx = p2.x - p1.x;
                const dy = p2.y - p1.y;
                const dz = p2.z - p1.z;
                const dist = Math.hypot(dx, dy, dz) || 0.0001;
                const diff = (dist - length) / dist * 0.5 * stiffness;

                const offsetX = dx * diff;
                const offsetY = dy * diff;
                const offsetZ = dz * diff;

                if (!p1.pinned) {
                    p1.x += offsetX;
                    p1.y += offsetY;
                    p1.z += offsetZ;
                }
                if (!p2.pinned) {
                    p2.x -= offsetX;
                    p2.y -= offsetY;
                    p2.z -= offsetZ;
                }
            }
        }
    }
}

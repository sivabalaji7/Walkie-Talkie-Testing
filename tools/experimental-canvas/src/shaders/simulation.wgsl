struct Particle {
    position : vec2<f32>,
    velocity : vec2<f32>,
};

@group(0) @binding(0) var<storage, read_write> particles : array<Particle>;

@compute @workgroup_size(64)
fn main(@builtin(global_invocation_id) global_id : vec3<u32>) {
    let index = global_id.x;
    if (index >= arrayLength(&particles)) {
        return;
    }

    var p = particles[index];
    p.position += p.velocity * 0.016;

    // Boundary bounces
    if (abs(p.position.x) > 1.0) {
        p.velocity.x *= -1.0;
    }
    if (abs(p.position.y) > 1.0) {
        p.velocity.y *= -1.0;
    }

    particles[index] = p;
}

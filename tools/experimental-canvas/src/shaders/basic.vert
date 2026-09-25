varying vec2 vUv;
varying vec3 vNormal;
varying vec3 vPosition;

uniform float uTime;

void main() {
    vUv = uv;
    vNormal = normalize(normalMatrix * normal);
    vPosition = position;

    // Subtle organic wave perturbation
    vec3 pos = position;
    float wave = sin(pos.x * 2.0 + uTime * 1.5) * cos(pos.y * 2.0 + uTime * 1.2) * 0.12;
    pos += normal * wave;

    gl_Position = projectionMatrix * modelViewMatrix * vec4(pos, 1.0);
}

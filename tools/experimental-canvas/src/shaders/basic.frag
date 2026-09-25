precision highp float;

uniform float uTime;
uniform vec2 uResolution;

varying vec2 vUv;
varying vec3 vNormal;
varying vec3 vPosition;

void main() {
    // Normal lighting & view direction estimation
    vec3 normal = normalize(vNormal);
    vec3 viewDir = vec3(0.0, 0.0, 1.0);

    // Fresnel rim effect
    float fresnel = pow(1.0 - max(dot(normal, viewDir), 0.0), 3.0);

    // Dynamic gradient palette
    vec3 colA = vec3(0.08, 0.45, 0.95); // Deep electric cyan-blue
    vec3 colB = vec3(0.85, 0.15, 0.65); // Radiant magenta
    vec3 colC = vec3(0.05, 0.95, 0.70); // Emerald neon

    float t = sin(uTime * 0.8 + vUv.x * 3.14159) * 0.5 + 0.5;
    vec3 baseColor = mix(colA, colB, t);
    baseColor = mix(baseColor, colC, sin(uTime * 0.5 + vUv.y * 3.14159) * 0.5 + 0.5);

    // Combine diffuse, fresnel glow, and wire accent
    float diffuse = clamp(dot(normal, normalize(vec3(1.0, 1.5, 2.0))), 0.15, 1.0);
    vec3 finalColor = baseColor * diffuse + vec3(0.9, 0.95, 1.0) * fresnel * 0.8;

    gl_FragColor = vec4(finalColor, 1.0);
}

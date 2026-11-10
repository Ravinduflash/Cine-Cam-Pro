#version 300 es
#extension GL_OES_EGL_image_external_essl3 : require
precision mediump float;

uniform samplerExternalOES uTexture;
uniform float uZebraThreshold;
uniform float uTime;

in vec2 vTexCoord;
out vec4 fragColor;

// Rec.709 Luminance weights for accurate cinema luminance calculation
float getLuminance(vec3 c) {
    return dot(c, vec3(0.2126, 0.7152, 0.0722));
}

void main() {
    vec4 rawColor = texture(uTexture, vTexCoord);
    float lum = getLuminance(rawColor.rgb);

    if (lum >= uZebraThreshold) {
        // High-contrast 45-degree diagonal moving zebra stripes
        // Moving stripes indicate active exposure clipping warning
        float stripe = mod(gl_FragCoord.x + gl_FragCoord.y - uTime * 35.0, 24.0);
        if (stripe < 12.0) {
            fragColor = vec4(0.04, 0.04, 0.05, 1.0); // Deep Black stripe
        } else {
            fragColor = vec4(1.0, 1.0, 1.0, 1.0); // Pure White stripe
        }
    } else {
        fragColor = rawColor;
    }
}

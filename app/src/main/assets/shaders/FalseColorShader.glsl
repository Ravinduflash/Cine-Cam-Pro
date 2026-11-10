#version 300 es
#extension GL_OES_EGL_image_external_essl3 : require
precision mediump float;

uniform samplerExternalOES uTexture;
in vec2 vTexCoord;
out vec4 fragColor;

// Rec.709 Luminance weights
float getLuminance(vec3 c) {
    return dot(c, vec3(0.2126, 0.7152, 0.0722));
}

// 16-zone Cinema IRE Exposure Heatmap
vec3 mapFalseColor(float ire) {
    if (ire < 0.02) return vec3(0.42, 0.0, 0.58);   // < 2 IRE: Crushed black / Clipped shadows (Deep Purple)
    if (ire < 0.12) return vec3(0.0, 0.20, 0.80);   // 2-12 IRE: Low Shadows (Deep Blue)
    if (ire < 0.28) return vec3(0.0, 0.55, 0.70);   // 12-28 IRE: Shadow details (Teal)
    if (ire < 0.38) return vec3(0.30, 0.30, 0.34);  // 28-38 IRE: Low mid-tones (Slate Grey)
    if (ire <= 0.43) return vec3(0.50, 0.50, 0.50); // 38-43 IRE: 18% Neutral Grey Reference Standard
    if (ire < 0.52) return vec3(0.38, 0.38, 0.42);  // 43-52 IRE: Mid-tones
    if (ire <= 0.58) return vec3(0.95, 0.45, 0.65); // 52-58 IRE: Caucasian / Key Skin Tone Reference (Pink)
    if (ire < 0.70) return vec3(0.58, 0.58, 0.58);  // 58-70 IRE: High mid-tones
    if (ire <= 0.76) return vec3(0.0, 0.85, 0.20);  // 70-76 IRE: 90% Reflectance White Card (Vibrant Green)
    if (ire < 0.85) return vec3(0.72, 0.72, 0.40);  // 76-85 IRE: Highlights
    if (ire <= 0.94) return vec3(0.96, 0.90, 0.0);  // 85-94 IRE: Sub-clipping warning zone (Yellow)
    return vec3(1.0, 0.05, 0.05);                   // > 95 IRE: Blown highlights / Clipping (Pure Red)
}

void main() {
    vec4 rawColor = texture(uTexture, vTexCoord);
    float ire = getLuminance(rawColor.rgb);
    fragColor = vec4(mapFalseColor(ire), 1.0);
}

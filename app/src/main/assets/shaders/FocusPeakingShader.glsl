#version 300 es
#extension GL_OES_EGL_image_external_essl3 : require
precision mediump float;

uniform samplerExternalOES uTexture;
uniform vec2 uTexelSize;
uniform vec3 uPeakingColor;
uniform float uPeakingThreshold;

in vec2 vTexCoord;
out vec4 fragColor;

// Rec.709 Luminance weights
float getLuminance(vec3 c) {
    return dot(c, vec3(0.2126, 0.7152, 0.0722));
}

void main() {
    vec4 centerColor = texture(uTexture, vTexCoord);

    float dx = uTexelSize.x;
    float dy = uTexelSize.y;

    // 3x3 Neighborhood luminance sampling for Sobel convolution
    float l00 = getLuminance(texture(uTexture, vTexCoord + vec2(-dx, -dy)).rgb);
    float l01 = getLuminance(texture(uTexture, vTexCoord + vec2(-dx,  0.0)).rgb);
    float l02 = getLuminance(texture(uTexture, vTexCoord + vec2(-dx,  dy)).rgb);
    float l10 = getLuminance(texture(uTexture, vTexCoord + vec2( 0.0, -dy)).rgb);
    float l12 = getLuminance(texture(uTexture, vTexCoord + vec2( 0.0,  dy)).rgb);
    float l20 = getLuminance(texture(uTexture, vTexCoord + vec2( dx, -dy)).rgb);
    float l21 = getLuminance(texture(uTexture, vTexCoord + vec2( dx,  0.0)).rgb);
    float l22 = getLuminance(texture(uTexture, vTexCoord + vec2( dx,  dy)).rgb);

    // Sobel gradient horizontal (Gx) and vertical (Gy)
    float gx = -l00 - 2.0 * l01 - l02 + l20 + 2.0 * l21 + l22;
    float gy = -l00 - 2.0 * l10 - l20 + l02 + 2.0 * l12 + l22;
    float edge = sqrt(gx * gx + gy * gy);

    if (edge > uPeakingThreshold) {
        // High-contrast in-focus edge detected: render vibrant peaking color
        fragColor = vec4(mix(centerColor.rgb, uPeakingColor, 0.88), 1.0);
    } else {
        fragColor = centerColor;
    }
}

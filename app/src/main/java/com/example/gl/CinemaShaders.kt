package com.example.gl

object CinemaShaders {

    val VERTEX_SHADER = """
        attribute vec4 a_position;
        attribute vec2 a_texCoord;
        uniform mat4 u_stMatrix;
        varying vec2 v_texCoord;

        void main() {
            gl_Position = a_position;
            v_texCoord = (u_stMatrix * vec4(a_texCoord, 0.0, 1.0)).xy;
        }
    """.trimIndent()

    val FRAGMENT_SHADER_OES = """
        #extension GL_OES_EGL_image_external : require
        precision mediump float;

        uniform samplerExternalOES u_texture;
        uniform vec2 u_texSize;
        uniform int u_assist_mode; // 0: None, 1: Peaking, 2: False Color, 3: Zebras
        uniform vec3 u_peaking_color;
        uniform float u_peaking_threshold;
        uniform float u_zebra_threshold;
        uniform float u_kelvin_r;
        uniform float u_kelvin_b;
        uniform float u_tint_g;
        uniform float u_exposure_multiplier;

        varying vec2 v_texCoord;

        // Luminance calculation (Rec.709)
        float getLum(vec3 color) {
            return dot(color, vec3(0.2126, 0.7152, 0.0722));
        }

        // False color mapping across 16 cinema IRE zones
        vec3 mapFalseColor(float ire) {
            if (ire < 0.02) return vec3(0.4, 0.0, 0.6); // < 2 IRE Clipped shadows (Purple)
            if (ire < 0.10) return vec3(0.0, 0.2, 0.8); // 2-10 IRE Shadows (Blue)
            if (ire < 0.25) return vec3(0.0, 0.5, 0.7); // 10-25 IRE Dark tones (Teal)
            if (ire < 0.38) return vec3(0.25, 0.25, 0.28); // 25-38 IRE Mid-dark
            if (ire <= 0.43) return vec3(0.5, 0.5, 0.5); // 38-43 IRE 18% Middle Grey (Reference Grey)
            if (ire < 0.52) return vec3(0.35, 0.35, 0.38); // 43-52 IRE Low skin tone
            if (ire <= 0.58) return vec3(0.95, 0.45, 0.65); // 52-58 IRE Skin tone ideal (Pink)
            if (ire < 0.70) return vec3(0.55, 0.55, 0.55); // 58-70 IRE High skin tone
            if (ire <= 0.76) return vec3(0.0, 0.85, 0.2); // 70-76 IRE 90% White card (Green)
            if (ire < 0.85) return vec3(0.7, 0.7, 0.4); // 76-85 IRE Bright highlights
            if (ire <= 0.94) return vec3(0.95, 0.90, 0.0); // 85-94 IRE Near clipping (Yellow)
            return vec3(1.0, 0.0, 0.0); // > 95 IRE Clipped highlights (Red)
        }

        void main() {
            vec3 rawRgb = texture2D(u_texture, v_texCoord).rgb;

            // Apply exposure and manual white balance / tint gains
            vec3 gradedRgb = rawRgb * u_exposure_multiplier;
            gradedRgb.r *= u_kelvin_r;
            gradedRgb.b *= u_kelvin_b;
            gradedRgb.g *= u_tint_g;
            gradedRgb = clamp(gradedRgb, 0.0, 1.0);

            float lum = getLum(gradedRgb);

            if (u_assist_mode == 1) {
                // Focus Peaking: 3x3 Sobel edge filter
                float dx = 1.0 / u_texSize.x;
                float dy = 1.0 / u_texSize.y;

                float l00 = getLum(texture2D(u_texture, v_texCoord + vec2(-dx, -dy)).rgb);
                float l01 = getLum(texture2D(u_texture, v_texCoord + vec2(-dx, 0.0)).rgb);
                float l02 = getLum(texture2D(u_texture, v_texCoord + vec2(-dx, dy)).rgb);
                float l10 = getLum(texture2D(u_texture, v_texCoord + vec2(0.0, -dy)).rgb);
                float l12 = getLum(texture2D(u_texture, v_texCoord + vec2(0.0, dy)).rgb);
                float l20 = getLum(texture2D(u_texture, v_texCoord + vec2(dx, -dy)).rgb);
                float l21 = getLum(texture2D(u_texture, v_texCoord + vec2(dx, 0.0)).rgb);
                float l22 = getLum(texture2D(u_texture, v_texCoord + vec2(dx, dy)).rgb);

                float gx = -l00 - 2.0 * l01 - l02 + l20 + 2.0 * l21 + l22;
                float gy = -l00 - 2.0 * l10 - l20 + l02 + 2.0 * l12 + l22;
                float edge = sqrt(gx * gx + gy * gy);

                if (edge > u_peaking_threshold) {
                    // Overlay vibrant peaking color on high-contrast focus edges
                    vec3 peaking = mix(gradedRgb, u_peaking_color, 0.85);
                    gl_FragColor = vec4(peaking, 1.0);
                } else {
                    // Monochrome desaturation background to emphasize peaking lines
                    float bgLum = lum * 0.7;
                    gl_FragColor = vec4(vec3(bgLum), 1.0);
                }
            } else if (u_assist_mode == 2) {
                // False Color Exposure map
                gl_FragColor = vec4(mapFalseColor(lum), 1.0);
            } else if (u_assist_mode == 3) {
                // Zebra Stripes for highlights exceeding threshold
                if (lum >= u_zebra_threshold) {
                    float stripe = mod(gl_FragCoord.x + gl_FragCoord.y, 20.0);
                    if (stripe < 10.0) {
                        gl_FragColor = vec4(0.0, 0.0, 0.0, 1.0);
                    } else {
                        gl_FragColor = vec4(1.0, 1.0, 1.0, 1.0);
                    }
                } else {
                    gl_FragColor = vec4(gradedRgb, 1.0);
                }
            } else {
                // Normal cinema preview
                gl_FragColor = vec4(gradedRgb, 1.0);
            }
        }
    """.trimIndent()

    // 2D Texture Shader for fallback / simulated cinema sensor pattern
    val FRAGMENT_SHADER_2D = """
        precision mediump float;
        uniform sampler2D u_texture;
        uniform vec2 u_texSize;
        uniform int u_assist_mode;
        uniform vec3 u_peaking_color;
        uniform float u_peaking_threshold;
        uniform float u_zebra_threshold;
        uniform float u_kelvin_r;
        uniform float u_kelvin_b;
        uniform float u_tint_g;
        uniform float u_exposure_multiplier;

        varying vec2 v_texCoord;

        float getLum(vec3 color) {
            return dot(color, vec3(0.2126, 0.7152, 0.0722));
        }

        vec3 mapFalseColor(float ire) {
            if (ire < 0.02) return vec3(0.4, 0.0, 0.6);
            if (ire < 0.10) return vec3(0.0, 0.2, 0.8);
            if (ire < 0.25) return vec3(0.0, 0.5, 0.7);
            if (ire < 0.38) return vec3(0.25, 0.25, 0.28);
            if (ire <= 0.43) return vec3(0.5, 0.5, 0.5);
            if (ire < 0.52) return vec3(0.35, 0.35, 0.38);
            if (ire <= 0.58) return vec3(0.95, 0.45, 0.65);
            if (ire < 0.70) return vec3(0.55, 0.55, 0.55);
            if (ire <= 0.76) return vec3(0.0, 0.85, 0.2);
            if (ire < 0.85) return vec3(0.7, 0.7, 0.4);
            if (ire <= 0.94) return vec3(0.95, 0.90, 0.0);
            return vec3(1.0, 0.0, 0.0);
        }

        void main() {
            vec3 rawRgb = texture2D(u_texture, v_texCoord).rgb;
            vec3 gradedRgb = rawRgb * u_exposure_multiplier;
            gradedRgb.r *= u_kelvin_r;
            gradedRgb.b *= u_kelvin_b;
            gradedRgb.g *= u_tint_g;
            gradedRgb = clamp(gradedRgb, 0.0, 1.0);

            float lum = getLum(gradedRgb);

            if (u_assist_mode == 1) {
                float dx = 1.0 / u_texSize.x;
                float dy = 1.0 / u_texSize.y;

                float l00 = getLum(texture2D(u_texture, v_texCoord + vec2(-dx, -dy)).rgb);
                float l01 = getLum(texture2D(u_texture, v_texCoord + vec2(-dx, 0.0)).rgb);
                float l02 = getLum(texture2D(u_texture, v_texCoord + vec2(-dx, dy)).rgb);
                float l10 = getLum(texture2D(u_texture, v_texCoord + vec2(0.0, -dy)).rgb);
                float l12 = getLum(texture2D(u_texture, v_texCoord + vec2(0.0, dy)).rgb);
                float l20 = getLum(texture2D(u_texture, v_texCoord + vec2(dx, -dy)).rgb);
                float l21 = getLum(texture2D(u_texture, v_texCoord + vec2(dx, 0.0)).rgb);
                float l22 = getLum(texture2D(u_texture, v_texCoord + vec2(dx, dy)).rgb);

                float gx = -l00 - 2.0 * l01 - l02 + l20 + 2.0 * l21 + l22;
                float gy = -l00 - 2.0 * l10 - l20 + l02 + 2.0 * l12 + l22;
                float edge = sqrt(gx * gx + gy * gy);

                if (edge > u_peaking_threshold) {
                    gl_FragColor = vec4(mix(gradedRgb, u_peaking_color, 0.85), 1.0);
                } else {
                    gl_FragColor = vec4(vec3(lum * 0.7), 1.0);
                }
            } else if (u_assist_mode == 2) {
                gl_FragColor = vec4(mapFalseColor(lum), 1.0);
            } else if (u_assist_mode == 3) {
                if (lum >= u_zebra_threshold) {
                    float stripe = mod(gl_FragCoord.x + gl_FragCoord.y, 20.0);
                    gl_FragColor = (stripe < 10.0) ? vec4(0.0, 0.0, 0.0, 1.0) : vec4(1.0, 1.0, 1.0, 1.0);
                } else {
                    gl_FragColor = vec4(gradedRgb, 1.0);
                }
            } else {
                gl_FragColor = vec4(gradedRgb, 1.0);
            }
        }
    """.trimIndent()
}

/*
 *
 * IMPORTANT:
 *
 * The SurfaceTexture functionality from ICS provides the video frames from
 * the movie in an unconventional format. So we cant use Texture2D but we
 * need to use the ExternalOES extension.
 *
 * Two things that are important in the shader below. The first is the
 * extension declaration (first line). The second is the type of the
 * texSamplerOES uniform.
 */

#extension GL_OES_EGL_image_external : require
precision mediump float;
varying vec2 texCoord;

uniform vec3 chromaKey;
uniform samplerExternalOES texSamplerOES;

void main()
{
    // Keying for specific color
    vec3 keying_color = chromaKey;
    float l_thresh = 0.1;
    float h_thresh = 0.1;
    vec3 input_color = texture2D(texSamplerOES, texCoord).rgb;
    if (input_color.b > 0.9) {
        gl_FragColor = vec4(input_color, 0.0);
    } else {
        gl_FragColor = vec4(input_color, 1.0);
    }
//    if (input_color.r > keying_color.r - l_thresh && input_color.r < keying_color.r + h_thresh &&
//    input_color.g > keying_color.g - l_thresh && input_color.g < keying_color.g + h_thresh &&
//    input_color.b > keying_color.b - l_thresh && input_color.b < keying_color.b + h_thresh) {
//        gl_FragColor = vec4(input_color, 0.0);
//    } else {
//        gl_FragColor = vec4(input_color, 1.0);
//    }
//    float d = abs(length(abs(keying_color.rgb - input_color.rgb)));
//    float edge0 = thresh * (1.0 - slope);
//    float alpha = smoothstep(edge0, thresh, d);
//    gl_FragColor = vec4(input_color, alpha);
}
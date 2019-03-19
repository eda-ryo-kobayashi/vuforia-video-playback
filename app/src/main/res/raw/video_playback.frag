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
    float thresh = 0.8;
    float slope = 0.2;
    vec3 input_color = texture2D(texSamplerOES, texCoord).rgb;
    float d = abs(length(abs(keying_color.rgb - input_color.rgb)));
    float edge0 = thresh * (1.0 - slope);
    float alpha = smoothstep(edge0, thresh, d);
    gl_FragColor = vec4(input_color, alpha);
}
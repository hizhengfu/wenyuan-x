#version 300 es
precision mediump float;
precision highp int;

uniform highp sampler2D SPIRV_Cross_CombinedsceneColourTexturesceneColourSampler;

in highp vec2 varying_TEXCOORD0;
layout(location = 0) out highp vec4 out_var_SV_Target;

void main()
{
    highp vec4 _32 = texture(SPIRV_Cross_CombinedsceneColourTexturesceneColourSampler, varying_TEXCOORD0);
    highp float _33 = _32.w;
    out_var_SV_Target = mix(vec4(0.0), vec4(_33, 0.0, 1.0, 1.0), vec4(float(int(_33 * 255.0) & 1)));
}


#version 300 es
precision mediump float;
precision highp int;

uniform highp sampler2D SPIRV_Cross_CombinedsceneColourTexturesceneColourSampler;
uniform highp sampler2D SPIRV_Cross_CombinedsceneDepthTexturesceneDepthSampler;

in highp vec2 varying_TEXCOORD0;
layout(location = 0) out highp vec4 out_var_SV_Target0;
layout(location = 1) out highp vec4 out_var_SV_Target1;

void main()
{
    highp vec4 _39 = texture(SPIRV_Cross_CombinedsceneColourTexturesceneColourSampler, varying_TEXCOORD0);
    highp vec4 _43 = texture(SPIRV_Cross_CombinedsceneDepthTexturesceneDepthSampler, varying_TEXCOORD0);
    highp float _44 = _43.x;
    out_var_SV_Target0 = vec4(_39.zyx, float((int(_39.w * 255.0) >> 1) & 7) * 0.14285714924335479736328125);
    out_var_SV_Target1 = vec4(0.0, ((step(0.0, 0.0) * 2.0) - 1.0) * _44, 0.0, 0.0);
    gl_FragDepth = _44;
}


#version 300 es
precision mediump float;
precision highp int;

layout(std140) uniform type_u_fragParams
{
    highp vec4 u_colour;
} u_fragParams;

uniform highp sampler2D SPIRV_Cross_CombinedsceneColourTexturesceneColourSampler;
uniform highp sampler2D SPIRV_Cross_CombinedsceneDepthTexturesceneDepthSampler;
uniform highp sampler2D SPIRV_Cross_CombinedsceneNormalTexturesceneNormalSampler;

in highp vec2 varying_TEXCOORD0;
in highp vec2 varying_TEXCOORD1;
in highp vec2 varying_TEXCOORD2;
in highp vec2 varying_TEXCOORD3;
in highp vec2 varying_TEXCOORD4;
layout(location = 0) out highp vec4 out_var_SV_Target0;
layout(location = 1) out highp vec4 out_var_SV_Target1;

void main()
{
    highp vec4 _61 = texture(SPIRV_Cross_CombinedsceneDepthTexturesceneDepthSampler, varying_TEXCOORD0);
    highp float _62 = _61.x;
    highp float _75 = texture(SPIRV_Cross_CombinedsceneDepthTexturesceneDepthSampler, varying_TEXCOORD1).x - _62;
    highp float _76 = texture(SPIRV_Cross_CombinedsceneDepthTexturesceneDepthSampler, varying_TEXCOORD2).x - _62;
    highp float _77 = texture(SPIRV_Cross_CombinedsceneDepthTexturesceneDepthSampler, varying_TEXCOORD3).x - _62;
    highp float _78 = texture(SPIRV_Cross_CombinedsceneDepthTexturesceneDepthSampler, varying_TEXCOORD4).x - _62;
    out_var_SV_Target0 = vec4(mix(texture(SPIRV_Cross_CombinedsceneColourTexturesceneColourSampler, varying_TEXCOORD0).xyz, u_fragParams.u_colour.xyz, vec3((max(0.0, (((((step(_75, -1.0000000116860974230803549289703e-07) * step(abs(_75), 0.00999999977648258209228515625)) + (step(_76, -1.0000000116860974230803549289703e-07) * step(abs(_76), 0.00999999977648258209228515625))) + (step(_77, -1.0000000116860974230803549289703e-07) * step(abs(_77), 0.00999999977648258209228515625))) + (step(_78, -1.0000000116860974230803549289703e-07) * step(abs(_78), 0.00999999977648258209228515625))) * 0.25) - 0.5) * 2.0) * u_fragParams.u_colour.w)), 1.0);
    out_var_SV_Target1 = texture(SPIRV_Cross_CombinedsceneNormalTexturesceneNormalSampler, varying_TEXCOORD0);
    gl_FragDepth = _62;
}


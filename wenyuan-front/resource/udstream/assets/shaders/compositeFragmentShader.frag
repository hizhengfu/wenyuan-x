#version 300 es
precision mediump float;
precision highp int;

layout(std140) uniform type_u_params
{
    highp vec4 u_screenParams;
    highp vec4 u_saturation;
} u_params;

uniform highp sampler2D SPIRV_Cross_CombinedsceneColourTexturesceneColourSampler;

in highp vec2 varying_TEXCOORD0;
in highp vec4 varying_TEXCOORD1;
layout(location = 0) out highp vec4 out_var_SV_Target0;

void main()
{
    highp vec3 _41 = texture(SPIRV_Cross_CombinedsceneColourTexturesceneColourSampler, varying_TEXCOORD0).xyz;
    out_var_SV_Target0 = vec4(mix(vec3(dot(_41, vec3(0.2125000059604644775390625, 0.7153999805450439453125, 0.07209999859333038330078125))), _41, vec3(u_params.u_saturation.x)), 1.0);
}


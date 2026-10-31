#version 300 es
precision mediump float;
precision highp int;

layout(std140) uniform type_u_cameraPlaneParams
{
    highp float s_CameraNearPlane;
    highp float s_CameraFarPlane;
    highp float u_clipZNear;
    highp float u_clipZFar;
} u_cameraPlaneParams;

uniform highp sampler2D SPIRV_Cross_CombinedcolourTexturecolourSampler;
uniform highp sampler2D SPIRV_Cross_CombinednormalTexturenormalSampler;

in highp vec4 varying_COLOR0;
in highp vec2 varying_TEXCOORD0;
in highp vec2 varying_TEXCOORD1;
in highp vec2 varying_TEXCOORD2;
in highp vec2 varying_TEXCOORD3;
in highp vec3 varying_TEXCOORD4;
in highp vec3 varying_TEXCOORD5;
in highp vec2 varying_TEXCOORD6;
layout(location = 0) out highp vec4 out_var_SV_Target0;
layout(location = 1) out highp vec4 out_var_SV_Target1;

void main()
{
    highp vec4 _64 = texture(SPIRV_Cross_CombinedcolourTexturecolourSampler, varying_TEXCOORD0);
    highp vec3 _71 = (texture(SPIRV_Cross_CombinednormalTexturenormalSampler, varying_TEXCOORD3).xyz * vec3(2.0)) - vec3(1.0);
    highp vec3 _77 = _71;
    _77.y = _71.y * (-1.0);
    highp float _104;
    do
    {
        if (varying_TEXCOORD6.y != 0.0)
        {
            _104 = varying_TEXCOORD6.x / varying_TEXCOORD6.y;
            break;
        }
        _104 = log2(varying_TEXCOORD6.x) * (1.0 / log2(u_cameraPlaneParams.s_CameraFarPlane + 1.0));
        break;
    } while(false);
    highp vec3 _108 = normalize(mix(vec3(0.0, 1.0, 0.0), normalize(mat3(normalize(cross(varying_TEXCOORD5, varying_TEXCOORD4)), varying_TEXCOORD5, varying_TEXCOORD4) * _77), vec3(step(0.99989998340606689453125, varying_COLOR0.w))));
    out_var_SV_Target0 = vec4(_64.xyz * varying_COLOR0.xyz, varying_COLOR0.w);
    out_var_SV_Target1 = vec4(varying_TEXCOORD2.x, ((step(0.0, _108.z) * 2.0) - 1.0) * _104, _108.xy);
    gl_FragDepth = _104;
}


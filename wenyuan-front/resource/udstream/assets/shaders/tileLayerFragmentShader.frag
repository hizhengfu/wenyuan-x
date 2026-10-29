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
    highp vec4 _52 = texture(SPIRV_Cross_CombinedcolourTexturecolourSampler, varying_TEXCOORD0);
    highp float _79;
    do
    {
        if (varying_TEXCOORD6.y != 0.0)
        {
            _79 = varying_TEXCOORD6.x / varying_TEXCOORD6.y;
            break;
        }
        _79 = log2(varying_TEXCOORD6.x) * (1.0 / log2(u_cameraPlaneParams.s_CameraFarPlane + 1.0));
        break;
    } while(false);
    out_var_SV_Target0 = vec4(_52.xyz * varying_COLOR0.xyz, _52.w * varying_COLOR0.w);
    out_var_SV_Target1 = vec4(0.0);
    gl_FragDepth = _79;
}


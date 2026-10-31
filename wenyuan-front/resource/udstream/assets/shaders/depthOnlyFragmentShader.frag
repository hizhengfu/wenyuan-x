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

in highp vec2 varying_TEXCOORD0;
in highp vec3 varying_NORMAL;
in highp vec4 varying_COLOR0;
in highp vec2 varying_TEXCOORD1;
in highp vec2 varying_TEXCOORD2;
layout(location = 0) out highp vec4 out_var_SV_Target0;
layout(location = 1) out highp vec4 out_var_SV_Target1;

void main()
{
    highp float _53;
    do
    {
        if (varying_TEXCOORD1.y != 0.0)
        {
            _53 = varying_TEXCOORD1.x / varying_TEXCOORD1.y;
            break;
        }
        _53 = log2(varying_TEXCOORD1.x) * (1.0 / log2(u_cameraPlaneParams.s_CameraFarPlane + 1.0));
        break;
    } while(false);
    out_var_SV_Target0 = vec4(0.0);
    out_var_SV_Target1 = vec4(0.0);
    gl_FragDepth = _53;
}


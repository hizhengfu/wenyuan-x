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

layout(std140) uniform type_u_EveryFrameFrag
{
    layout(row_major) highp mat4 u_inverseMatrix;
    highp vec4 u_colour;
    highp vec4 u_params;
} u_EveryFrameFrag;

uniform highp sampler2D SPIRV_Cross_CombinedsceneDepthTexturesceneDepthSampler;

in highp vec3 varying_TEXCOORD0;
in highp vec2 varying_TEXCOORD1;
layout(location = 0) out highp vec4 out_var_SV_Target0;
layout(location = 1) out highp vec4 out_var_SV_Target1;

void main()
{
    highp vec2 _53 = varying_TEXCOORD0.xy / vec2(varying_TEXCOORD0.z);
    highp float _54 = _53.x;
    highp vec4 _64 = texture(SPIRV_Cross_CombinedsceneDepthTexturesceneDepthSampler, vec2((_54 * 0.5) + 0.5, (_53.y * 0.5) + 0.5));
    highp float _65 = _64.x;
    highp vec4 _97 = vec4(u_EveryFrameFrag.u_colour.xyz, 0.0);
    highp vec4 _101 = vec4(_54, _53.y, (mix(_65, (u_cameraPlaneParams.s_CameraFarPlane / (u_cameraPlaneParams.s_CameraFarPlane - u_cameraPlaneParams.s_CameraNearPlane)) + (((u_cameraPlaneParams.s_CameraFarPlane * u_cameraPlaneParams.s_CameraNearPlane) / (u_cameraPlaneParams.s_CameraNearPlane - u_cameraPlaneParams.s_CameraFarPlane)) / (pow(2.0, _65 * log2(u_cameraPlaneParams.s_CameraFarPlane + 1.0)) - 1.0)), u_EveryFrameFrag.u_params.y) * (u_cameraPlaneParams.u_clipZFar - u_cameraPlaneParams.u_clipZNear)) + u_cameraPlaneParams.u_clipZNear, 1.0) * u_EveryFrameFrag.u_inverseMatrix;
    highp vec4 _104 = _101 / vec4(_101.w);
    highp float _108 = _104.x;
    highp float _113 = _104.y;
    highp float _118 = _104.z;
    highp vec4 _128;
    if (((((((u_EveryFrameFrag.u_params.x == 1.0) && (_108 >= (-1.0))) && (_108 <= 1.0)) && (_113 >= (-1.0))) && (_113 <= 1.0)) && (_118 >= (-1.0))) && (_118 <= 1.0))
    {
        highp vec4 _127 = _97;
        _127.w = u_EveryFrameFrag.u_colour.w;
        _128 = _127;
    }
    else
    {
        _128 = _97;
    }
    out_var_SV_Target0 = _128;
    out_var_SV_Target1 = vec4(0.0, ((step(0.0, 0.0) * 2.0) - 1.0) * _65, 0.0, 0.0);
    gl_FragDepth = _65;
}


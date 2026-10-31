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
    highp vec4 u_colour;
    highp vec4 u_params;
} u_EveryFrameFrag;

uniform highp sampler2D SPIRV_Cross_CombinedgridTexturegridSampler;

in highp vec2 varying_TEXCOORD0;
in highp vec2 varying_TEXCOORD1;
layout(location = 0) out highp vec4 out_var_SV_Target0;
layout(location = 1) out highp vec4 out_var_SV_Target1;

void main()
{
    highp vec4 _57 = texture(SPIRV_Cross_CombinedgridTexturegridSampler, vec2(varying_TEXCOORD0.x * 10.0, varying_TEXCOORD0.y * 5.0));
    highp vec4 _60 = _57 * u_EveryFrameFrag.u_colour;
    highp float _77;
    do
    {
        if (varying_TEXCOORD1.y != 0.0)
        {
            _77 = varying_TEXCOORD1.x / varying_TEXCOORD1.y;
            break;
        }
        _77 = log2(varying_TEXCOORD1.x) * (1.0 / log2(u_cameraPlaneParams.s_CameraFarPlane + 1.0));
        break;
    } while(false);
    highp float _82 = u_cameraPlaneParams.s_CameraFarPlane - u_cameraPlaneParams.s_CameraNearPlane;
    out_var_SV_Target0 = vec4(_60.xyz, mix(_60.w, 0.0, clamp(((((u_cameraPlaneParams.u_clipZFar - u_cameraPlaneParams.u_clipZNear) * u_cameraPlaneParams.s_CameraNearPlane) / ((u_cameraPlaneParams.s_CameraFarPlane + u_cameraPlaneParams.s_CameraNearPlane) - (((u_cameraPlaneParams.s_CameraFarPlane / _82) + (((u_cameraPlaneParams.s_CameraFarPlane * u_cameraPlaneParams.s_CameraNearPlane) / (u_cameraPlaneParams.s_CameraNearPlane - u_cameraPlaneParams.s_CameraFarPlane)) / (pow(2.0, _77 * log2(u_cameraPlaneParams.s_CameraFarPlane + 1.0)) - 1.0))) * _82))) * u_cameraPlaneParams.s_CameraFarPlane) / u_EveryFrameFrag.u_params.x, 0.0, 1.0)));
    out_var_SV_Target1 = vec4(0.0, ((step(0.0, 0.0) * 2.0) - 1.0) * _77, 0.0, 0.0);
    gl_FragDepth = _77;
}


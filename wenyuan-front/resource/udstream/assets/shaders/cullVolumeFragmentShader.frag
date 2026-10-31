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
    layout(row_major) highp mat4 u_inverseModelViewProjectionMatrix;
    highp vec4 u_colour;
    highp vec4 u_params;
} u_EveryFrameFrag;

uniform highp sampler2D SPIRV_Cross_CombinedsceneColourTexturesceneColourSampler;
uniform highp sampler2D SPIRV_Cross_CombinedsceneDepthTexturesceneDepthSampler;

in highp vec4 varying_TEXCOORD0;
in highp vec2 varying_TEXCOORD1;
in highp vec2 varying_TEXCOORD2;
layout(location = 0) out highp vec4 out_var_SV_Target0;

void main()
{
    highp vec4 _143;
    do
    {
        highp vec2 _61 = varying_TEXCOORD0.xy / vec2(varying_TEXCOORD0.w);
        highp float _62 = _61.x;
        highp vec2 _68 = vec2((_62 * 0.5) + 0.5, (_61.y * 0.5) + 0.5);
        highp vec4 _72 = texture(SPIRV_Cross_CombinedsceneColourTexturesceneColourSampler, _68);
        highp vec4 _76 = texture(SPIRV_Cross_CombinedsceneDepthTexturesceneDepthSampler, _68);
        highp float _77 = _76.x;
        highp float _103 = (mix(_77, (u_cameraPlaneParams.s_CameraFarPlane / (u_cameraPlaneParams.s_CameraFarPlane - u_cameraPlaneParams.s_CameraNearPlane)) + (((u_cameraPlaneParams.s_CameraFarPlane * u_cameraPlaneParams.s_CameraNearPlane) / (u_cameraPlaneParams.s_CameraNearPlane - u_cameraPlaneParams.s_CameraFarPlane)) / (pow(2.0, _77 * log2(u_cameraPlaneParams.s_CameraFarPlane + 1.0)) - 1.0)), u_EveryFrameFrag.u_params.y) * (u_cameraPlaneParams.u_clipZFar - u_cameraPlaneParams.u_clipZNear)) + u_cameraPlaneParams.u_clipZNear;
        highp vec4 _107 = vec4(_62, _61.y, _103, 1.0) * u_EveryFrameFrag.u_inverseModelViewProjectionMatrix;
        highp vec4 _110 = _107 / vec4(_107.w);
        highp float _111 = _110.x;
        highp float _115 = _110.y;
        highp float _120 = _110.z;
        if ((((((_111 < (-1.0)) || (_111 > 1.0)) || (_115 < (-1.0))) || (_115 > 1.0)) || (_120 < (-1.0))) || (_120 > 1.0))
        {
            if (_103 < (varying_TEXCOORD0.z / varying_TEXCOORD0.w))
            {
                _143 = vec4(0.0199999995529651641845703125, 0.100000001490116119384765625, 0.5, 1.0);
                break;
            }
            discard;
        }
        bvec4 _141 = bvec4(((((_111 < (-0.980000019073486328125)) || (_111 > 0.980000019073486328125)) || (_115 < (-0.980000019073486328125))) || (_115 > 0.980000019073486328125)) || (_120 > 0.980000019073486328125));
        _143 = vec4(_141.x ? vec4(0.100000001490116119384765625, 0.5, 0.699999988079071044921875, 1.0).x : _72.x, _141.y ? vec4(0.100000001490116119384765625, 0.5, 0.699999988079071044921875, 1.0).y : _72.y, _141.z ? vec4(0.100000001490116119384765625, 0.5, 0.699999988079071044921875, 1.0).z : _72.z, _141.w ? vec4(0.100000001490116119384765625, 0.5, 0.699999988079071044921875, 1.0).w : _72.w);
        break;
    } while(false);
    out_var_SV_Target0 = _143;
}


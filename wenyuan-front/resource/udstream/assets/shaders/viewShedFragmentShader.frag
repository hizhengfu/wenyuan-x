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

layout(std140) uniform type_u_params
{
    layout(row_major) highp mat4 u_shadowMapVP[3];
    layout(row_major) highp mat4 u_inverseProjection;
    highp vec4 u_visibleColour;
    highp vec4 u_notVisibleColour;
    highp vec4 u_viewDistance;
} u_params;

uniform highp sampler2D SPIRV_Cross_CombinedsceneDepthTexturesceneDepthSampler;
uniform highp sampler2D SPIRV_Cross_CombinedshadowMapAtlasTextureshadowMapAtlasSampler;

in highp vec4 varying_TEXCOORD0;
in highp vec2 varying_TEXCOORD1;
layout(location = 0) out highp vec4 out_var_SV_Target0;
layout(location = 1) out highp vec4 out_var_SV_Target1;

void main()
{
    highp vec4 _64 = texture(SPIRV_Cross_CombinedsceneDepthTexturesceneDepthSampler, varying_TEXCOORD1);
    highp float _65 = _64.x;
    highp float _70 = u_cameraPlaneParams.s_CameraFarPlane - u_cameraPlaneParams.s_CameraNearPlane;
    highp float _76 = log2(u_cameraPlaneParams.s_CameraFarPlane + 1.0);
    highp float _89 = u_cameraPlaneParams.u_clipZFar - u_cameraPlaneParams.u_clipZNear;
    highp vec4 _97 = vec4(varying_TEXCOORD0.xy, (mix(_65, (u_cameraPlaneParams.s_CameraFarPlane / _70) + (((u_cameraPlaneParams.s_CameraFarPlane * u_cameraPlaneParams.s_CameraNearPlane) / (u_cameraPlaneParams.s_CameraNearPlane - u_cameraPlaneParams.s_CameraFarPlane)) / (pow(2.0, _65 * _76) - 1.0)), u_params.u_viewDistance.y) * _89) + u_cameraPlaneParams.u_clipZNear, 1.0) * u_params.u_inverseProjection;
    highp vec4 _106 = vec4((_97 / vec4(_97.w)).xyz, 1.0);
    highp vec4 _107 = _106 * u_params.u_shadowMapVP[0];
    highp vec4 _110 = _106 * u_params.u_shadowMapVP[1];
    highp vec4 _113 = _106 * u_params.u_shadowMapVP[2];
    highp float _115 = _107.w;
    highp vec3 _119 = ((_107.xyz / vec3(_115)) * vec3(0.5, 0.5, 1.0)) + vec3(0.5, 0.5, 0.0);
    highp float _121 = _110.w;
    highp vec3 _125 = ((_110.xyz / vec3(_121)) * vec3(0.5, 0.5, 1.0)) + vec3(0.5, 0.5, 0.0);
    highp float _127 = _113.w;
    highp vec3 _131 = ((_113.xyz / vec3(_127)) * vec3(0.5, 0.5, 1.0)) + vec3(0.5, 0.5, 0.0);
    highp float _132 = _119.x;
    highp float _136 = _119.y;
    highp float _141 = _119.z;
    highp float _146 = float((((((_132 >= 0.0) && (_132 <= 1.0)) && (_136 >= 0.0)) && (_136 <= 1.0)) && (_141 >= 0.0)) && (_141 <= 1.0));
    highp float _147 = _125.x;
    highp float _151 = _125.y;
    highp float _156 = _125.z;
    highp float _161 = float((((((_147 >= 0.0) && (_147 <= 1.0)) && (_151 >= 0.0)) && (_151 <= 1.0)) && (_156 >= 0.0)) && (_156 <= 1.0));
    highp float _162 = _131.x;
    highp float _166 = _131.y;
    highp float _171 = _131.z;
    highp float _176 = float((((((_162 >= 0.0) && (_162 <= 1.0)) && (_166 >= 0.0)) && (_166 <= 1.0)) && (_171 >= 0.0)) && (_171 <= 1.0));
    highp vec4 _193 = mix(mix(mix(vec4(0.0), vec4(_132 * 0.3333333432674407958984375, 1.0 - _136, _141, _115), vec4(_146)), vec4(0.3333333432674407958984375 + (_147 * 0.3333333432674407958984375), 1.0 - _151, _156, _121), vec4(_161)), vec4(0.666666686534881591796875 + (_162 * 0.3333333432674407958984375), 1.0 - _166, _171, _127), vec4(_176));
    highp float _210 = (mix(mix(mix(0.0, _107.x, _146), _110.x, _161), _113.x, _176) * 1.73205077648162841796875) / u_params.u_viewDistance.x;
    highp float _211 = (((_89 * u_cameraPlaneParams.s_CameraNearPlane) / ((u_cameraPlaneParams.s_CameraFarPlane + u_cameraPlaneParams.s_CameraNearPlane) - (_193.z * _70))) * u_cameraPlaneParams.s_CameraFarPlane) / u_params.u_viewDistance.x;
    highp vec4 _244;
    if ((length(_193.xyz) > 0.0) && ((((_210 * _210) * 4.0) + (_211 * _211)) <= 1.0))
    {
        _244 = mix(u_params.u_visibleColour, u_params.u_notVisibleColour, vec4(clamp((3.9999998989515006542205810546875e-05 * u_cameraPlaneParams.s_CameraFarPlane) * ((log2(1.0 + _193.w) * (1.0 / _76)) - texture(SPIRV_Cross_CombinedshadowMapAtlasTextureshadowMapAtlasSampler, _193.xy).x), 0.0, 1.0)));
    }
    else
    {
        _244 = vec4(0.0);
    }
    out_var_SV_Target0 = vec4(_244.xyz * _244.w, 1.0);
    out_var_SV_Target1 = vec4(0.0);
}


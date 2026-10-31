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

uniform highp sampler2D SPIRV_Cross_CombinedvoxelColoursTexturevoxelColoursSampler;

in highp vec2 varying_TEXCOORD0;
flat in highp vec2 varying_TEXCOORD1;
in highp float varying_TEXCOORD2;
in highp vec4 varying_TEXCOORD3;
layout(location = 0) out highp vec4 out_var_SV_Target0;
layout(location = 1) out highp vec4 out_var_SV_Target1;

void main()
{
    highp vec4 _45 = texture(SPIRV_Cross_CombinedvoxelColoursTexturevoxelColoursSampler, varying_TEXCOORD1);
    out_var_SV_Target0 = vec4(mix(_45.zyx, varying_TEXCOORD3.xyz, vec3(varying_TEXCOORD3.w)), _45.w);
    out_var_SV_Target1 = vec4(varying_TEXCOORD2, (varying_TEXCOORD0.x / (u_cameraPlaneParams.u_clipZFar - u_cameraPlaneParams.u_clipZNear)) - (0.5 * u_cameraPlaneParams.u_clipZNear), 0.0, 0.0);
}


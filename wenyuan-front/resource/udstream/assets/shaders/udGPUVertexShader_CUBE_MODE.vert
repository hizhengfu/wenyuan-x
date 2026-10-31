#version 300 es

layout(std140) uniform type_u_cameraPlaneParams
{
    float s_CameraNearPlane;
    float s_CameraFarPlane;
    float u_clipZNear;
    float u_clipZFar;
} u_cameraPlaneParams;

layout(std140) uniform type_u_vertParams
{
    layout(row_major) mat4 u_proj;
    layout(row_major) mat4 u_worldView;
    layout(row_major) mat4 u_worldViewProj;
    vec4 u_voxelSize;
    vec4 u_screenSize;
    vec4 u_eyeBS;
    vec4 u_colourOverride;
} u_vertParams;

uniform highp sampler2D SPIRV_Cross_CombinedvoxelPositionsTexturevoxelPositionsSampler;

layout(location = 0) in vec2 in_var_TEXCOORD0;
out vec2 varying_TEXCOORD0;
flat out vec2 varying_TEXCOORD1;
out float varying_TEXCOORD2;
out vec4 varying_TEXCOORD3;

void main()
{
    int _60 = int(in_var_TEXCOORD0.x * 65535.0);
    int _63 = int(in_var_TEXCOORD0.y * 65535.0);
    float _73 = float(_63 & 16383) * 6.10388815402984619140625e-05;
    vec2 _79 = vec2(float(_60 & 16383) * 6.10388815402984619140625e-05, _73);
    _79.y = _73 * u_vertParams.u_eyeBS.w;
    vec4 _83 = textureLod(SPIRV_Cross_CombinedvoxelPositionsTexturevoxelPositionsSampler, _79, 0.0);
    vec3 _84 = _83.xyz;
    vec3 _92 = _84 - u_vertParams.u_eyeBS.xyz;
    vec4 _133 = vec4(_84 + (vec3(1.0 + ((u_vertParams.u_voxelSize.w * ((float(_60 & 32768) * 6.103515625e-05) - 1.0)) * ((step(0.0, _92.x) * 2.0) - 1.0)), 1.0 + ((u_vertParams.u_voxelSize.w * ((float(_63 & 32768) * 6.103515625e-05) - 1.0)) * ((step(_92.y, 0.0) * 2.0) - 1.0)), 1.0 + ((u_vertParams.u_voxelSize.w * ((float(_63 & 16384) * 0.0001220703125) - 1.0)) * ((step(0.0, _92.z) * 2.0) - 1.0))) * (u_vertParams.u_voxelSize.xyz * _83.w)), 1.0) * u_vertParams.u_worldViewProj;
    float _144 = _133.w;
    float _149 = (log2(max(9.9999999747524270787835121154785e-07, 1.0 + _144)) * ((u_cameraPlaneParams.u_clipZFar - u_cameraPlaneParams.u_clipZNear) / log2(u_cameraPlaneParams.s_CameraFarPlane + 1.0))) + u_cameraPlaneParams.u_clipZNear;
    vec4 _151 = _133;
    _151.z = _149 * _144;
    gl_Position = _151;
    varying_TEXCOORD0 = vec2(_149, 0.0);
    varying_TEXCOORD1 = _79;
    varying_TEXCOORD2 = u_vertParams.u_screenSize.w;
    varying_TEXCOORD3 = u_vertParams.u_colourOverride;
}


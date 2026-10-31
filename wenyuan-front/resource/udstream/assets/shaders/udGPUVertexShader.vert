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
    int _63 = int(in_var_TEXCOORD0.x * 65535.0);
    int _66 = int(in_var_TEXCOORD0.y * 65535.0);
    float _76 = float(_66 & 16383) * 6.10388815402984619140625e-05;
    vec2 _82 = vec2(float(_63 & 16383) * 6.10388815402984619140625e-05, _76);
    _82.y = _76 * u_vertParams.u_eyeBS.w;
    vec4 _86 = textureLod(SPIRV_Cross_CombinedvoxelPositionsTexturevoxelPositionsSampler, _82, 0.0);
    vec3 _87 = _86.xyz;
    float _91 = _86.w;
    vec4 _103 = vec4(_87 + (u_vertParams.u_voxelSize.xyz * _91), 1.0) * u_vertParams.u_worldView;
    vec3 _104 = normalize(_87 - u_vertParams.u_eyeBS.xyz);
    float _130 = 1.0 + (0.4000000059604644775390625 * (1.0 - pow(normalize(_103.xyz).y, 10.0)));
    vec2 _143 = _103.xz + ((((vec2((float(_63 & 32768) * 6.103515625e-05) - 1.0, (float(_66 & 32768) * 6.103515625e-05) - 1.0) * vec2(mix(1.0, 1.40999996662139892578125, 1.0 - abs((2.0 * abs(_104.x)) - 1.0)) * _130, mix(1.0, 1.40999996662139892578125, 1.0 - abs((2.0 * abs(_104.z)) - 1.0)) * _130)) * _91) * u_vertParams.u_screenSize.xy) * u_vertParams.u_voxelSize.w);
    vec4 _147 = vec4(_143.x, _103.y, _143.y, _103.w) * u_vertParams.u_proj;
    float _158 = _147.w;
    float _163 = (log2(max(9.9999999747524270787835121154785e-07, 1.0 + _158)) * ((u_cameraPlaneParams.u_clipZFar - u_cameraPlaneParams.u_clipZNear) / log2(u_cameraPlaneParams.s_CameraFarPlane + 1.0))) + u_cameraPlaneParams.u_clipZNear;
    vec4 _165 = _147;
    _165.z = _163 * _158;
    gl_Position = _165;
    varying_TEXCOORD0 = vec2(_163, 0.0);
    varying_TEXCOORD1 = _82;
    varying_TEXCOORD2 = u_vertParams.u_screenSize.w;
    varying_TEXCOORD3 = u_vertParams.u_colourOverride;
}


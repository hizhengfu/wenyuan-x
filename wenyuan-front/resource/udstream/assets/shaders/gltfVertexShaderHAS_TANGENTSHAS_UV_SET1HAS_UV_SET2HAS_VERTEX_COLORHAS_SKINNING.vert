#version 300 es

layout(std140) uniform type_u_EveryFrame
{
    layout(row_major) mat4 u_ModelViewProjectionMatrix;
    layout(row_major) mat4 u_ModelMatrix;
    layout(row_major) mat4 u_ModelViewMatrix;
    layout(row_major) mat4 u_NormalMatrix;
    vec4 u_objectInfo;
} u_EveryFrame;

layout(std140) uniform type_u_SkinningInfo
{
    layout(row_major) mat4 u_jointMatrix[96];
    layout(row_major) mat4 u_jointNormalMatrix[96];
} u_SkinningInfo;

layout(location = 0) in vec3 in_var_POSITION;
layout(location = 1) in vec3 in_var_NORMAL;
layout(location = 2) in vec4 in_var_TANGENT;
layout(location = 3) in vec2 in_var_TEXCOORD0;
layout(location = 4) in vec2 in_var_TEXCOORD1;
layout(location = 5) in vec4 in_var_COLOR0;
layout(location = 6) in vec4 in_var_BLENDINDICES0;
layout(location = 7) in vec4 in_var_BLENDWEIGHT0;
out vec3 varying_POSITION0;
out vec3 varying_POSITION1;
out mat3 varying_NORMAL;
out vec2 varying_TEXCOORD0;
out vec2 varying_TEXCOORD1;
out vec2 varying_TEXCOORD2;
out vec2 varying_TEXCOORD3;
out mat4 varying_TEXCOORD4;
out vec4 varying_COLOR0;

void main()
{
    int _79 = int(in_var_BLENDINDICES0.x * 256.0);
    mat4 _83 = u_SkinningInfo.u_jointMatrix[_79] * in_var_BLENDWEIGHT0.x;
    int _86 = int(in_var_BLENDINDICES0.y * 256.0);
    mat4 _90 = u_SkinningInfo.u_jointMatrix[_86] * in_var_BLENDWEIGHT0.y;
    int _105 = int(in_var_BLENDINDICES0.z * 256.0);
    mat4 _109 = u_SkinningInfo.u_jointMatrix[_105] * in_var_BLENDWEIGHT0.z;
    int _120 = int(in_var_BLENDINDICES0.w * 256.0);
    mat4 _124 = u_SkinningInfo.u_jointMatrix[_120] * in_var_BLENDWEIGHT0.w;
    mat4 _133 = mat4(((_83[0] + _90[0]) + _109[0]) + _124[0], ((_83[1] + _90[1]) + _109[1]) + _124[1], ((_83[2] + _90[2]) + _109[2]) + _124[2], ((_83[3] + _90[3]) + _109[3]) + _124[3]);
    vec4 _134 = vec4(in_var_POSITION, 1.0) * _133;
    vec4 _135 = _134 * u_EveryFrame.u_ModelMatrix;
    vec4 _142 = _134 * u_EveryFrame.u_ModelViewMatrix;
    mat4 _151 = u_SkinningInfo.u_jointNormalMatrix[_79] * in_var_BLENDWEIGHT0.x;
    mat4 _154 = u_SkinningInfo.u_jointNormalMatrix[_86] * in_var_BLENDWEIGHT0.y;
    mat4 _169 = u_SkinningInfo.u_jointNormalMatrix[_105] * in_var_BLENDWEIGHT0.z;
    mat4 _180 = u_SkinningInfo.u_jointNormalMatrix[_120] * in_var_BLENDWEIGHT0.w;
    vec3 _203 = normalize((vec4(normalize((vec4(in_var_NORMAL, 0.0) * mat4(((_151[0] + _154[0]) + _169[0]) + _180[0], ((_151[1] + _154[1]) + _169[1]) + _180[1], ((_151[2] + _154[2]) + _169[2]) + _180[2], ((_151[3] + _154[3]) + _169[3]) + _180[3])).xyz), 0.0) * u_EveryFrame.u_NormalMatrix).xyz);
    vec3 _217 = normalize((vec4(normalize((vec4(in_var_TANGENT.xyz, 0.0) * _133).xyz), 0.0) * u_EveryFrame.u_NormalMatrix).xyz);
    vec4 _224 = _134 * u_EveryFrame.u_ModelViewProjectionMatrix;
    vec2 _238;
    do
    {
        if (u_EveryFrame.u_ModelViewProjectionMatrix[3u].y == 0.0)
        {
            _238 = vec2(_224.zw);
            break;
        }
        _238 = vec2(1.0 + _224.w, 0.0);
        break;
    } while(false);
    gl_Position = _224;
    varying_POSITION0 = _135.xyz / vec3(_135.w);
    varying_POSITION1 = _142.xyz / vec3(_142.w);
    varying_NORMAL = mat3(_217, cross(_203, _217) * in_var_TANGENT.w, _203);
    varying_TEXCOORD0 = in_var_TEXCOORD0;
    varying_TEXCOORD1 = in_var_TEXCOORD1;
    varying_TEXCOORD2 = _238;
    varying_TEXCOORD3 = u_EveryFrame.u_objectInfo.xy;
    varying_COLOR0 = in_var_COLOR0;
}


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
layout(location = 3) in vec4 in_var_BLENDINDICES0;
layout(location = 4) in vec4 in_var_BLENDWEIGHT0;
out vec3 varying_POSITION0;
out vec3 varying_POSITION1;
out mat3 varying_NORMAL;
out vec2 varying_TEXCOORD0;
out vec2 varying_TEXCOORD1;
out vec2 varying_TEXCOORD2;
out vec2 varying_TEXCOORD3;
out mat4 varying_TEXCOORD4;

void main()
{
    int _72 = int(in_var_BLENDINDICES0.x * 256.0);
    mat4 _76 = u_SkinningInfo.u_jointMatrix[_72] * in_var_BLENDWEIGHT0.x;
    int _79 = int(in_var_BLENDINDICES0.y * 256.0);
    mat4 _83 = u_SkinningInfo.u_jointMatrix[_79] * in_var_BLENDWEIGHT0.y;
    int _98 = int(in_var_BLENDINDICES0.z * 256.0);
    mat4 _102 = u_SkinningInfo.u_jointMatrix[_98] * in_var_BLENDWEIGHT0.z;
    int _113 = int(in_var_BLENDINDICES0.w * 256.0);
    mat4 _117 = u_SkinningInfo.u_jointMatrix[_113] * in_var_BLENDWEIGHT0.w;
    mat4 _126 = mat4(((_76[0] + _83[0]) + _102[0]) + _117[0], ((_76[1] + _83[1]) + _102[1]) + _117[1], ((_76[2] + _83[2]) + _102[2]) + _117[2], ((_76[3] + _83[3]) + _102[3]) + _117[3]);
    vec4 _127 = vec4(in_var_POSITION, 1.0) * _126;
    vec4 _128 = _127 * u_EveryFrame.u_ModelMatrix;
    vec4 _135 = _127 * u_EveryFrame.u_ModelViewMatrix;
    mat4 _144 = u_SkinningInfo.u_jointNormalMatrix[_72] * in_var_BLENDWEIGHT0.x;
    mat4 _147 = u_SkinningInfo.u_jointNormalMatrix[_79] * in_var_BLENDWEIGHT0.y;
    mat4 _162 = u_SkinningInfo.u_jointNormalMatrix[_98] * in_var_BLENDWEIGHT0.z;
    mat4 _173 = u_SkinningInfo.u_jointNormalMatrix[_113] * in_var_BLENDWEIGHT0.w;
    vec3 _196 = normalize((vec4(normalize((vec4(in_var_NORMAL, 0.0) * mat4(((_144[0] + _147[0]) + _162[0]) + _173[0], ((_144[1] + _147[1]) + _162[1]) + _173[1], ((_144[2] + _147[2]) + _162[2]) + _173[2], ((_144[3] + _147[3]) + _162[3]) + _173[3])).xyz), 0.0) * u_EveryFrame.u_NormalMatrix).xyz);
    vec3 _210 = normalize((vec4(normalize((vec4(in_var_TANGENT.xyz, 0.0) * _126).xyz), 0.0) * u_EveryFrame.u_NormalMatrix).xyz);
    vec4 _217 = _127 * u_EveryFrame.u_ModelViewProjectionMatrix;
    vec2 _231;
    do
    {
        if (u_EveryFrame.u_ModelViewProjectionMatrix[3u].y == 0.0)
        {
            _231 = vec2(_217.zw);
            break;
        }
        _231 = vec2(1.0 + _217.w, 0.0);
        break;
    } while(false);
    gl_Position = _217;
    varying_POSITION0 = _128.xyz / vec3(_128.w);
    varying_POSITION1 = _135.xyz / vec3(_135.w);
    varying_NORMAL = mat3(_210, cross(_196, _210) * in_var_TANGENT.w, _196);
    varying_TEXCOORD0 = vec2(0.0);
    varying_TEXCOORD1 = vec2(0.0);
    varying_TEXCOORD2 = _231;
    varying_TEXCOORD3 = u_EveryFrame.u_objectInfo.xy;
}


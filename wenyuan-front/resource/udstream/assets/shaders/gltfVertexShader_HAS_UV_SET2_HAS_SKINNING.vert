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
layout(location = 2) in vec2 in_var_TEXCOORD1;
layout(location = 3) in vec4 in_var_BLENDINDICES0;
layout(location = 4) in vec4 in_var_BLENDWEIGHT0;
out vec3 varying_POSITION0;
out vec3 varying_POSITION1;
out vec3 varying_NORMAL;
out vec2 varying_TEXCOORD0;
out vec2 varying_TEXCOORD1;
out vec2 varying_TEXCOORD2;
out vec2 varying_TEXCOORD3;
out mat4 varying_TEXCOORD4;

void main()
{
    int _71 = int(in_var_BLENDINDICES0.x * 256.0);
    mat4 _75 = u_SkinningInfo.u_jointMatrix[_71] * in_var_BLENDWEIGHT0.x;
    int _78 = int(in_var_BLENDINDICES0.y * 256.0);
    mat4 _82 = u_SkinningInfo.u_jointMatrix[_78] * in_var_BLENDWEIGHT0.y;
    int _97 = int(in_var_BLENDINDICES0.z * 256.0);
    mat4 _101 = u_SkinningInfo.u_jointMatrix[_97] * in_var_BLENDWEIGHT0.z;
    int _112 = int(in_var_BLENDINDICES0.w * 256.0);
    mat4 _116 = u_SkinningInfo.u_jointMatrix[_112] * in_var_BLENDWEIGHT0.w;
    vec4 _126 = vec4(in_var_POSITION, 1.0) * mat4(((_75[0] + _82[0]) + _101[0]) + _116[0], ((_75[1] + _82[1]) + _101[1]) + _116[1], ((_75[2] + _82[2]) + _101[2]) + _116[2], ((_75[3] + _82[3]) + _101[3]) + _116[3]);
    vec4 _127 = _126 * u_EveryFrame.u_ModelMatrix;
    vec4 _134 = _126 * u_EveryFrame.u_ModelViewMatrix;
    mat4 _143 = u_SkinningInfo.u_jointNormalMatrix[_71] * in_var_BLENDWEIGHT0.x;
    mat4 _146 = u_SkinningInfo.u_jointNormalMatrix[_78] * in_var_BLENDWEIGHT0.y;
    mat4 _161 = u_SkinningInfo.u_jointNormalMatrix[_97] * in_var_BLENDWEIGHT0.z;
    mat4 _172 = u_SkinningInfo.u_jointNormalMatrix[_112] * in_var_BLENDWEIGHT0.w;
    vec4 _198 = _126 * u_EveryFrame.u_ModelViewProjectionMatrix;
    vec2 _212;
    do
    {
        if (u_EveryFrame.u_ModelViewProjectionMatrix[3u].y == 0.0)
        {
            _212 = vec2(_198.zw);
            break;
        }
        _212 = vec2(1.0 + _198.w, 0.0);
        break;
    } while(false);
    gl_Position = _198;
    varying_POSITION0 = _127.xyz / vec3(_127.w);
    varying_POSITION1 = _134.xyz / vec3(_134.w);
    varying_NORMAL = normalize((vec4(normalize((vec4(in_var_NORMAL, 0.0) * mat4(((_143[0] + _146[0]) + _161[0]) + _172[0], ((_143[1] + _146[1]) + _161[1]) + _172[1], ((_143[2] + _146[2]) + _161[2]) + _172[2], ((_143[3] + _146[3]) + _161[3]) + _172[3])).xyz), 0.0) * u_EveryFrame.u_NormalMatrix).xyz);
    varying_TEXCOORD0 = vec2(0.0);
    varying_TEXCOORD1 = in_var_TEXCOORD1;
    varying_TEXCOORD2 = _212;
    varying_TEXCOORD3 = u_EveryFrame.u_objectInfo.xy;
}


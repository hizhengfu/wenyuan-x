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
layout(location = 3) in vec4 in_var_COLOR0;
layout(location = 4) in vec4 in_var_BLENDINDICES0;
layout(location = 5) in vec4 in_var_BLENDWEIGHT0;
out vec3 varying_POSITION0;
out vec3 varying_POSITION1;
out vec3 varying_NORMAL;
out vec2 varying_TEXCOORD0;
out vec2 varying_TEXCOORD1;
out vec2 varying_TEXCOORD2;
out vec2 varying_TEXCOORD3;
out mat4 varying_TEXCOORD4;
out vec4 varying_COLOR0;

void main()
{
    int _74 = int(in_var_BLENDINDICES0.x * 256.0);
    mat4 _78 = u_SkinningInfo.u_jointMatrix[_74] * in_var_BLENDWEIGHT0.x;
    int _81 = int(in_var_BLENDINDICES0.y * 256.0);
    mat4 _85 = u_SkinningInfo.u_jointMatrix[_81] * in_var_BLENDWEIGHT0.y;
    int _100 = int(in_var_BLENDINDICES0.z * 256.0);
    mat4 _104 = u_SkinningInfo.u_jointMatrix[_100] * in_var_BLENDWEIGHT0.z;
    int _115 = int(in_var_BLENDINDICES0.w * 256.0);
    mat4 _119 = u_SkinningInfo.u_jointMatrix[_115] * in_var_BLENDWEIGHT0.w;
    vec4 _129 = vec4(in_var_POSITION, 1.0) * mat4(((_78[0] + _85[0]) + _104[0]) + _119[0], ((_78[1] + _85[1]) + _104[1]) + _119[1], ((_78[2] + _85[2]) + _104[2]) + _119[2], ((_78[3] + _85[3]) + _104[3]) + _119[3]);
    vec4 _130 = _129 * u_EveryFrame.u_ModelMatrix;
    vec4 _137 = _129 * u_EveryFrame.u_ModelViewMatrix;
    mat4 _146 = u_SkinningInfo.u_jointNormalMatrix[_74] * in_var_BLENDWEIGHT0.x;
    mat4 _149 = u_SkinningInfo.u_jointNormalMatrix[_81] * in_var_BLENDWEIGHT0.y;
    mat4 _164 = u_SkinningInfo.u_jointNormalMatrix[_100] * in_var_BLENDWEIGHT0.z;
    mat4 _175 = u_SkinningInfo.u_jointNormalMatrix[_115] * in_var_BLENDWEIGHT0.w;
    vec4 _201 = _129 * u_EveryFrame.u_ModelViewProjectionMatrix;
    vec2 _215;
    do
    {
        if (u_EveryFrame.u_ModelViewProjectionMatrix[3u].y == 0.0)
        {
            _215 = vec2(_201.zw);
            break;
        }
        _215 = vec2(1.0 + _201.w, 0.0);
        break;
    } while(false);
    gl_Position = _201;
    varying_POSITION0 = _130.xyz / vec3(_130.w);
    varying_POSITION1 = _137.xyz / vec3(_137.w);
    varying_NORMAL = normalize((vec4(normalize((vec4(in_var_NORMAL, 0.0) * mat4(((_146[0] + _149[0]) + _164[0]) + _175[0], ((_146[1] + _149[1]) + _164[1]) + _175[1], ((_146[2] + _149[2]) + _164[2]) + _175[2], ((_146[3] + _149[3]) + _164[3]) + _175[3])).xyz), 0.0) * u_EveryFrame.u_NormalMatrix).xyz);
    varying_TEXCOORD0 = vec2(0.0);
    varying_TEXCOORD1 = in_var_TEXCOORD1;
    varying_TEXCOORD2 = _215;
    varying_TEXCOORD3 = u_EveryFrame.u_objectInfo.xy;
    varying_COLOR0 = in_var_COLOR0;
}


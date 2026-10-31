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
layout(location = 5) in vec4 in_var_BLENDINDICES0;
layout(location = 6) in vec4 in_var_BLENDWEIGHT0;
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
    int _76 = int(in_var_BLENDINDICES0.x * 256.0);
    mat4 _80 = u_SkinningInfo.u_jointMatrix[_76] * in_var_BLENDWEIGHT0.x;
    int _83 = int(in_var_BLENDINDICES0.y * 256.0);
    mat4 _87 = u_SkinningInfo.u_jointMatrix[_83] * in_var_BLENDWEIGHT0.y;
    int _102 = int(in_var_BLENDINDICES0.z * 256.0);
    mat4 _106 = u_SkinningInfo.u_jointMatrix[_102] * in_var_BLENDWEIGHT0.z;
    int _117 = int(in_var_BLENDINDICES0.w * 256.0);
    mat4 _121 = u_SkinningInfo.u_jointMatrix[_117] * in_var_BLENDWEIGHT0.w;
    mat4 _130 = mat4(((_80[0] + _87[0]) + _106[0]) + _121[0], ((_80[1] + _87[1]) + _106[1]) + _121[1], ((_80[2] + _87[2]) + _106[2]) + _121[2], ((_80[3] + _87[3]) + _106[3]) + _121[3]);
    vec4 _131 = vec4(in_var_POSITION, 1.0) * _130;
    vec4 _132 = _131 * u_EveryFrame.u_ModelMatrix;
    vec4 _139 = _131 * u_EveryFrame.u_ModelViewMatrix;
    mat4 _148 = u_SkinningInfo.u_jointNormalMatrix[_76] * in_var_BLENDWEIGHT0.x;
    mat4 _151 = u_SkinningInfo.u_jointNormalMatrix[_83] * in_var_BLENDWEIGHT0.y;
    mat4 _166 = u_SkinningInfo.u_jointNormalMatrix[_102] * in_var_BLENDWEIGHT0.z;
    mat4 _177 = u_SkinningInfo.u_jointNormalMatrix[_117] * in_var_BLENDWEIGHT0.w;
    vec3 _200 = normalize((vec4(normalize((vec4(in_var_NORMAL, 0.0) * mat4(((_148[0] + _151[0]) + _166[0]) + _177[0], ((_148[1] + _151[1]) + _166[1]) + _177[1], ((_148[2] + _151[2]) + _166[2]) + _177[2], ((_148[3] + _151[3]) + _166[3]) + _177[3])).xyz), 0.0) * u_EveryFrame.u_NormalMatrix).xyz);
    vec3 _214 = normalize((vec4(normalize((vec4(in_var_TANGENT.xyz, 0.0) * _130).xyz), 0.0) * u_EveryFrame.u_NormalMatrix).xyz);
    vec4 _221 = _131 * u_EveryFrame.u_ModelViewProjectionMatrix;
    vec2 _235;
    do
    {
        if (u_EveryFrame.u_ModelViewProjectionMatrix[3u].y == 0.0)
        {
            _235 = vec2(_221.zw);
            break;
        }
        _235 = vec2(1.0 + _221.w, 0.0);
        break;
    } while(false);
    gl_Position = _221;
    varying_POSITION0 = _132.xyz / vec3(_132.w);
    varying_POSITION1 = _139.xyz / vec3(_139.w);
    varying_NORMAL = mat3(_214, cross(_200, _214) * in_var_TANGENT.w, _200);
    varying_TEXCOORD0 = in_var_TEXCOORD0;
    varying_TEXCOORD1 = in_var_TEXCOORD1;
    varying_TEXCOORD2 = _235;
    varying_TEXCOORD3 = u_EveryFrame.u_objectInfo.xy;
}


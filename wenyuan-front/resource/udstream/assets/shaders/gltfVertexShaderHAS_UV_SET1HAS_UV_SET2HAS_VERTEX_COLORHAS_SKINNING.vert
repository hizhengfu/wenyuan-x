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
layout(location = 2) in vec2 in_var_TEXCOORD0;
layout(location = 3) in vec2 in_var_TEXCOORD1;
layout(location = 4) in vec4 in_var_COLOR0;
layout(location = 5) in vec4 in_var_BLENDINDICES0;
layout(location = 6) in vec4 in_var_BLENDWEIGHT0;
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
    int _75 = int(in_var_BLENDINDICES0.x * 256.0);
    mat4 _79 = u_SkinningInfo.u_jointMatrix[_75] * in_var_BLENDWEIGHT0.x;
    int _82 = int(in_var_BLENDINDICES0.y * 256.0);
    mat4 _86 = u_SkinningInfo.u_jointMatrix[_82] * in_var_BLENDWEIGHT0.y;
    int _101 = int(in_var_BLENDINDICES0.z * 256.0);
    mat4 _105 = u_SkinningInfo.u_jointMatrix[_101] * in_var_BLENDWEIGHT0.z;
    int _116 = int(in_var_BLENDINDICES0.w * 256.0);
    mat4 _120 = u_SkinningInfo.u_jointMatrix[_116] * in_var_BLENDWEIGHT0.w;
    vec4 _130 = vec4(in_var_POSITION, 1.0) * mat4(((_79[0] + _86[0]) + _105[0]) + _120[0], ((_79[1] + _86[1]) + _105[1]) + _120[1], ((_79[2] + _86[2]) + _105[2]) + _120[2], ((_79[3] + _86[3]) + _105[3]) + _120[3]);
    vec4 _131 = _130 * u_EveryFrame.u_ModelMatrix;
    vec4 _138 = _130 * u_EveryFrame.u_ModelViewMatrix;
    mat4 _147 = u_SkinningInfo.u_jointNormalMatrix[_75] * in_var_BLENDWEIGHT0.x;
    mat4 _150 = u_SkinningInfo.u_jointNormalMatrix[_82] * in_var_BLENDWEIGHT0.y;
    mat4 _165 = u_SkinningInfo.u_jointNormalMatrix[_101] * in_var_BLENDWEIGHT0.z;
    mat4 _176 = u_SkinningInfo.u_jointNormalMatrix[_116] * in_var_BLENDWEIGHT0.w;
    vec4 _202 = _130 * u_EveryFrame.u_ModelViewProjectionMatrix;
    vec2 _216;
    do
    {
        if (u_EveryFrame.u_ModelViewProjectionMatrix[3u].y == 0.0)
        {
            _216 = vec2(_202.zw);
            break;
        }
        _216 = vec2(1.0 + _202.w, 0.0);
        break;
    } while(false);
    gl_Position = _202;
    varying_POSITION0 = _131.xyz / vec3(_131.w);
    varying_POSITION1 = _138.xyz / vec3(_138.w);
    varying_NORMAL = normalize((vec4(normalize((vec4(in_var_NORMAL, 0.0) * mat4(((_147[0] + _150[0]) + _165[0]) + _176[0], ((_147[1] + _150[1]) + _165[1]) + _176[1], ((_147[2] + _150[2]) + _165[2]) + _176[2], ((_147[3] + _150[3]) + _165[3]) + _176[3])).xyz), 0.0) * u_EveryFrame.u_NormalMatrix).xyz);
    varying_TEXCOORD0 = in_var_TEXCOORD0;
    varying_TEXCOORD1 = in_var_TEXCOORD1;
    varying_TEXCOORD2 = _216;
    varying_TEXCOORD3 = u_EveryFrame.u_objectInfo.xy;
    varying_COLOR0 = in_var_COLOR0;
}


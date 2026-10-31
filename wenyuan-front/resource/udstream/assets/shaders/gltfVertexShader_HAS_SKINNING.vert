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
layout(location = 2) in vec4 in_var_BLENDINDICES0;
layout(location = 3) in vec4 in_var_BLENDWEIGHT0;
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
    int _68 = int(in_var_BLENDINDICES0.x * 256.0);
    mat4 _72 = u_SkinningInfo.u_jointMatrix[_68] * in_var_BLENDWEIGHT0.x;
    int _75 = int(in_var_BLENDINDICES0.y * 256.0);
    mat4 _79 = u_SkinningInfo.u_jointMatrix[_75] * in_var_BLENDWEIGHT0.y;
    int _94 = int(in_var_BLENDINDICES0.z * 256.0);
    mat4 _98 = u_SkinningInfo.u_jointMatrix[_94] * in_var_BLENDWEIGHT0.z;
    int _109 = int(in_var_BLENDINDICES0.w * 256.0);
    mat4 _113 = u_SkinningInfo.u_jointMatrix[_109] * in_var_BLENDWEIGHT0.w;
    vec4 _123 = vec4(in_var_POSITION, 1.0) * mat4(((_72[0] + _79[0]) + _98[0]) + _113[0], ((_72[1] + _79[1]) + _98[1]) + _113[1], ((_72[2] + _79[2]) + _98[2]) + _113[2], ((_72[3] + _79[3]) + _98[3]) + _113[3]);
    vec4 _124 = _123 * u_EveryFrame.u_ModelMatrix;
    vec4 _131 = _123 * u_EveryFrame.u_ModelViewMatrix;
    mat4 _140 = u_SkinningInfo.u_jointNormalMatrix[_68] * in_var_BLENDWEIGHT0.x;
    mat4 _143 = u_SkinningInfo.u_jointNormalMatrix[_75] * in_var_BLENDWEIGHT0.y;
    mat4 _158 = u_SkinningInfo.u_jointNormalMatrix[_94] * in_var_BLENDWEIGHT0.z;
    mat4 _169 = u_SkinningInfo.u_jointNormalMatrix[_109] * in_var_BLENDWEIGHT0.w;
    vec4 _195 = _123 * u_EveryFrame.u_ModelViewProjectionMatrix;
    vec2 _209;
    do
    {
        if (u_EveryFrame.u_ModelViewProjectionMatrix[3u].y == 0.0)
        {
            _209 = vec2(_195.zw);
            break;
        }
        _209 = vec2(1.0 + _195.w, 0.0);
        break;
    } while(false);
    gl_Position = _195;
    varying_POSITION0 = _124.xyz / vec3(_124.w);
    varying_POSITION1 = _131.xyz / vec3(_131.w);
    varying_NORMAL = normalize((vec4(normalize((vec4(in_var_NORMAL, 0.0) * mat4(((_140[0] + _143[0]) + _158[0]) + _169[0], ((_140[1] + _143[1]) + _158[1]) + _169[1], ((_140[2] + _143[2]) + _158[2]) + _169[2], ((_140[3] + _143[3]) + _158[3]) + _169[3])).xyz), 0.0) * u_EveryFrame.u_NormalMatrix).xyz);
    varying_TEXCOORD0 = vec2(0.0);
    varying_TEXCOORD1 = vec2(0.0);
    varying_TEXCOORD2 = _209;
    varying_TEXCOORD3 = u_EveryFrame.u_objectInfo.xy;
}


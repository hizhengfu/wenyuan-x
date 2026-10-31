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
layout(location = 3) in vec2 in_var_TEXCOORD1;
layout(location = 4) in vec4 in_var_COLOR0;
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
out vec4 varying_COLOR0;

void main()
{
    int _78 = int(in_var_BLENDINDICES0.x * 256.0);
    mat4 _82 = u_SkinningInfo.u_jointMatrix[_78] * in_var_BLENDWEIGHT0.x;
    int _85 = int(in_var_BLENDINDICES0.y * 256.0);
    mat4 _89 = u_SkinningInfo.u_jointMatrix[_85] * in_var_BLENDWEIGHT0.y;
    int _104 = int(in_var_BLENDINDICES0.z * 256.0);
    mat4 _108 = u_SkinningInfo.u_jointMatrix[_104] * in_var_BLENDWEIGHT0.z;
    int _119 = int(in_var_BLENDINDICES0.w * 256.0);
    mat4 _123 = u_SkinningInfo.u_jointMatrix[_119] * in_var_BLENDWEIGHT0.w;
    mat4 _132 = mat4(((_82[0] + _89[0]) + _108[0]) + _123[0], ((_82[1] + _89[1]) + _108[1]) + _123[1], ((_82[2] + _89[2]) + _108[2]) + _123[2], ((_82[3] + _89[3]) + _108[3]) + _123[3]);
    vec4 _133 = vec4(in_var_POSITION, 1.0) * _132;
    vec4 _134 = _133 * u_EveryFrame.u_ModelMatrix;
    vec4 _141 = _133 * u_EveryFrame.u_ModelViewMatrix;
    mat4 _150 = u_SkinningInfo.u_jointNormalMatrix[_78] * in_var_BLENDWEIGHT0.x;
    mat4 _153 = u_SkinningInfo.u_jointNormalMatrix[_85] * in_var_BLENDWEIGHT0.y;
    mat4 _168 = u_SkinningInfo.u_jointNormalMatrix[_104] * in_var_BLENDWEIGHT0.z;
    mat4 _179 = u_SkinningInfo.u_jointNormalMatrix[_119] * in_var_BLENDWEIGHT0.w;
    vec3 _202 = normalize((vec4(normalize((vec4(in_var_NORMAL, 0.0) * mat4(((_150[0] + _153[0]) + _168[0]) + _179[0], ((_150[1] + _153[1]) + _168[1]) + _179[1], ((_150[2] + _153[2]) + _168[2]) + _179[2], ((_150[3] + _153[3]) + _168[3]) + _179[3])).xyz), 0.0) * u_EveryFrame.u_NormalMatrix).xyz);
    vec3 _216 = normalize((vec4(normalize((vec4(in_var_TANGENT.xyz, 0.0) * _132).xyz), 0.0) * u_EveryFrame.u_NormalMatrix).xyz);
    vec4 _223 = _133 * u_EveryFrame.u_ModelViewProjectionMatrix;
    vec2 _237;
    do
    {
        if (u_EveryFrame.u_ModelViewProjectionMatrix[3u].y == 0.0)
        {
            _237 = vec2(_223.zw);
            break;
        }
        _237 = vec2(1.0 + _223.w, 0.0);
        break;
    } while(false);
    gl_Position = _223;
    varying_POSITION0 = _134.xyz / vec3(_134.w);
    varying_POSITION1 = _141.xyz / vec3(_141.w);
    varying_NORMAL = mat3(_216, cross(_202, _216) * in_var_TANGENT.w, _202);
    varying_TEXCOORD0 = vec2(0.0);
    varying_TEXCOORD1 = in_var_TEXCOORD1;
    varying_TEXCOORD2 = _237;
    varying_TEXCOORD3 = u_EveryFrame.u_objectInfo.xy;
    varying_COLOR0 = in_var_COLOR0;
}


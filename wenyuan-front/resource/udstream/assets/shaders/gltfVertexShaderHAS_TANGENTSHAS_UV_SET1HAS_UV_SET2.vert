#version 300 es

layout(std140) uniform type_u_EveryFrame
{
    layout(row_major) mat4 u_ModelViewProjectionMatrix;
    layout(row_major) mat4 u_ModelMatrix;
    layout(row_major) mat4 u_ModelViewMatrix;
    layout(row_major) mat4 u_NormalMatrix;
    vec4 u_objectInfo;
} u_EveryFrame;

layout(location = 0) in vec3 in_var_POSITION;
layout(location = 1) in vec3 in_var_NORMAL;
layout(location = 2) in vec4 in_var_TANGENT;
layout(location = 3) in vec2 in_var_TEXCOORD0;
layout(location = 4) in vec2 in_var_TEXCOORD1;
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
    vec4 _63 = vec4(in_var_POSITION, 1.0);
    vec4 _64 = _63 * u_EveryFrame.u_ModelMatrix;
    vec4 _71 = _63 * u_EveryFrame.u_ModelViewMatrix;
    vec3 _85 = normalize((vec4(normalize(in_var_NORMAL), 0.0) * u_EveryFrame.u_NormalMatrix).xyz);
    vec3 _94 = normalize((vec4(normalize(in_var_TANGENT.xyz), 0.0) * u_EveryFrame.u_NormalMatrix).xyz);
    vec4 _101 = _63 * u_EveryFrame.u_ModelViewProjectionMatrix;
    vec2 _115;
    do
    {
        if (u_EveryFrame.u_ModelViewProjectionMatrix[3u].y == 0.0)
        {
            _115 = vec2(_101.zw);
            break;
        }
        _115 = vec2(1.0 + _101.w, 0.0);
        break;
    } while(false);
    gl_Position = _101;
    varying_POSITION0 = _64.xyz / vec3(_64.w);
    varying_POSITION1 = _71.xyz / vec3(_71.w);
    varying_NORMAL = mat3(_94, cross(_85, _94) * in_var_TANGENT.w, _85);
    varying_TEXCOORD0 = in_var_TEXCOORD0;
    varying_TEXCOORD1 = in_var_TEXCOORD1;
    varying_TEXCOORD2 = _115;
    varying_TEXCOORD3 = u_EveryFrame.u_objectInfo.xy;
}


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
layout(location = 5) in vec4 in_var_COLOR0;
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
    vec4 _66 = vec4(in_var_POSITION, 1.0);
    vec4 _67 = _66 * u_EveryFrame.u_ModelMatrix;
    vec4 _74 = _66 * u_EveryFrame.u_ModelViewMatrix;
    vec3 _88 = normalize((vec4(normalize(in_var_NORMAL), 0.0) * u_EveryFrame.u_NormalMatrix).xyz);
    vec3 _97 = normalize((vec4(normalize(in_var_TANGENT.xyz), 0.0) * u_EveryFrame.u_NormalMatrix).xyz);
    vec4 _104 = _66 * u_EveryFrame.u_ModelViewProjectionMatrix;
    vec2 _118;
    do
    {
        if (u_EveryFrame.u_ModelViewProjectionMatrix[3u].y == 0.0)
        {
            _118 = vec2(_104.zw);
            break;
        }
        _118 = vec2(1.0 + _104.w, 0.0);
        break;
    } while(false);
    gl_Position = _104;
    varying_POSITION0 = _67.xyz / vec3(_67.w);
    varying_POSITION1 = _74.xyz / vec3(_74.w);
    varying_NORMAL = mat3(_97, cross(_88, _97) * in_var_TANGENT.w, _88);
    varying_TEXCOORD0 = in_var_TEXCOORD0;
    varying_TEXCOORD1 = in_var_TEXCOORD1;
    varying_TEXCOORD2 = _118;
    varying_TEXCOORD3 = u_EveryFrame.u_objectInfo.xy;
    varying_COLOR0 = in_var_COLOR0;
}


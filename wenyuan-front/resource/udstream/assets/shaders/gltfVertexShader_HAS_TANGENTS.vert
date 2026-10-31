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
    vec4 _59 = vec4(in_var_POSITION, 1.0);
    vec4 _60 = _59 * u_EveryFrame.u_ModelMatrix;
    vec4 _67 = _59 * u_EveryFrame.u_ModelViewMatrix;
    vec3 _81 = normalize((vec4(normalize(in_var_NORMAL), 0.0) * u_EveryFrame.u_NormalMatrix).xyz);
    vec3 _90 = normalize((vec4(normalize(in_var_TANGENT.xyz), 0.0) * u_EveryFrame.u_NormalMatrix).xyz);
    vec4 _97 = _59 * u_EveryFrame.u_ModelViewProjectionMatrix;
    vec2 _111;
    do
    {
        if (u_EveryFrame.u_ModelViewProjectionMatrix[3u].y == 0.0)
        {
            _111 = vec2(_97.zw);
            break;
        }
        _111 = vec2(1.0 + _97.w, 0.0);
        break;
    } while(false);
    gl_Position = _97;
    varying_POSITION0 = _60.xyz / vec3(_60.w);
    varying_POSITION1 = _67.xyz / vec3(_67.w);
    varying_NORMAL = mat3(_90, cross(_81, _90) * in_var_TANGENT.w, _81);
    varying_TEXCOORD0 = vec2(0.0);
    varying_TEXCOORD1 = vec2(0.0);
    varying_TEXCOORD2 = _111;
    varying_TEXCOORD3 = u_EveryFrame.u_objectInfo.xy;
}


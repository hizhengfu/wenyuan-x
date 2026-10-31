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
    vec4 _54 = vec4(in_var_POSITION, 1.0);
    vec4 _55 = _54 * u_EveryFrame.u_ModelMatrix;
    vec4 _62 = _54 * u_EveryFrame.u_ModelViewMatrix;
    vec4 _79 = _54 * u_EveryFrame.u_ModelViewProjectionMatrix;
    vec2 _93;
    do
    {
        if (u_EveryFrame.u_ModelViewProjectionMatrix[3u].y == 0.0)
        {
            _93 = vec2(_79.zw);
            break;
        }
        _93 = vec2(1.0 + _79.w, 0.0);
        break;
    } while(false);
    gl_Position = _79;
    varying_POSITION0 = _55.xyz / vec3(_55.w);
    varying_POSITION1 = _62.xyz / vec3(_62.w);
    varying_NORMAL = normalize((vec4(normalize(in_var_NORMAL), 0.0) * u_EveryFrame.u_NormalMatrix).xyz);
    varying_TEXCOORD0 = vec2(0.0);
    varying_TEXCOORD1 = vec2(0.0);
    varying_TEXCOORD2 = _93;
    varying_TEXCOORD3 = u_EveryFrame.u_objectInfo.xy;
}


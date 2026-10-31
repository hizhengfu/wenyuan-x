#version 300 es

layout(std140) uniform type_u_EveryFrameVert
{
    layout(row_major) mat4 u_worldViewProjectionMatrix;
} u_EveryFrameVert;

layout(location = 0) in vec3 in_var_POSITION;
layout(location = 1) in vec3 in_var_NORMAL;
layout(location = 2) in vec2 in_var_TEXCOORD0;
out vec2 varying_TEXCOORD0;
out vec2 varying_TEXCOORD1;

void main()
{
    vec4 _42 = vec4(in_var_POSITION, 1.0) * u_EveryFrameVert.u_worldViewProjectionMatrix;
    vec2 _56;
    do
    {
        if (u_EveryFrameVert.u_worldViewProjectionMatrix[3u].y == 0.0)
        {
            _56 = vec2(_42.zw);
            break;
        }
        _56 = vec2(1.0 + _42.w, 0.0);
        break;
    } while(false);
    gl_Position = _42;
    varying_TEXCOORD0 = in_var_TEXCOORD0;
    varying_TEXCOORD1 = _56;
}


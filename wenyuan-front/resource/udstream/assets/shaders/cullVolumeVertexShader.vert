#version 300 es

layout(std140) uniform type_u_EveryFrameVert
{
    layout(row_major) mat4 u_modelViewMatrix;
    layout(row_major) mat4 u_modelViewProjectionMatrix;
} u_EveryFrameVert;

layout(location = 0) in vec3 in_var_POSITION;
layout(location = 1) in vec3 in_var_NORMAL;
layout(location = 2) in vec2 in_var_TEXCOORD0;
out vec4 varying_TEXCOORD0;
out vec2 varying_TEXCOORD1;
out vec2 varying_TEXCOORD2;

void main()
{
    vec4 _42 = vec4(in_var_POSITION, 1.0);
    vec4 _43 = _42 * u_EveryFrameVert.u_modelViewProjectionMatrix;
    vec2 _57;
    do
    {
        if (u_EveryFrameVert.u_modelViewProjectionMatrix[3u].y == 0.0)
        {
            _57 = vec2(_43.zw);
            break;
        }
        _57 = vec2(1.0 + _43.w, 0.0);
        break;
    } while(false);
    gl_Position = _43;
    varying_TEXCOORD0 = _43;
    varying_TEXCOORD1 = _57;
    varying_TEXCOORD2 = (_42 * u_EveryFrameVert.u_modelViewMatrix).yx;
}


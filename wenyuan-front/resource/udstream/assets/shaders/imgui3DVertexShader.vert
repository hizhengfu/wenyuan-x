#version 300 es

layout(std140) uniform type_u_EveryObject
{
    layout(row_major) mat4 u_worldViewProjectionMatrix;
    vec4 u_screenSize;
} u_EveryObject;

layout(location = 0) in vec2 in_var_POSITION;
layout(location = 1) in vec2 in_var_TEXCOORD0;
layout(location = 2) in vec4 in_var_COLOR0;
out vec4 varying_COLOR0;
out vec2 varying_TEXCOORD0;
out vec2 varying_TEXCOORD1;
out vec2 varying_TEXCOORD2;

void main()
{
    vec4 _70;
    if (u_EveryObject.u_screenSize.z == 0.0)
    {
        vec4 _53 = vec4(0.0, 0.0, 0.0, 1.0) * u_EveryObject.u_worldViewProjectionMatrix;
        vec2 _60 = _53.xy + ((u_EveryObject.u_screenSize.xy * in_var_POSITION) * _53.w);
        _70 = vec4(_60.x, _60.y, _53.z, _53.w);
    }
    else
    {
        _70 = vec4(in_var_POSITION.x * 0.0625, in_var_POSITION.y * 0.0625, 0.0, 1.0) * u_EveryObject.u_worldViewProjectionMatrix;
    }
    vec2 _84;
    do
    {
        if (u_EveryObject.u_worldViewProjectionMatrix[3u].y == 0.0)
        {
            _84 = vec2(_70.zw);
            break;
        }
        _84 = vec2(1.0 + _70.w, 0.0);
        break;
    } while(false);
    gl_Position = _70;
    varying_COLOR0 = in_var_COLOR0;
    varying_TEXCOORD0 = in_var_TEXCOORD0;
    varying_TEXCOORD1 = _84;
    varying_TEXCOORD2 = vec2(u_EveryObject.u_screenSize.w);
}


#version 300 es

layout(std140) uniform type_u_EveryObject
{
    layout(row_major) mat4 u_worldViewProjectionMatrix;
    layout(row_major) mat4 u_worldMatrix;
    vec4 u_colour;
    vec4 u_objectInfo;
} u_EveryObject;

layout(location = 0) in vec3 in_var_POSITION;
layout(location = 1) in vec3 in_var_NORMAL;
layout(location = 2) in vec2 in_var_TEXCOORD0;
layout(location = 3) in vec4 in_var_COLOR0;
out vec2 varying_TEXCOORD0;
out vec3 varying_NORMAL;
out vec4 varying_COLOR0;
out vec2 varying_TEXCOORD1;
out vec2 varying_TEXCOORD2;

void main()
{
    vec4 _63 = vec4(in_var_POSITION, 1.0) * u_EveryObject.u_worldViewProjectionMatrix;
    vec2 _77;
    do
    {
        if (u_EveryObject.u_worldViewProjectionMatrix[3u].y == 0.0)
        {
            _77 = vec2(_63.zw);
            break;
        }
        _77 = vec2(1.0 + _63.w, 0.0);
        break;
    } while(false);
    gl_Position = _63;
    varying_TEXCOORD0 = in_var_TEXCOORD0;
    varying_NORMAL = normalize((vec4(in_var_NORMAL, 0.0) * u_EveryObject.u_worldMatrix).xyz);
    varying_COLOR0 = u_EveryObject.u_colour;
    varying_TEXCOORD1 = _77;
    varying_TEXCOORD2 = u_EveryObject.u_objectInfo.xy;
}


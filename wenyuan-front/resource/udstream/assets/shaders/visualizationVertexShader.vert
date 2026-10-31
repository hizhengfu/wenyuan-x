#version 300 es

layout(std140) uniform type_u_vertParams
{
    vec4 u_outlineStepSize;
} u_vertParams;

layout(location = 0) in vec3 in_var_POSITION;
layout(location = 1) in vec2 in_var_TEXCOORD0;
out vec4 varying_TEXCOORD0;
out vec3 varying_TEXCOORD1;
out vec2 varying_TEXCOORD2;
out vec2 varying_TEXCOORD3;
out vec2 varying_TEXCOORD4;
out vec2 varying_TEXCOORD5;

void main()
{
    vec4 _37 = vec4(in_var_POSITION.xy, 0.0, 1.0);
    vec3 _43 = vec3(in_var_TEXCOORD0, u_vertParams.u_outlineStepSize.z);
    vec3 _47 = vec3(u_vertParams.u_outlineStepSize.xy, 0.0);
    vec2 _48 = _43.xy;
    vec2 _49 = _47.xz;
    vec2 _52 = _47.zy;
    gl_Position = _37;
    varying_TEXCOORD0 = _37;
    varying_TEXCOORD1 = _43;
    varying_TEXCOORD2 = _48 + _49;
    varying_TEXCOORD3 = _48 - _49;
    varying_TEXCOORD4 = _48 + _52;
    varying_TEXCOORD5 = _48 - _52;
}


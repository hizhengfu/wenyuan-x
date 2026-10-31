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
layout(location = 4) in vec4 in_var_COLOR0;
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
    vec4 _65 = vec4(in_var_POSITION, 1.0);
    vec4 _66 = _65 * u_EveryFrame.u_ModelMatrix;
    vec4 _73 = _65 * u_EveryFrame.u_ModelViewMatrix;
    vec3 _87 = normalize((vec4(normalize(in_var_NORMAL), 0.0) * u_EveryFrame.u_NormalMatrix).xyz);
    vec3 _96 = normalize((vec4(normalize(in_var_TANGENT.xyz), 0.0) * u_EveryFrame.u_NormalMatrix).xyz);
    vec4 _103 = _65 * u_EveryFrame.u_ModelViewProjectionMatrix;
    vec2 _117;
    do
    {
        if (u_EveryFrame.u_ModelViewProjectionMatrix[3u].y == 0.0)
        {
            _117 = vec2(_103.zw);
            break;
        }
        _117 = vec2(1.0 + _103.w, 0.0);
        break;
    } while(false);
    gl_Position = _103;
    varying_POSITION0 = _66.xyz / vec3(_66.w);
    varying_POSITION1 = _73.xyz / vec3(_73.w);
    varying_NORMAL = mat3(_96, cross(_87, _96) * in_var_TANGENT.w, _87);
    varying_TEXCOORD0 = in_var_TEXCOORD0;
    varying_TEXCOORD1 = vec2(0.0);
    varying_TEXCOORD2 = _117;
    varying_TEXCOORD3 = u_EveryFrame.u_objectInfo.xy;
    varying_COLOR0 = in_var_COLOR0;
}


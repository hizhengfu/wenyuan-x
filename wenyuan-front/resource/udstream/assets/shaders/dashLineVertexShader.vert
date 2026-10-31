#version 300 es

layout(std140) uniform type_u_EveryObject
{
    vec4 u_colour;
    vec4 u_thickness;
    vec4 u_nearPlane;
    layout(row_major) mat4 u_worldViewProjectionMatrix;
} u_EveryObject;

layout(location = 0) in vec4 in_var_POSITION;
layout(location = 1) in vec4 in_var_COLOR0;
layout(location = 2) in vec4 in_var_COLOR1;
layout(location = 3) in vec2 in_var_TEXCOORD0;
out vec4 varying_COLOR0;
out vec2 varying_TEXCOORD0;
out vec2 varying_TEXCOORD1;

vec2 _43;
vec4 _44;

void main()
{
    vec4 _167;
    vec4 _168;
    vec2 _169;
    vec2 _170;
    do
    {
        float _59 = dot(in_var_POSITION.xyz, u_EveryObject.u_nearPlane.xyz) - u_EveryObject.u_nearPlane.w;
        float _61 = dot(in_var_COLOR0.xyz, u_EveryObject.u_nearPlane.xyz) - u_EveryObject.u_nearPlane.w;
        int _77;
        if (_59 > 0.0)
        {
            _77 = (_61 >= 0.0) ? 0 : 2;
        }
        else
        {
            int _76;
            if (_59 < 0.0)
            {
                _76 = (_61 <= 0.0) ? 3 : 1;
            }
            else
            {
                _76 = (_61 > 0.0) ? 0 : 3;
            }
            _77 = _76;
        }
        vec3 _98;
        vec3 _99;
        if (_77 == 1)
        {
            _98 = in_var_COLOR0.xyz;
            _99 = in_var_POSITION.xyz + ((in_var_COLOR0.xyz - in_var_POSITION.xyz) * ((-_59) / (_61 - _59)));
        }
        else
        {
            vec3 _97;
            if (_77 == 2)
            {
                _97 = in_var_COLOR0.xyz + ((in_var_POSITION.xyz - in_var_COLOR0.xyz) * ((-_61) / (_59 - _61)));
            }
            else
            {
                _97 = in_var_COLOR0.xyz;
            }
            _98 = _97;
            _99 = in_var_POSITION.xyz;
        }
        if (_77 == 3)
        {
            _167 = vec4(0.0);
            _168 = _44;
            _169 = vec2(0.0);
            _170 = _43;
            break;
        }
        vec4 _113 = vec4(_99, 1.0) * u_EveryObject.u_worldViewProjectionMatrix;
        vec4 _114 = vec4(_98, 1.0) * u_EveryObject.u_worldViewProjectionMatrix;
        float _116 = _113.w;
        vec2 _118 = _113.xy / vec2(_116);
        vec2 _132;
        do
        {
            vec2 _125 = _118 - (_114.xy / vec2(_114.w));
            float _126 = length(_125);
            if (_126 == 0.0)
            {
                _132 = vec2(1.0, 0.0);
                break;
            }
            _132 = _125 / vec2(_126);
            break;
        } while(false);
        vec2 _138 = vec2(-_132.y, _132.x) * in_var_POSITION.w;
        vec2 _143 = _138;
        _143.x = _138.x / u_EveryObject.u_thickness.y;
        vec2 _147 = _118 + (_143 * u_EveryObject.u_thickness.x);
        float _152 = _113.z;
        vec2 _166;
        do
        {
            if (u_EveryObject.u_worldViewProjectionMatrix[3u].y == 0.0)
            {
                _166 = vec2(_152, _116);
                break;
            }
            _166 = vec2(1.0 + _116, 0.0);
            break;
        } while(false);
        _167 = vec4(_147.x * _116, _147.y * _116, _152, _116);
        _168 = u_EveryObject.u_colour;
        _169 = _166;
        _170 = in_var_TEXCOORD0;
        break;
    } while(false);
    gl_Position = _167;
    varying_COLOR0 = _168;
    varying_TEXCOORD0 = _169;
    varying_TEXCOORD1 = _170;
}


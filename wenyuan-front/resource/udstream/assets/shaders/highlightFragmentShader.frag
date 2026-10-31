#version 300 es
precision mediump float;
precision highp int;

layout(std140) uniform type_u_HighlightColours
{
    highp vec4 u_highlightColours[16];
} u_HighlightColours;

uniform highp sampler2D SPIRV_Cross_CombinedcolourTexturecolourSampler;

in highp vec2 varying_TEXCOORD0;
in highp vec2 varying_TEXCOORD1;
in highp vec2 varying_TEXCOORD2;
in highp vec2 varying_TEXCOORD3;
in highp vec2 varying_TEXCOORD4;
in highp vec4 varying_COLOR0;
layout(location = 0) out highp vec4 out_var_SV_Target0;
layout(location = 1) out highp vec4 out_var_SV_Target1;

void main()
{
    highp vec4 _160;
    do
    {
        highp vec4 _58 = texture(SPIRV_Cross_CombinedcolourTexturecolourSampler, varying_TEXCOORD0);
        highp float _59 = _58.x;
        int _63 = (int(_59 * 255.0) >> 4) & 15;
        highp float _66 = _58.w;
        if (_58.z == 0.0)
        {
            _160 = vec4(u_HighlightColours.u_highlightColours[_63].xyz, min(1.0, varying_COLOR0.z * _66));
            break;
        }
        highp float _80 = 0.25 * u_HighlightColours.u_highlightColours[_63].w;
        highp vec4 _82 = texture(SPIRV_Cross_CombinedcolourTexturecolourSampler, varying_TEXCOORD1);
        highp vec4 _84 = texture(SPIRV_Cross_CombinedcolourTexturecolourSampler, varying_TEXCOORD2);
        highp vec4 _86 = texture(SPIRV_Cross_CombinedcolourTexturecolourSampler, varying_TEXCOORD3);
        highp vec4 _88 = texture(SPIRV_Cross_CombinedcolourTexturecolourSampler, varying_TEXCOORD4);
        _160 = vec4(u_HighlightColours.u_highlightColours[_63].xyz, max(varying_COLOR0.w, ((((((((1.0 - _66) + (_80 * step(_82.z - _59, -9.9999997473787516355514526367188e-06))) + (_80 * step(_84.z - _59, -9.9999997473787516355514526367188e-06))) + (_80 * step(_86.z - _59, -9.9999997473787516355514526367188e-06))) + (_80 * step(_88.z - _59, -9.9999997473787516355514526367188e-06))) + (_80 * min(1.0, float(abs(((int(_82.x * 255.0) >> 4) & 15) - _63))))) + (_80 * min(1.0, float(abs(((int(_84.x * 255.0) >> 4) & 15) - _63))))) + (_80 * min(1.0, float(abs(((int(_86.x * 255.0) >> 4) & 15) - _63))))) + (_80 * min(1.0, float(abs(((int(_88.x * 255.0) >> 4) & 15) - _63))))) * u_HighlightColours.u_highlightColours[_63].w);
        break;
    } while(false);
    out_var_SV_Target0 = _160;
    out_var_SV_Target1 = vec4(0.0);
}


#version 300 es
precision mediump float;
precision highp int;

uniform highp sampler2D SPIRV_Cross_CombinedcolourTexturecolourSampler;

in highp vec2 varying_TEXCOORD0;
in highp vec2 varying_TEXCOORD1;
in highp vec2 varying_TEXCOORD2;
layout(location = 0) out highp vec4 out_var_SV_Target;

void main()
{
    highp vec4 _36 = texture(SPIRV_Cross_CombinedcolourTexturecolourSampler, varying_TEXCOORD0);
    highp vec4 _38 = texture(SPIRV_Cross_CombinedcolourTexturecolourSampler, varying_TEXCOORD1);
    highp vec4 _40 = texture(SPIRV_Cross_CombinedcolourTexturecolourSampler, varying_TEXCOORD2);
    highp vec4 _51 = ((vec4(0.0, 0.0, 0.0, 0.279009997844696044921875) * _36) + (vec4(1.0, 0.0, 1.0, 0.44198000431060791015625) * _38)) + (vec4(0.0, 0.0, 0.0, 0.279009997844696044921875) * _40);
    _51.x = max(_36.x, max(_38.x, _40.x));
    out_var_SV_Target = _51;
}


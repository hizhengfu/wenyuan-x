#version 300 es
precision mediump float;
precision highp int;

layout(std140) uniform type_u_params
{
    highp vec4 u_sunDirection;
    highp vec4 u_sunColour;
    highp vec4 u_cameraUp;
} u_params;

uniform highp sampler2D SPIRV_Cross_CombinedsceneColourTexturesceneColourSampler;
uniform highp sampler2D SPIRV_Cross_CombinedsceneDepthTexturesceneDepthSampler;
uniform highp sampler2D SPIRV_Cross_CombinedsceneNormalTexturesceneNormalSampler;

in highp vec2 varying_TEXCOORD0;
layout(location = 0) out highp vec4 out_var_SV_Target0;
layout(location = 1) out highp vec4 out_var_SV_Target1;

void main()
{
    highp float _61;
    highp vec4 _65;
    highp vec4 _116;
    do
    {
        highp vec4 _55 = texture(SPIRV_Cross_CombinedsceneColourTexturesceneColourSampler, varying_TEXCOORD0);
        _61 = texture(SPIRV_Cross_CombinedsceneDepthTexturesceneDepthSampler, varying_TEXCOORD0).x;
        _65 = texture(SPIRV_Cross_CombinedsceneNormalTexturesceneNormalSampler, varying_TEXCOORD0);
        highp vec3 _97;
        if ((_65.z == 0.0) && (_65.w == 0.0))
        {
            _97 = u_params.u_cameraUp.xyz;
        }
        else
        {
            if ((_65.z >= 0.99989998340606689453125) && (_65.w >= 0.99989998340606689453125))
            {
                _116 = vec4(_55.xyz, 1.0);
                break;
            }
            _97 = vec3(_65.zw, float(int(sign(_65.y))) * sqrt(max(0.0, 1.0 - dot(_65.zw, _65.zw))));
        }
        _116 = vec4(pow((pow(_55.xyz, vec3(2.2000000476837158203125)) * clamp(0.02500000037252902984619140625 + clamp(dot(_97, u_params.u_sunDirection.xyz), 0.0, 1.0), 0.0, 1.0)) * u_params.u_sunColour.xyz, vec3(0.4545454680919647216796875)), 1.0);
        break;
    } while(false);
    out_var_SV_Target0 = _116;
    out_var_SV_Target1 = _65;
    gl_FragDepth = _61;
}


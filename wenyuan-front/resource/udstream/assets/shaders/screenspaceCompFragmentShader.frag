#version 300 es
precision mediump float;
precision highp int;

layout(std140) uniform type_u_cameraPlaneParams
{
    highp float s_CameraNearPlane;
    highp float s_CameraFarPlane;
    highp float u_clipZNear;
    highp float u_clipZFar;
} u_cameraPlaneParams;

layout(std140) uniform type_u_fragParams
{
    highp vec4 u_thresh;
    highp vec4 u_filter;
    highp vec4 u_negColour;
    highp vec4 u_posColour;
    highp vec4 u_nullColour;
} u_fragParams;

uniform highp sampler2D SPIRV_Cross_CombinedsceneDepthTexturesceneDepthSampler;
uniform highp sampler2D SPIRV_Cross_CombinedsceneCompareDepthTexturesceneCompareDepthSampler;
uniform highp sampler2D SPIRV_Cross_CombinedsceneColourTexturesceneColourSampler;
uniform highp sampler2D SPIRV_Cross_CombinedsceneCompareColourTexturesceneCompareColourSampler;

in highp vec2 varying_TEXCOORD0;
layout(location = 0) out highp vec4 out_var_SV_Target0;
layout(location = 1) out highp vec4 out_var_SV_Target1;

void main()
{
    highp vec4 _59 = texture(SPIRV_Cross_CombinedsceneDepthTexturesceneDepthSampler, varying_TEXCOORD0);
    highp float _60 = _59.x;
    highp vec4 _64 = texture(SPIRV_Cross_CombinedsceneCompareDepthTexturesceneCompareDepthSampler, varying_TEXCOORD0);
    highp float _65 = _64.x;
    highp float _69 = log2(u_cameraPlaneParams.s_CameraFarPlane + 1.0);
    highp float _76 = (pow(2.0, _60 * _69) - 1.0) - (pow(2.0, _65 * _69) - 1.0);
    highp vec4 _80 = texture(SPIRV_Cross_CombinedsceneColourTexturesceneColourSampler, varying_TEXCOORD0);
    highp vec4 _81 = _80.zyxw;
    highp vec4 _85 = texture(SPIRV_Cross_CombinedsceneCompareColourTexturesceneCompareColourSampler, varying_TEXCOORD0);
    highp vec4 _86 = _85.zyxw;
    bvec4 _88 = bvec4(_60 > _65);
    highp vec4 _89 = vec4(_88.x ? _86.x : _81.x, _88.y ? _86.y : _81.y, _88.z ? _86.z : _81.z, _88.w ? _86.w : _81.w);
    bool _90 = _60 == 1.0;
    bool _91 = _65 == 1.0;
    highp vec4 _139;
    highp float _140;
    if ((_90 || _91) && (!(_90 && _91)))
    {
        _139 = u_fragParams.u_nullColour;
        _140 = (!(u_fragParams.u_filter.z != 0.0)) ? 1.0 : (_90 ? _65 : _60);
    }
    else
    {
        highp vec4 _137;
        highp float _138;
        if ((_76 > u_fragParams.u_thresh.x) && (_76 < u_fragParams.u_thresh.y))
        {
            _137 = u_fragParams.u_posColour;
            _138 = (!(u_fragParams.u_filter.y != 0.0)) ? 1.0 : _60;
        }
        else
        {
            highp vec4 _135;
            highp float _136;
            if ((_76 < (-u_fragParams.u_thresh.x)) && (_76 > (-u_fragParams.u_thresh.y)))
            {
                _135 = u_fragParams.u_negColour;
                _136 = (!(u_fragParams.u_filter.x != 0.0)) ? 1.0 : _60;
            }
            else
            {
                _135 = _89;
                _136 = _60;
            }
            _137 = _135;
            _138 = _136;
        }
        _139 = _137;
        _140 = _138;
    }
    highp vec4 _144 = mix(_89, _139, vec4(_139.w));
    bvec4 _145 = bvec4(_140 == 1.0);
    out_var_SV_Target0 = vec4(_145.x ? vec4(0.0).x : _144.x, _145.y ? vec4(0.0).y : _144.y, _145.z ? vec4(0.0).z : _144.z, _145.w ? vec4(0.0).w : _144.w);
    out_var_SV_Target1 = vec4(0.0, ((step(0.0, 0.0) * 2.0) - 1.0) * _140, 0.0, 0.0);
    gl_FragDepth = _140;
}


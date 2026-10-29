#version 300 es
precision mediump float;
precision highp int;

const vec2 _80[8] = vec2[](vec2(1.0, 0.0), vec2(-1.0, 0.0), vec2(0.0, 1.0), vec2(0.0, -1.0), vec2(1.0), vec2(-1.0, 1.0), vec2(1.0, -1.0), vec2(-1.0));

layout(std140) uniform type_u_cameraPlaneParams
{
    highp float s_CameraNearPlane;
    highp float s_CameraFarPlane;
    highp float u_clipZNear;
    highp float u_clipZFar;
} u_cameraPlaneParams;

layout(std140) uniform type_u_params
{
    highp vec4 u_screenParams;
    layout(row_major) highp mat4 u_reprojectionMatrix;
    highp vec4 u_jitter;
} u_params;

uniform highp sampler2D SPIRV_Cross_CombinedsceneColourTexturesceneColourSampler;
uniform highp sampler2D SPIRV_Cross_CombinedsceneNormalTexturesceneNormalSampler;
uniform highp sampler2D SPIRV_Cross_CombinedsceneDepthTexturesceneDepthSampler;
uniform highp sampler2D SPIRV_Cross_CombinedhistorySceneColourTexturehistorySceneColourSampler;
uniform highp sampler2D SPIRV_Cross_CombinedhistorySceneNormalTexturehistorySceneNormalSampler;
uniform highp sampler2D SPIRV_Cross_CombinedhistorySceneDepthTexturehistorySceneDepthSampler;

in highp vec2 varying_TEXCOORD0;
in highp vec4 varying_TEXCOORD1;
layout(location = 0) out highp vec4 out_var_SV_Target0;
layout(location = 1) out highp vec4 out_var_SV_Target1;

void main()
{
    highp vec4 _252;
    highp vec4 _253;
    highp float _254;
    do
    {
        highp vec2 _94 = varying_TEXCOORD0 + (vec2(u_params.u_jitter.x, -u_params.u_jitter.y) * 0.5);
        highp vec4 _98 = texture(SPIRV_Cross_CombinedsceneColourTexturesceneColourSampler, _94);
        highp vec3 _99 = _98.xyz;
        highp vec4 _103 = texture(SPIRV_Cross_CombinedsceneNormalTexturesceneNormalSampler, _94);
        highp vec4 _107 = texture(SPIRV_Cross_CombinedsceneDepthTexturesceneDepthSampler, _94);
        highp float _108 = _107.x;
        highp vec4 _113 = texture(SPIRV_Cross_CombinedsceneDepthTexturesceneDepthSampler, varying_TEXCOORD0);
        highp float _114 = _113.x;
        highp vec3 _119;
        highp vec3 _121;
        highp vec2 _123;
        _119 = _99;
        _121 = _99;
        _123 = vec2(0.0);
        highp float _116 = _114;
        int _125 = 0;
        for (; _125 < 8; )
        {
            highp vec2 _131 = _80[_125] * u_params.u_screenParams.xy;
            highp vec2 _132 = varying_TEXCOORD0 + _131;
            highp vec4 _134 = texture(SPIRV_Cross_CombinedsceneDepthTexturesceneDepthSampler, _132);
            highp float _135 = _134.x;
            bool _136 = _135 < _116;
            bvec2 _137 = bvec2(_136);
            highp vec3 _140 = texture(SPIRV_Cross_CombinedsceneColourTexturesceneColourSampler, _132).xyz;
            _116 = _136 ? _135 : _116;
            _119 = max(_119, _140);
            _121 = min(_121, _140);
            _123 = vec2(_137.x ? _131.x : _123.x, _137.y ? _131.y : _123.y);
            _125++;
            continue;
        }
        highp vec4 _143 = texture(SPIRV_Cross_CombinedsceneDepthTexturesceneDepthSampler, varying_TEXCOORD0 + _123);
        highp float _144 = _143.x;
        highp vec4 _182 = vec4(((varying_TEXCOORD0.x + _123.x) * 2.0) - 1.0, ((varying_TEXCOORD0.y + _123.y) * 2.0) - 1.0, ((((u_cameraPlaneParams.s_CameraFarPlane / (u_cameraPlaneParams.s_CameraFarPlane - u_cameraPlaneParams.s_CameraNearPlane)) + (((u_cameraPlaneParams.s_CameraFarPlane * u_cameraPlaneParams.s_CameraNearPlane) / (u_cameraPlaneParams.s_CameraNearPlane - u_cameraPlaneParams.s_CameraFarPlane)) / (pow(2.0, _144 * log2(u_cameraPlaneParams.s_CameraFarPlane + 1.0)) - 1.0))) * 0.99999988079071044921875) * (u_cameraPlaneParams.u_clipZFar - u_cameraPlaneParams.u_clipZNear)) + u_cameraPlaneParams.u_clipZNear, 1.0) * u_params.u_reprojectionMatrix;
        highp vec4 _185 = _182 / vec4(_182.w);
        highp vec2 _193 = vec2((_185.x * 0.5) + 0.5, (_185.y * 0.5) + 0.5) - _123;
        highp float _194 = _193.x;
        highp float _198 = _193.y;
        if ((((_194 < 0.0) || (_194 > 1.0)) || (_198 < 0.0)) || (_198 > 1.0))
        {
            _252 = vec4(_98.xyz, 1.0);
            _253 = _103;
            _254 = _108;
            break;
        }
        highp vec4 _217 = texture(SPIRV_Cross_CombinedhistorySceneNormalTexturehistorySceneNormalSampler, _193);
        highp float _227 = min(_144, clamp(length(varying_TEXCOORD0 - _193) * 700.0, 0.0, 1.0));
        highp float _231 = ((_144 >= 1.0) && (_227 > 9.9999997473787516355514526367188e-05)) ? 1.0 : _227;
        bool _248 = (_231 < 0.001000000047497451305389404296875) && (abs(_103.x - _217.x) <= 1.0000000133514319600180897396058e-10);
        bvec4 _249 = bvec4(_248);
        _252 = vec4(mix(_99, clamp(texture(SPIRV_Cross_CombinedhistorySceneColourTexturehistorySceneColourSampler, _193).xyz, _121, _119), vec3(clamp(0.89999997615814208984375 * (1.0 - _231), 0.0, 1.0))), 1.0);
        _253 = vec4(_249.x ? _217.x : _103.x, _249.y ? _217.y : _103.y, _249.z ? _217.z : _103.z, _249.w ? _217.w : _103.w);
        _254 = _248 ? texture(SPIRV_Cross_CombinedhistorySceneDepthTexturehistorySceneDepthSampler, _193).x : _108;
        break;
    } while(false);
    out_var_SV_Target0 = _252;
    out_var_SV_Target1 = _253;
    gl_FragDepth = _254;
}


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
    highp vec4 u_screenParams;
    layout(row_major) highp mat4 u_inverseProjection;
    highp vec4 u_eyeToEarthSurfaceEyeSpace;
    highp vec4 u_outlineColour;
    highp vec4 u_outlineParams;
    highp vec4 u_colourizeHeightColourMin;
    highp vec4 u_colourizeHeightColourMax;
    highp vec4 u_colourizeHeightParams;
    highp vec4 u_colourizeDepthColour;
    highp vec4 u_colourizeDepthParams;
    highp vec4 u_contourColour;
    highp vec4 u_contourParams;
} u_fragParams;

layout(std140) uniform type_u_constColorsParams
{
    highp vec4 u_colorScale[256];
    highp vec4 u_heightStops[64];
    int u_colorCountInScheme;
    int pad0;
    int pad1;
    int pad2;
} u_constColorsParams;

uniform highp sampler2D SPIRV_Cross_CombinedsceneColourTexturesceneColourSampler;
uniform highp sampler2D SPIRV_Cross_CombinedsceneNormalTexturesceneNormalSampler;
uniform highp sampler2D SPIRV_Cross_CombinedsceneDepthTexturesceneDepthSampler;

in highp vec4 varying_TEXCOORD0;
in highp vec3 varying_TEXCOORD1;
in highp vec2 varying_TEXCOORD2;
in highp vec2 varying_TEXCOORD3;
in highp vec2 varying_TEXCOORD4;
in highp vec2 varying_TEXCOORD5;
layout(location = 0) out highp vec4 out_var_SV_Target0;
layout(location = 1) out highp vec4 out_var_SV_Target1;

void main()
{
    highp vec4 _93 = texture(SPIRV_Cross_CombinedsceneColourTexturesceneColourSampler, varying_TEXCOORD1.xy);
    highp vec4 _97 = texture(SPIRV_Cross_CombinedsceneNormalTexturesceneNormalSampler, varying_TEXCOORD1.xy);
    highp vec4 _101 = texture(SPIRV_Cross_CombinedsceneDepthTexturesceneDepthSampler, varying_TEXCOORD1.xy);
    highp float _102 = _101.x;
    highp vec4 _133 = vec4(varying_TEXCOORD0.xy, (mix(_102, (u_cameraPlaneParams.s_CameraFarPlane / (u_cameraPlaneParams.s_CameraFarPlane - u_cameraPlaneParams.s_CameraNearPlane)) + (((u_cameraPlaneParams.s_CameraFarPlane * u_cameraPlaneParams.s_CameraNearPlane) / (u_cameraPlaneParams.s_CameraNearPlane - u_cameraPlaneParams.s_CameraFarPlane)) / (pow(2.0, _102 * log2(u_cameraPlaneParams.s_CameraFarPlane + 1.0)) - 1.0)), varying_TEXCOORD1.z) * (u_cameraPlaneParams.u_clipZFar - u_cameraPlaneParams.u_clipZNear)) + u_cameraPlaneParams.u_clipZNear, 1.0) * u_fragParams.u_inverseProjection;
    highp vec3 _137 = _93.xyz;
    highp vec3 _138 = (_133 / vec4(_133.w)).xyz;
    highp float _145 = dot(u_fragParams.u_eyeToEarthSurfaceEyeSpace.xyz, u_fragParams.u_eyeToEarthSurfaceEyeSpace.xyz);
    highp vec3 _147 = u_fragParams.u_eyeToEarthSurfaceEyeSpace.xyz * (dot(_138, u_fragParams.u_eyeToEarthSurfaceEyeSpace.xyz) / _145);
    highp float _156 = abs(length(u_fragParams.u_eyeToEarthSurfaceEyeSpace.xyz - _147) * ((float(dot(_147, _147) < _145) * 2.0) - 1.0));
    int _165 = u_constColorsParams.u_colorCountInScheme - 1;
    highp float _167 = clamp((_156 - u_fragParams.u_colourizeHeightParams.x) / (u_fragParams.u_colourizeHeightParams.y - u_fragParams.u_colourizeHeightParams.x), 0.0, 1.0) * float(_165);
    int _168 = int(_167);
    highp float _234 = abs(_156);
    highp vec3 _245 = mix(mix(mix(mix(mix(mix(_137, u_constColorsParams.u_colorScale[_168].xyz, vec3(u_fragParams.u_colourizeHeightColourMin.w)), mix(_137, u_constColorsParams.u_colorScale[clamp(_168 + 1, 1, _165)].xyz, vec3(u_fragParams.u_colourizeHeightColourMax.w)), vec3(_167 - float(_168))), _137, vec3(1.0 - step(_102, 0.99989998340606689453125))).xyz, u_fragParams.u_colourizeDepthColour.xyz, vec3(clamp((length(_138) - u_fragParams.u_colourizeDepthParams.x) / (u_fragParams.u_colourizeDepthParams.y - u_fragParams.u_colourizeDepthParams.x), 0.0, 1.0) * u_fragParams.u_colourizeDepthColour.w)).xyz, clamp(abs((fract(vec3(_156 * (1.0 / u_fragParams.u_contourParams.z), 1.0, 1.0).xxx + vec3(1.0, 0.666666686534881591796875, 0.3333333432674407958984375)) * 6.0) - vec3(3.0)) - vec3(1.0), vec3(0.0), vec3(1.0)) * 1.0, vec3(u_fragParams.u_contourParams.w)), u_fragParams.u_contourColour.xyz, vec3((1.0 - step(u_fragParams.u_contourParams.y, _234 - u_fragParams.u_contourParams.x * trunc(_234 / u_fragParams.u_contourParams.x))) * u_fragParams.u_contourColour.w));
    highp float _309;
    highp vec4 _310;
    if ((u_fragParams.u_outlineParams.x > 0.0) && (u_fragParams.u_outlineColour.w > 0.0))
    {
        highp vec4 _261 = texture(SPIRV_Cross_CombinedsceneDepthTexturesceneDepthSampler, varying_TEXCOORD2);
        highp float _262 = _261.x;
        highp vec4 _264 = texture(SPIRV_Cross_CombinedsceneDepthTexturesceneDepthSampler, varying_TEXCOORD3);
        highp float _265 = _264.x;
        highp vec4 _267 = texture(SPIRV_Cross_CombinedsceneDepthTexturesceneDepthSampler, varying_TEXCOORD4);
        highp float _268 = _267.x;
        highp vec4 _270 = texture(SPIRV_Cross_CombinedsceneDepthTexturesceneDepthSampler, varying_TEXCOORD5);
        highp float _271 = _270.x;
        highp float _273 = (u_fragParams.u_outlineParams.y * _102) * 0.001000000047497451305389404296875;
        highp vec4 _306 = mix(vec4(_245, _102), vec4(mix(_245.xyz, u_fragParams.u_outlineColour.xyz, vec3(u_fragParams.u_outlineColour.w)), min(min(min(_262, _265), _268), _271)), vec4(1.0 - (((step(abs(_262 - _102), _273) * step(abs(_265 - _102), _273)) * step(abs(_268 - _102), _273)) * step(abs(_271 - _102), _273))));
        _309 = _306.w;
        _310 = vec4(_306.x, _306.y, _306.z, _93.w);
    }
    else
    {
        _309 = _102;
        _310 = vec4(_245.x, _245.y, _245.z, _93.w);
    }
    out_var_SV_Target0 = vec4(_310.xyz, 1.0);
    out_var_SV_Target1 = _97;
    gl_FragDepth = _309;
}


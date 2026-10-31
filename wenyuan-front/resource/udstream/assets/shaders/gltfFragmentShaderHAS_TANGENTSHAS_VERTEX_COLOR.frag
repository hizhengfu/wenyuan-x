#version 300 es
precision mediump float;
precision highp int;

struct Light
{
    highp vec3 direction;
    highp float range;
    highp vec3 color;
    highp float intensity;
    highp vec3 position;
    highp float innerConeCos;
    highp float outerConeCos;
    int type;
    highp vec2 _padding;
};

layout(std140) uniform type_u_cameraPlaneParams
{
    highp float s_CameraNearPlane;
    highp float s_CameraFarPlane;
    highp float u_clipZNear;
    highp float u_clipZFar;
} u_cameraPlaneParams;

layout(std140) uniform type_u_FragSettings
{
    highp vec3 u_Camera;
    highp float u_Exposure;
    highp vec3 u_EmissiveFactor;
    highp float u_NormalScale;
    highp vec4 u_BaseColorFactor;
    highp float u_MetallicFactor;
    highp float u_RoughnessFactor;
    int u_BaseColorUVSet;
    int u_MetallicRoughnessUVSet;
    int u_NormalUVSet;
    int u_EmissiveUVSet;
    int u_OcclusionUVSet;
    highp float u_OcclusionStrength;
    int u_alphaMode;
    highp float u_AlphaCutoff;
    highp float _padding;
    int u_lightCount;
    highp vec4 u_ambience;
    highp vec4 u_colourOnly;
    highp vec4 u_tint;
    Light u_Lights[8];
} u_FragSettings;

uniform highp sampler2D SPIRV_Cross_CombinedbaseColorTexturebaseColorSampler;
uniform highp sampler2D SPIRV_Cross_CombinednormalTexturenormalSampler;
uniform highp sampler2D SPIRV_Cross_CombinedmetallicRoughnessTexturemetallicRoughnessSampler;
uniform highp sampler2D SPIRV_Cross_CombinedemissiveTextureemissiveSampler;
uniform highp sampler2D SPIRV_Cross_CombinedocclusionTextureocclusionSampler;

in highp vec3 varying_POSITION0;
in highp vec3 varying_POSITION1;
in highp mat3 varying_NORMAL;
in highp vec2 varying_TEXCOORD0;
in highp vec2 varying_TEXCOORD1;
in highp vec2 varying_TEXCOORD2;
in highp vec2 varying_TEXCOORD3;
in highp mat4 varying_TEXCOORD4;
in highp vec4 varying_COLOR0;
layout(location = 0) out highp vec4 out_var_SV_Target0;
layout(location = 1) out highp vec4 out_var_SV_Target1;

float _106;
vec4 _107;

void main()
{
    bool _128 = u_FragSettings.u_BaseColorUVSet >= 0;
    highp vec4 _153;
    if (_128)
    {
        highp vec3 _141;
        if (_128)
        {
            bvec2 _136 = bvec2(u_FragSettings.u_BaseColorUVSet < 1);
            _141 = vec3(vec2(_136.x ? varying_TEXCOORD0.x : varying_TEXCOORD1.x, _136.y ? varying_TEXCOORD0.y : varying_TEXCOORD1.y), 1.0);
        }
        else
        {
            _141 = vec3(0.0);
        }
        highp vec4 _144 = texture(SPIRV_Cross_CombinedbaseColorTexturebaseColorSampler, _141.xy);
        _153 = u_FragSettings.u_BaseColorFactor * vec4(pow(_144.xyz, vec3(2.2000000476837158203125)), _144.w);
    }
    else
    {
        _153 = u_FragSettings.u_BaseColorFactor;
    }
    highp vec4 _157 = (_153 * varying_COLOR0) * u_FragSettings.u_tint;
    highp vec3 _159 = -normalize(varying_POSITION1);
    bool _162 = u_FragSettings.u_NormalUVSet >= 0;
    highp vec2 _168;
    if (_162)
    {
        bvec2 _166 = bvec2(u_FragSettings.u_NormalUVSet < 1);
        _168 = vec2(_166.x ? varying_TEXCOORD0.x : varying_TEXCOORD1.x, _166.y ? varying_TEXCOORD0.y : varying_TEXCOORD1.y);
    }
    else
    {
        _168 = vec2(0.0);
    }
    highp vec3 _174 = normalize(varying_NORMAL[2]);
    highp vec3 _192;
    if (_162)
    {
        _192 = mat3(normalize(varying_NORMAL[0]), normalize(varying_NORMAL[1]), _174) * normalize(((texture(SPIRV_Cross_CombinednormalTexturenormalSampler, _168).xyz * 2.0) - vec3(1.0)) * vec3(u_FragSettings.u_NormalScale, u_FragSettings.u_NormalScale, 1.0));
    }
    else
    {
        _192 = _174;
    }
    highp vec3 _193 = _157.xyz;
    bool _200 = u_FragSettings.u_MetallicRoughnessUVSet >= 0;
    highp float _221;
    highp float _222;
    if (_200)
    {
        highp vec3 _213;
        if (_200)
        {
            bvec2 _208 = bvec2(u_FragSettings.u_MetallicRoughnessUVSet < 1);
            _213 = vec3(vec2(_208.x ? varying_TEXCOORD0.x : varying_TEXCOORD1.x, _208.y ? varying_TEXCOORD0.y : varying_TEXCOORD1.y), 1.0);
        }
        else
        {
            _213 = vec3(0.0);
        }
        highp vec4 _216 = texture(SPIRV_Cross_CombinedmetallicRoughnessTexturemetallicRoughnessSampler, _213.xy);
        _221 = u_FragSettings.u_RoughnessFactor * _216.y;
        _222 = u_FragSettings.u_MetallicFactor * _216.z;
    }
    else
    {
        _221 = u_FragSettings.u_RoughnessFactor;
        _222 = u_FragSettings.u_MetallicFactor;
    }
    highp vec3 _224 = vec3(_222);
    highp vec3 _225 = mix(_193 * vec3(0.959999978542327880859375), vec3(0.0), _224);
    highp vec3 _226 = mix(vec3(0.039999999105930328369140625), _193, _224);
    highp float _227 = clamp(_221, 0.0, 1.0);
    highp float _228 = _227 * _227;
    highp vec3 _236 = vec3(clamp(max(max(_226.x, _226.y), _226.z) * 50.0, 0.0, 1.0));
    highp vec3 _243;
    highp vec3 _246;
    _243 = vec3(0.0);
    _246 = pow(_225 * u_FragSettings.u_ambience.xyz, vec3(2.2000000476837158203125));
    highp vec3 _244;
    highp vec3 _247;
    for (int _248 = 0; _248 < u_FragSettings.u_lightCount; _243 = _244, _246 = _247, _248++)
    {
        highp float _291;
        highp vec3 _292;
        if (u_FragSettings.u_Lights[_248].type != 0)
        {
            highp vec3 _276 = u_FragSettings.u_Lights[_248].position - varying_POSITION0;
            highp float _277 = length(_276);
            highp float _290;
            do
            {
                if (u_FragSettings.u_Lights[_248].range <= 0.0)
                {
                    _290 = 1.0;
                    break;
                }
                _290 = max(min(1.0 - pow(_277 / u_FragSettings.u_Lights[_248].range, 4.0), 1.0), 0.0) / pow(_277, 2.0);
                break;
            } while(false);
            _291 = _290;
            _292 = _276;
        }
        else
        {
            _291 = 1.0;
            _292 = -u_FragSettings.u_Lights[_248].direction;
        }
        highp float _310;
        if (u_FragSettings.u_Lights[_248].type == 2)
        {
            highp float _309;
            do
            {
                highp float _301 = dot(normalize(u_FragSettings.u_Lights[_248].direction), normalize(-_292));
                if (_301 > u_FragSettings.u_Lights[_248].outerConeCos)
                {
                    if (_301 < u_FragSettings.u_Lights[_248].innerConeCos)
                    {
                        _309 = smoothstep(u_FragSettings.u_Lights[_248].outerConeCos, u_FragSettings.u_Lights[_248].innerConeCos, _301);
                        break;
                    }
                    _309 = 1.0;
                    break;
                }
                _309 = 0.0;
                break;
            } while(false);
            _310 = _309;
        }
        else
        {
            _310 = 1.0;
        }
        highp vec3 _314 = normalize(_292);
        highp vec3 _316 = normalize(_314 + _159);
        highp float _318 = clamp(dot(_192, _314), 0.0, 1.0);
        highp float _320 = clamp(dot(_192, _159), 0.0, 1.0);
        highp float _322 = clamp(dot(_192, _316), 0.0, 1.0);
        if ((_318 > 0.0) || (_320 > 0.0))
        {
            highp float _344;
            highp vec3 _330 = (u_FragSettings.u_Lights[_248].color * ((_291 * _310) * u_FragSettings.u_Lights[_248].intensity)) * _318;
            highp vec3 _336 = _226 + ((_236 - _226) * pow(clamp(1.0 - clamp(dot(_159, _316), 0.0, 1.0), 0.0, 1.0), 5.0));
            highp float _361;
            do
            {
                _344 = _228 * _228;
                highp float _346 = 1.0 - _344;
                highp float _356 = (_318 * sqrt(((_320 * _320) * _346) + _344)) + (_320 * sqrt(((_318 * _318) * _346) + _344));
                if (_356 > 0.0)
                {
                    _361 = 0.5 / _356;
                    break;
                }
                _361 = 0.0;
                break;
            } while(false);
            highp float _365 = ((_322 * _322) * (_344 - 1.0)) + 1.0;
            _244 = _243 + (_330 * ((_336 * _361) * (_344 / ((3.1415927410125732421875 * _365) * _365))));
            _247 = _246 + (_330 * ((vec3(1.0) - _336) * (_225 * vec3(0.3183098733425140380859375))));
        }
        else
        {
            _244 = _243;
            _247 = _246;
        }
    }
    bool _377 = u_FragSettings.u_EmissiveUVSet >= 0;
    highp vec3 _402;
    if (_377)
    {
        highp vec3 _390;
        if (_377)
        {
            bvec2 _385 = bvec2(u_FragSettings.u_EmissiveUVSet < 1);
            _390 = vec3(vec2(_385.x ? varying_TEXCOORD0.x : varying_TEXCOORD1.x, _385.y ? varying_TEXCOORD0.y : varying_TEXCOORD1.y), 1.0);
        }
        else
        {
            _390 = vec3(0.0);
        }
        _402 = u_FragSettings.u_EmissiveFactor * vec4(pow(texture(SPIRV_Cross_CombinedemissiveTextureemissiveSampler, _390.xy).xyz, vec3(2.2000000476837158203125)), _106).xyz;
    }
    else
    {
        _402 = u_FragSettings.u_EmissiveFactor;
    }
    highp vec3 _404 = (_402 + _246) + _243;
    bool _407 = u_FragSettings.u_OcclusionUVSet >= 0;
    highp vec3 _430;
    if (_407)
    {
        highp vec3 _420;
        if (_407)
        {
            bvec2 _415 = bvec2(u_FragSettings.u_OcclusionUVSet < 1);
            _420 = vec3(vec2(_415.x ? varying_TEXCOORD0.x : varying_TEXCOORD1.x, _415.y ? varying_TEXCOORD0.y : varying_TEXCOORD1.y), 1.0);
        }
        else
        {
            _420 = vec3(0.0);
        }
        _430 = mix(_404, _404 * texture(SPIRV_Cross_CombinedocclusionTextureocclusionSampler, _420.xy).x, vec3(u_FragSettings.u_OcclusionStrength));
    }
    else
    {
        _430 = _404;
    }
    highp vec4 _443;
    if (u_FragSettings.u_alphaMode == 1)
    {
        if (_157.w < u_FragSettings.u_AlphaCutoff)
        {
            discard;
        }
        highp vec4 _442 = _107;
        _442.w = 1.0;
        _443 = _442;
    }
    else
    {
        _443 = _157;
    }
    highp float _469;
    do
    {
        if (varying_TEXCOORD2.y != 0.0)
        {
            _469 = varying_TEXCOORD2.x / varying_TEXCOORD2.y;
            break;
        }
        _469 = log2(varying_TEXCOORD2.x) * (1.0 / log2(u_cameraPlaneParams.s_CameraFarPlane + 1.0));
        break;
    } while(false);
    highp vec4 _486;
    if (u_FragSettings.u_alphaMode == 2)
    {
        highp vec4 _485 = vec4(varying_TEXCOORD3.x, ((step(0.0, 0.0) * 2.0) - 1.0) * _469, 0.0, 0.0);
        _485.w = varying_TEXCOORD3.y;
        _486 = _485;
    }
    else
    {
        _486 = vec4(varying_TEXCOORD3.x, ((step(0.0, 1.0) * 2.0) - 1.0) * _469, 1.0, 1.0);
    }
    out_var_SV_Target0 = mix(vec4(pow(_430 * u_FragSettings.u_Exposure, vec3(0.454545438289642333984375)), _443.w), vec4(u_FragSettings.u_colourOnly.xyz, u_FragSettings.u_tint.w), vec4(u_FragSettings.u_colourOnly.w));
    out_var_SV_Target1 = _486;
    gl_FragDepth = _469;
}


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
in highp vec3 varying_NORMAL;
in highp vec2 varying_TEXCOORD0;
in highp vec2 varying_TEXCOORD1;
in highp vec2 varying_TEXCOORD2;
in highp vec2 varying_TEXCOORD3;
in highp mat4 varying_TEXCOORD4;
in highp vec4 varying_COLOR0;
layout(location = 0) out highp vec4 out_var_SV_Target0;
layout(location = 1) out highp vec4 out_var_SV_Target1;

float _105;
vec4 _106;

void main()
{
    bool _127 = u_FragSettings.u_BaseColorUVSet >= 0;
    highp vec4 _152;
    if (_127)
    {
        highp vec3 _140;
        if (_127)
        {
            bvec2 _135 = bvec2(u_FragSettings.u_BaseColorUVSet < 1);
            _140 = vec3(vec2(_135.x ? varying_TEXCOORD0.x : varying_TEXCOORD1.x, _135.y ? varying_TEXCOORD0.y : varying_TEXCOORD1.y), 1.0);
        }
        else
        {
            _140 = vec3(0.0);
        }
        highp vec4 _143 = texture(SPIRV_Cross_CombinedbaseColorTexturebaseColorSampler, _140.xy);
        _152 = u_FragSettings.u_BaseColorFactor * vec4(pow(_143.xyz, vec3(2.2000000476837158203125)), _143.w);
    }
    else
    {
        _152 = u_FragSettings.u_BaseColorFactor;
    }
    highp vec4 _156 = (_152 * varying_COLOR0) * u_FragSettings.u_tint;
    highp vec3 _158 = -normalize(varying_POSITION1);
    bool _161 = u_FragSettings.u_NormalUVSet >= 0;
    highp vec2 _167;
    if (_161)
    {
        bvec2 _165 = bvec2(u_FragSettings.u_NormalUVSet < 1);
        _167 = vec2(_165.x ? varying_TEXCOORD0.x : varying_TEXCOORD1.x, _165.y ? varying_TEXCOORD0.y : varying_TEXCOORD1.y);
    }
    else
    {
        _167 = vec2(0.0);
    }
    highp vec3 _170 = vec3(_167, 0.0);
    highp vec3 _171 = dFdx(_170);
    highp vec3 _172 = dFdy(_170);
    highp vec3 _173 = dFdx(varying_POSITION1);
    highp float _174 = _172.y;
    highp vec3 _176 = dFdy(varying_POSITION1);
    highp float _177 = _171.y;
    highp vec3 _186 = ((_173 * _174) - (_176 * _177)) / vec3((_171.x * _174) - (_172.x * _177));
    highp vec3 _187 = normalize(varying_NORMAL);
    highp vec3 _191 = normalize(_186 - (_187 * dot(_187, _186)));
    highp vec3 _210;
    if (_161)
    {
        _210 = mat3(_191, cross(_187, _191), _187) * normalize(((texture(SPIRV_Cross_CombinednormalTexturenormalSampler, _167).xyz * 2.0) - vec3(1.0)) * vec3(u_FragSettings.u_NormalScale, u_FragSettings.u_NormalScale, 1.0));
    }
    else
    {
        _210 = _187;
    }
    highp vec3 _211 = _156.xyz;
    bool _218 = u_FragSettings.u_MetallicRoughnessUVSet >= 0;
    highp float _239;
    highp float _240;
    if (_218)
    {
        highp vec3 _231;
        if (_218)
        {
            bvec2 _226 = bvec2(u_FragSettings.u_MetallicRoughnessUVSet < 1);
            _231 = vec3(vec2(_226.x ? varying_TEXCOORD0.x : varying_TEXCOORD1.x, _226.y ? varying_TEXCOORD0.y : varying_TEXCOORD1.y), 1.0);
        }
        else
        {
            _231 = vec3(0.0);
        }
        highp vec4 _234 = texture(SPIRV_Cross_CombinedmetallicRoughnessTexturemetallicRoughnessSampler, _231.xy);
        _239 = u_FragSettings.u_RoughnessFactor * _234.y;
        _240 = u_FragSettings.u_MetallicFactor * _234.z;
    }
    else
    {
        _239 = u_FragSettings.u_RoughnessFactor;
        _240 = u_FragSettings.u_MetallicFactor;
    }
    highp vec3 _242 = vec3(_240);
    highp vec3 _243 = mix(_211 * vec3(0.959999978542327880859375), vec3(0.0), _242);
    highp vec3 _244 = mix(vec3(0.039999999105930328369140625), _211, _242);
    highp float _245 = clamp(_239, 0.0, 1.0);
    highp float _246 = _245 * _245;
    highp vec3 _254 = vec3(clamp(max(max(_244.x, _244.y), _244.z) * 50.0, 0.0, 1.0));
    highp vec3 _261;
    highp vec3 _264;
    _261 = vec3(0.0);
    _264 = pow(_243 * u_FragSettings.u_ambience.xyz, vec3(2.2000000476837158203125));
    highp vec3 _262;
    highp vec3 _265;
    for (int _266 = 0; _266 < u_FragSettings.u_lightCount; _261 = _262, _264 = _265, _266++)
    {
        highp float _309;
        highp vec3 _310;
        if (u_FragSettings.u_Lights[_266].type != 0)
        {
            highp vec3 _294 = u_FragSettings.u_Lights[_266].position - varying_POSITION0;
            highp float _295 = length(_294);
            highp float _308;
            do
            {
                if (u_FragSettings.u_Lights[_266].range <= 0.0)
                {
                    _308 = 1.0;
                    break;
                }
                _308 = max(min(1.0 - pow(_295 / u_FragSettings.u_Lights[_266].range, 4.0), 1.0), 0.0) / pow(_295, 2.0);
                break;
            } while(false);
            _309 = _308;
            _310 = _294;
        }
        else
        {
            _309 = 1.0;
            _310 = -u_FragSettings.u_Lights[_266].direction;
        }
        highp float _328;
        if (u_FragSettings.u_Lights[_266].type == 2)
        {
            highp float _327;
            do
            {
                highp float _319 = dot(normalize(u_FragSettings.u_Lights[_266].direction), normalize(-_310));
                if (_319 > u_FragSettings.u_Lights[_266].outerConeCos)
                {
                    if (_319 < u_FragSettings.u_Lights[_266].innerConeCos)
                    {
                        _327 = smoothstep(u_FragSettings.u_Lights[_266].outerConeCos, u_FragSettings.u_Lights[_266].innerConeCos, _319);
                        break;
                    }
                    _327 = 1.0;
                    break;
                }
                _327 = 0.0;
                break;
            } while(false);
            _328 = _327;
        }
        else
        {
            _328 = 1.0;
        }
        highp vec3 _332 = normalize(_310);
        highp vec3 _334 = normalize(_332 + _158);
        highp float _336 = clamp(dot(_210, _332), 0.0, 1.0);
        highp float _338 = clamp(dot(_210, _158), 0.0, 1.0);
        highp float _340 = clamp(dot(_210, _334), 0.0, 1.0);
        if ((_336 > 0.0) || (_338 > 0.0))
        {
            highp float _362;
            highp vec3 _348 = (u_FragSettings.u_Lights[_266].color * ((_309 * _328) * u_FragSettings.u_Lights[_266].intensity)) * _336;
            highp vec3 _354 = _244 + ((_254 - _244) * pow(clamp(1.0 - clamp(dot(_158, _334), 0.0, 1.0), 0.0, 1.0), 5.0));
            highp float _379;
            do
            {
                _362 = _246 * _246;
                highp float _364 = 1.0 - _362;
                highp float _374 = (_336 * sqrt(((_338 * _338) * _364) + _362)) + (_338 * sqrt(((_336 * _336) * _364) + _362));
                if (_374 > 0.0)
                {
                    _379 = 0.5 / _374;
                    break;
                }
                _379 = 0.0;
                break;
            } while(false);
            highp float _383 = ((_340 * _340) * (_362 - 1.0)) + 1.0;
            _262 = _261 + (_348 * ((_354 * _379) * (_362 / ((3.1415927410125732421875 * _383) * _383))));
            _265 = _264 + (_348 * ((vec3(1.0) - _354) * (_243 * vec3(0.3183098733425140380859375))));
        }
        else
        {
            _262 = _261;
            _265 = _264;
        }
    }
    bool _395 = u_FragSettings.u_EmissiveUVSet >= 0;
    highp vec3 _420;
    if (_395)
    {
        highp vec3 _408;
        if (_395)
        {
            bvec2 _403 = bvec2(u_FragSettings.u_EmissiveUVSet < 1);
            _408 = vec3(vec2(_403.x ? varying_TEXCOORD0.x : varying_TEXCOORD1.x, _403.y ? varying_TEXCOORD0.y : varying_TEXCOORD1.y), 1.0);
        }
        else
        {
            _408 = vec3(0.0);
        }
        _420 = u_FragSettings.u_EmissiveFactor * vec4(pow(texture(SPIRV_Cross_CombinedemissiveTextureemissiveSampler, _408.xy).xyz, vec3(2.2000000476837158203125)), _105).xyz;
    }
    else
    {
        _420 = u_FragSettings.u_EmissiveFactor;
    }
    highp vec3 _422 = (_420 + _264) + _261;
    bool _425 = u_FragSettings.u_OcclusionUVSet >= 0;
    highp vec3 _448;
    if (_425)
    {
        highp vec3 _438;
        if (_425)
        {
            bvec2 _433 = bvec2(u_FragSettings.u_OcclusionUVSet < 1);
            _438 = vec3(vec2(_433.x ? varying_TEXCOORD0.x : varying_TEXCOORD1.x, _433.y ? varying_TEXCOORD0.y : varying_TEXCOORD1.y), 1.0);
        }
        else
        {
            _438 = vec3(0.0);
        }
        _448 = mix(_422, _422 * texture(SPIRV_Cross_CombinedocclusionTextureocclusionSampler, _438.xy).x, vec3(u_FragSettings.u_OcclusionStrength));
    }
    else
    {
        _448 = _422;
    }
    highp vec4 _461;
    if (u_FragSettings.u_alphaMode == 1)
    {
        if (_156.w < u_FragSettings.u_AlphaCutoff)
        {
            discard;
        }
        highp vec4 _460 = _106;
        _460.w = 1.0;
        _461 = _460;
    }
    else
    {
        _461 = _156;
    }
    highp float _487;
    do
    {
        if (varying_TEXCOORD2.y != 0.0)
        {
            _487 = varying_TEXCOORD2.x / varying_TEXCOORD2.y;
            break;
        }
        _487 = log2(varying_TEXCOORD2.x) * (1.0 / log2(u_cameraPlaneParams.s_CameraFarPlane + 1.0));
        break;
    } while(false);
    highp vec4 _504;
    if (u_FragSettings.u_alphaMode == 2)
    {
        highp vec4 _503 = vec4(varying_TEXCOORD3.x, ((step(0.0, 0.0) * 2.0) - 1.0) * _487, 0.0, 0.0);
        _503.w = varying_TEXCOORD3.y;
        _504 = _503;
    }
    else
    {
        _504 = vec4(varying_TEXCOORD3.x, ((step(0.0, 1.0) * 2.0) - 1.0) * _487, 1.0, 1.0);
    }
    out_var_SV_Target0 = mix(vec4(pow(_448 * u_FragSettings.u_Exposure, vec3(0.454545438289642333984375)), _461.w), vec4(u_FragSettings.u_colourOnly.xyz, u_FragSettings.u_tint.w), vec4(u_FragSettings.u_colourOnly.w));
    out_var_SV_Target1 = _504;
    gl_FragDepth = _487;
}


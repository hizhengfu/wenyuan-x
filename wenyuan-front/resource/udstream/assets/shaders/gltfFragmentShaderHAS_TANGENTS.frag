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
layout(location = 0) out highp vec4 out_var_SV_Target0;
layout(location = 1) out highp vec4 out_var_SV_Target1;

float _105;
vec4 _106;

void main()
{
    bool _126 = u_FragSettings.u_BaseColorUVSet >= 0;
    highp vec4 _151;
    if (_126)
    {
        highp vec3 _139;
        if (_126)
        {
            bvec2 _134 = bvec2(u_FragSettings.u_BaseColorUVSet < 1);
            _139 = vec3(vec2(_134.x ? varying_TEXCOORD0.x : varying_TEXCOORD1.x, _134.y ? varying_TEXCOORD0.y : varying_TEXCOORD1.y), 1.0);
        }
        else
        {
            _139 = vec3(0.0);
        }
        highp vec4 _142 = texture(SPIRV_Cross_CombinedbaseColorTexturebaseColorSampler, _139.xy);
        _151 = u_FragSettings.u_BaseColorFactor * vec4(pow(_142.xyz, vec3(2.2000000476837158203125)), _142.w);
    }
    else
    {
        _151 = u_FragSettings.u_BaseColorFactor;
    }
    highp vec4 _154 = _151 * u_FragSettings.u_tint;
    highp vec3 _156 = -normalize(varying_POSITION1);
    bool _159 = u_FragSettings.u_NormalUVSet >= 0;
    highp vec2 _165;
    if (_159)
    {
        bvec2 _163 = bvec2(u_FragSettings.u_NormalUVSet < 1);
        _165 = vec2(_163.x ? varying_TEXCOORD0.x : varying_TEXCOORD1.x, _163.y ? varying_TEXCOORD0.y : varying_TEXCOORD1.y);
    }
    else
    {
        _165 = vec2(0.0);
    }
    highp vec3 _171 = normalize(varying_NORMAL[2]);
    highp vec3 _189;
    if (_159)
    {
        _189 = mat3(normalize(varying_NORMAL[0]), normalize(varying_NORMAL[1]), _171) * normalize(((texture(SPIRV_Cross_CombinednormalTexturenormalSampler, _165).xyz * 2.0) - vec3(1.0)) * vec3(u_FragSettings.u_NormalScale, u_FragSettings.u_NormalScale, 1.0));
    }
    else
    {
        _189 = _171;
    }
    highp vec3 _190 = _154.xyz;
    bool _197 = u_FragSettings.u_MetallicRoughnessUVSet >= 0;
    highp float _218;
    highp float _219;
    if (_197)
    {
        highp vec3 _210;
        if (_197)
        {
            bvec2 _205 = bvec2(u_FragSettings.u_MetallicRoughnessUVSet < 1);
            _210 = vec3(vec2(_205.x ? varying_TEXCOORD0.x : varying_TEXCOORD1.x, _205.y ? varying_TEXCOORD0.y : varying_TEXCOORD1.y), 1.0);
        }
        else
        {
            _210 = vec3(0.0);
        }
        highp vec4 _213 = texture(SPIRV_Cross_CombinedmetallicRoughnessTexturemetallicRoughnessSampler, _210.xy);
        _218 = u_FragSettings.u_RoughnessFactor * _213.y;
        _219 = u_FragSettings.u_MetallicFactor * _213.z;
    }
    else
    {
        _218 = u_FragSettings.u_RoughnessFactor;
        _219 = u_FragSettings.u_MetallicFactor;
    }
    highp vec3 _221 = vec3(_219);
    highp vec3 _222 = mix(_190 * vec3(0.959999978542327880859375), vec3(0.0), _221);
    highp vec3 _223 = mix(vec3(0.039999999105930328369140625), _190, _221);
    highp float _224 = clamp(_218, 0.0, 1.0);
    highp float _225 = _224 * _224;
    highp vec3 _233 = vec3(clamp(max(max(_223.x, _223.y), _223.z) * 50.0, 0.0, 1.0));
    highp vec3 _240;
    highp vec3 _243;
    _240 = vec3(0.0);
    _243 = pow(_222 * u_FragSettings.u_ambience.xyz, vec3(2.2000000476837158203125));
    highp vec3 _241;
    highp vec3 _244;
    for (int _245 = 0; _245 < u_FragSettings.u_lightCount; _240 = _241, _243 = _244, _245++)
    {
        highp float _288;
        highp vec3 _289;
        if (u_FragSettings.u_Lights[_245].type != 0)
        {
            highp vec3 _273 = u_FragSettings.u_Lights[_245].position - varying_POSITION0;
            highp float _274 = length(_273);
            highp float _287;
            do
            {
                if (u_FragSettings.u_Lights[_245].range <= 0.0)
                {
                    _287 = 1.0;
                    break;
                }
                _287 = max(min(1.0 - pow(_274 / u_FragSettings.u_Lights[_245].range, 4.0), 1.0), 0.0) / pow(_274, 2.0);
                break;
            } while(false);
            _288 = _287;
            _289 = _273;
        }
        else
        {
            _288 = 1.0;
            _289 = -u_FragSettings.u_Lights[_245].direction;
        }
        highp float _307;
        if (u_FragSettings.u_Lights[_245].type == 2)
        {
            highp float _306;
            do
            {
                highp float _298 = dot(normalize(u_FragSettings.u_Lights[_245].direction), normalize(-_289));
                if (_298 > u_FragSettings.u_Lights[_245].outerConeCos)
                {
                    if (_298 < u_FragSettings.u_Lights[_245].innerConeCos)
                    {
                        _306 = smoothstep(u_FragSettings.u_Lights[_245].outerConeCos, u_FragSettings.u_Lights[_245].innerConeCos, _298);
                        break;
                    }
                    _306 = 1.0;
                    break;
                }
                _306 = 0.0;
                break;
            } while(false);
            _307 = _306;
        }
        else
        {
            _307 = 1.0;
        }
        highp vec3 _311 = normalize(_289);
        highp vec3 _313 = normalize(_311 + _156);
        highp float _315 = clamp(dot(_189, _311), 0.0, 1.0);
        highp float _317 = clamp(dot(_189, _156), 0.0, 1.0);
        highp float _319 = clamp(dot(_189, _313), 0.0, 1.0);
        if ((_315 > 0.0) || (_317 > 0.0))
        {
            highp float _341;
            highp vec3 _327 = (u_FragSettings.u_Lights[_245].color * ((_288 * _307) * u_FragSettings.u_Lights[_245].intensity)) * _315;
            highp vec3 _333 = _223 + ((_233 - _223) * pow(clamp(1.0 - clamp(dot(_156, _313), 0.0, 1.0), 0.0, 1.0), 5.0));
            highp float _358;
            do
            {
                _341 = _225 * _225;
                highp float _343 = 1.0 - _341;
                highp float _353 = (_315 * sqrt(((_317 * _317) * _343) + _341)) + (_317 * sqrt(((_315 * _315) * _343) + _341));
                if (_353 > 0.0)
                {
                    _358 = 0.5 / _353;
                    break;
                }
                _358 = 0.0;
                break;
            } while(false);
            highp float _362 = ((_319 * _319) * (_341 - 1.0)) + 1.0;
            _241 = _240 + (_327 * ((_333 * _358) * (_341 / ((3.1415927410125732421875 * _362) * _362))));
            _244 = _243 + (_327 * ((vec3(1.0) - _333) * (_222 * vec3(0.3183098733425140380859375))));
        }
        else
        {
            _241 = _240;
            _244 = _243;
        }
    }
    bool _374 = u_FragSettings.u_EmissiveUVSet >= 0;
    highp vec3 _399;
    if (_374)
    {
        highp vec3 _387;
        if (_374)
        {
            bvec2 _382 = bvec2(u_FragSettings.u_EmissiveUVSet < 1);
            _387 = vec3(vec2(_382.x ? varying_TEXCOORD0.x : varying_TEXCOORD1.x, _382.y ? varying_TEXCOORD0.y : varying_TEXCOORD1.y), 1.0);
        }
        else
        {
            _387 = vec3(0.0);
        }
        _399 = u_FragSettings.u_EmissiveFactor * vec4(pow(texture(SPIRV_Cross_CombinedemissiveTextureemissiveSampler, _387.xy).xyz, vec3(2.2000000476837158203125)), _105).xyz;
    }
    else
    {
        _399 = u_FragSettings.u_EmissiveFactor;
    }
    highp vec3 _401 = (_399 + _243) + _240;
    bool _404 = u_FragSettings.u_OcclusionUVSet >= 0;
    highp vec3 _427;
    if (_404)
    {
        highp vec3 _417;
        if (_404)
        {
            bvec2 _412 = bvec2(u_FragSettings.u_OcclusionUVSet < 1);
            _417 = vec3(vec2(_412.x ? varying_TEXCOORD0.x : varying_TEXCOORD1.x, _412.y ? varying_TEXCOORD0.y : varying_TEXCOORD1.y), 1.0);
        }
        else
        {
            _417 = vec3(0.0);
        }
        _427 = mix(_401, _401 * texture(SPIRV_Cross_CombinedocclusionTextureocclusionSampler, _417.xy).x, vec3(u_FragSettings.u_OcclusionStrength));
    }
    else
    {
        _427 = _401;
    }
    highp vec4 _440;
    if (u_FragSettings.u_alphaMode == 1)
    {
        if (_154.w < u_FragSettings.u_AlphaCutoff)
        {
            discard;
        }
        highp vec4 _439 = _106;
        _439.w = 1.0;
        _440 = _439;
    }
    else
    {
        _440 = _154;
    }
    highp float _466;
    do
    {
        if (varying_TEXCOORD2.y != 0.0)
        {
            _466 = varying_TEXCOORD2.x / varying_TEXCOORD2.y;
            break;
        }
        _466 = log2(varying_TEXCOORD2.x) * (1.0 / log2(u_cameraPlaneParams.s_CameraFarPlane + 1.0));
        break;
    } while(false);
    highp vec4 _483;
    if (u_FragSettings.u_alphaMode == 2)
    {
        highp vec4 _482 = vec4(varying_TEXCOORD3.x, ((step(0.0, 0.0) * 2.0) - 1.0) * _466, 0.0, 0.0);
        _482.w = varying_TEXCOORD3.y;
        _483 = _482;
    }
    else
    {
        _483 = vec4(varying_TEXCOORD3.x, ((step(0.0, 1.0) * 2.0) - 1.0) * _466, 1.0, 1.0);
    }
    out_var_SV_Target0 = mix(vec4(pow(_427 * u_FragSettings.u_Exposure, vec3(0.454545438289642333984375)), _440.w), vec4(u_FragSettings.u_colourOnly.xyz, u_FragSettings.u_tint.w), vec4(u_FragSettings.u_colourOnly.w));
    out_var_SV_Target1 = _483;
    gl_FragDepth = _466;
}


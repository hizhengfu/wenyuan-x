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
layout(location = 0) out highp vec4 out_var_SV_Target0;
layout(location = 1) out highp vec4 out_var_SV_Target1;

float _104;
vec4 _105;

void main()
{
    bool _125 = u_FragSettings.u_BaseColorUVSet >= 0;
    highp vec4 _150;
    if (_125)
    {
        highp vec3 _138;
        if (_125)
        {
            bvec2 _133 = bvec2(u_FragSettings.u_BaseColorUVSet < 1);
            _138 = vec3(vec2(_133.x ? varying_TEXCOORD0.x : varying_TEXCOORD1.x, _133.y ? varying_TEXCOORD0.y : varying_TEXCOORD1.y), 1.0);
        }
        else
        {
            _138 = vec3(0.0);
        }
        highp vec4 _141 = texture(SPIRV_Cross_CombinedbaseColorTexturebaseColorSampler, _138.xy);
        _150 = u_FragSettings.u_BaseColorFactor * vec4(pow(_141.xyz, vec3(2.2000000476837158203125)), _141.w);
    }
    else
    {
        _150 = u_FragSettings.u_BaseColorFactor;
    }
    highp vec4 _153 = _150 * u_FragSettings.u_tint;
    highp vec3 _155 = -normalize(varying_POSITION1);
    bool _158 = u_FragSettings.u_NormalUVSet >= 0;
    highp vec2 _164;
    if (_158)
    {
        bvec2 _162 = bvec2(u_FragSettings.u_NormalUVSet < 1);
        _164 = vec2(_162.x ? varying_TEXCOORD0.x : varying_TEXCOORD1.x, _162.y ? varying_TEXCOORD0.y : varying_TEXCOORD1.y);
    }
    else
    {
        _164 = vec2(0.0);
    }
    highp vec3 _167 = vec3(_164, 0.0);
    highp vec3 _168 = dFdx(_167);
    highp vec3 _169 = dFdy(_167);
    highp vec3 _170 = dFdx(varying_POSITION1);
    highp float _171 = _169.y;
    highp vec3 _173 = dFdy(varying_POSITION1);
    highp float _174 = _168.y;
    highp vec3 _183 = ((_170 * _171) - (_173 * _174)) / vec3((_168.x * _171) - (_169.x * _174));
    highp vec3 _184 = normalize(varying_NORMAL);
    highp vec3 _188 = normalize(_183 - (_184 * dot(_184, _183)));
    highp vec3 _207;
    if (_158)
    {
        _207 = mat3(_188, cross(_184, _188), _184) * normalize(((texture(SPIRV_Cross_CombinednormalTexturenormalSampler, _164).xyz * 2.0) - vec3(1.0)) * vec3(u_FragSettings.u_NormalScale, u_FragSettings.u_NormalScale, 1.0));
    }
    else
    {
        _207 = _184;
    }
    highp vec3 _208 = _153.xyz;
    bool _215 = u_FragSettings.u_MetallicRoughnessUVSet >= 0;
    highp float _236;
    highp float _237;
    if (_215)
    {
        highp vec3 _228;
        if (_215)
        {
            bvec2 _223 = bvec2(u_FragSettings.u_MetallicRoughnessUVSet < 1);
            _228 = vec3(vec2(_223.x ? varying_TEXCOORD0.x : varying_TEXCOORD1.x, _223.y ? varying_TEXCOORD0.y : varying_TEXCOORD1.y), 1.0);
        }
        else
        {
            _228 = vec3(0.0);
        }
        highp vec4 _231 = texture(SPIRV_Cross_CombinedmetallicRoughnessTexturemetallicRoughnessSampler, _228.xy);
        _236 = u_FragSettings.u_RoughnessFactor * _231.y;
        _237 = u_FragSettings.u_MetallicFactor * _231.z;
    }
    else
    {
        _236 = u_FragSettings.u_RoughnessFactor;
        _237 = u_FragSettings.u_MetallicFactor;
    }
    highp vec3 _239 = vec3(_237);
    highp vec3 _240 = mix(_208 * vec3(0.959999978542327880859375), vec3(0.0), _239);
    highp vec3 _241 = mix(vec3(0.039999999105930328369140625), _208, _239);
    highp float _242 = clamp(_236, 0.0, 1.0);
    highp float _243 = _242 * _242;
    highp vec3 _251 = vec3(clamp(max(max(_241.x, _241.y), _241.z) * 50.0, 0.0, 1.0));
    highp vec3 _258;
    highp vec3 _261;
    _258 = vec3(0.0);
    _261 = pow(_240 * u_FragSettings.u_ambience.xyz, vec3(2.2000000476837158203125));
    highp vec3 _259;
    highp vec3 _262;
    for (int _263 = 0; _263 < u_FragSettings.u_lightCount; _258 = _259, _261 = _262, _263++)
    {
        highp float _306;
        highp vec3 _307;
        if (u_FragSettings.u_Lights[_263].type != 0)
        {
            highp vec3 _291 = u_FragSettings.u_Lights[_263].position - varying_POSITION0;
            highp float _292 = length(_291);
            highp float _305;
            do
            {
                if (u_FragSettings.u_Lights[_263].range <= 0.0)
                {
                    _305 = 1.0;
                    break;
                }
                _305 = max(min(1.0 - pow(_292 / u_FragSettings.u_Lights[_263].range, 4.0), 1.0), 0.0) / pow(_292, 2.0);
                break;
            } while(false);
            _306 = _305;
            _307 = _291;
        }
        else
        {
            _306 = 1.0;
            _307 = -u_FragSettings.u_Lights[_263].direction;
        }
        highp float _325;
        if (u_FragSettings.u_Lights[_263].type == 2)
        {
            highp float _324;
            do
            {
                highp float _316 = dot(normalize(u_FragSettings.u_Lights[_263].direction), normalize(-_307));
                if (_316 > u_FragSettings.u_Lights[_263].outerConeCos)
                {
                    if (_316 < u_FragSettings.u_Lights[_263].innerConeCos)
                    {
                        _324 = smoothstep(u_FragSettings.u_Lights[_263].outerConeCos, u_FragSettings.u_Lights[_263].innerConeCos, _316);
                        break;
                    }
                    _324 = 1.0;
                    break;
                }
                _324 = 0.0;
                break;
            } while(false);
            _325 = _324;
        }
        else
        {
            _325 = 1.0;
        }
        highp vec3 _329 = normalize(_307);
        highp vec3 _331 = normalize(_329 + _155);
        highp float _333 = clamp(dot(_207, _329), 0.0, 1.0);
        highp float _335 = clamp(dot(_207, _155), 0.0, 1.0);
        highp float _337 = clamp(dot(_207, _331), 0.0, 1.0);
        if ((_333 > 0.0) || (_335 > 0.0))
        {
            highp float _359;
            highp vec3 _345 = (u_FragSettings.u_Lights[_263].color * ((_306 * _325) * u_FragSettings.u_Lights[_263].intensity)) * _333;
            highp vec3 _351 = _241 + ((_251 - _241) * pow(clamp(1.0 - clamp(dot(_155, _331), 0.0, 1.0), 0.0, 1.0), 5.0));
            highp float _376;
            do
            {
                _359 = _243 * _243;
                highp float _361 = 1.0 - _359;
                highp float _371 = (_333 * sqrt(((_335 * _335) * _361) + _359)) + (_335 * sqrt(((_333 * _333) * _361) + _359));
                if (_371 > 0.0)
                {
                    _376 = 0.5 / _371;
                    break;
                }
                _376 = 0.0;
                break;
            } while(false);
            highp float _380 = ((_337 * _337) * (_359 - 1.0)) + 1.0;
            _259 = _258 + (_345 * ((_351 * _376) * (_359 / ((3.1415927410125732421875 * _380) * _380))));
            _262 = _261 + (_345 * ((vec3(1.0) - _351) * (_240 * vec3(0.3183098733425140380859375))));
        }
        else
        {
            _259 = _258;
            _262 = _261;
        }
    }
    bool _392 = u_FragSettings.u_EmissiveUVSet >= 0;
    highp vec3 _417;
    if (_392)
    {
        highp vec3 _405;
        if (_392)
        {
            bvec2 _400 = bvec2(u_FragSettings.u_EmissiveUVSet < 1);
            _405 = vec3(vec2(_400.x ? varying_TEXCOORD0.x : varying_TEXCOORD1.x, _400.y ? varying_TEXCOORD0.y : varying_TEXCOORD1.y), 1.0);
        }
        else
        {
            _405 = vec3(0.0);
        }
        _417 = u_FragSettings.u_EmissiveFactor * vec4(pow(texture(SPIRV_Cross_CombinedemissiveTextureemissiveSampler, _405.xy).xyz, vec3(2.2000000476837158203125)), _104).xyz;
    }
    else
    {
        _417 = u_FragSettings.u_EmissiveFactor;
    }
    highp vec3 _419 = (_417 + _261) + _258;
    bool _422 = u_FragSettings.u_OcclusionUVSet >= 0;
    highp vec3 _445;
    if (_422)
    {
        highp vec3 _435;
        if (_422)
        {
            bvec2 _430 = bvec2(u_FragSettings.u_OcclusionUVSet < 1);
            _435 = vec3(vec2(_430.x ? varying_TEXCOORD0.x : varying_TEXCOORD1.x, _430.y ? varying_TEXCOORD0.y : varying_TEXCOORD1.y), 1.0);
        }
        else
        {
            _435 = vec3(0.0);
        }
        _445 = mix(_419, _419 * texture(SPIRV_Cross_CombinedocclusionTextureocclusionSampler, _435.xy).x, vec3(u_FragSettings.u_OcclusionStrength));
    }
    else
    {
        _445 = _419;
    }
    highp vec4 _458;
    if (u_FragSettings.u_alphaMode == 1)
    {
        if (_153.w < u_FragSettings.u_AlphaCutoff)
        {
            discard;
        }
        highp vec4 _457 = _105;
        _457.w = 1.0;
        _458 = _457;
    }
    else
    {
        _458 = _153;
    }
    highp float _484;
    do
    {
        if (varying_TEXCOORD2.y != 0.0)
        {
            _484 = varying_TEXCOORD2.x / varying_TEXCOORD2.y;
            break;
        }
        _484 = log2(varying_TEXCOORD2.x) * (1.0 / log2(u_cameraPlaneParams.s_CameraFarPlane + 1.0));
        break;
    } while(false);
    highp vec4 _501;
    if (u_FragSettings.u_alphaMode == 2)
    {
        highp vec4 _500 = vec4(varying_TEXCOORD3.x, ((step(0.0, 0.0) * 2.0) - 1.0) * _484, 0.0, 0.0);
        _500.w = varying_TEXCOORD3.y;
        _501 = _500;
    }
    else
    {
        _501 = vec4(varying_TEXCOORD3.x, ((step(0.0, 1.0) * 2.0) - 1.0) * _484, 1.0, 1.0);
    }
    out_var_SV_Target0 = mix(vec4(pow(_445 * u_FragSettings.u_Exposure, vec3(0.454545438289642333984375)), _458.w), vec4(u_FragSettings.u_colourOnly.xyz, u_FragSettings.u_tint.w), vec4(u_FragSettings.u_colourOnly.w));
    out_var_SV_Target1 = _501;
    gl_FragDepth = _484;
}


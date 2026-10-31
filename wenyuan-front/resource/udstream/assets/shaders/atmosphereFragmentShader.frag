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
    highp vec4 u_camera;
    highp vec4 u_whitePoint;
    highp vec4 u_earthCenter;
    highp vec4 u_sunDirection;
    highp vec4 u_sunSize;
} u_fragParams;

uniform highp sampler2D SPIRV_Cross_CombinedsceneColourTexturesceneColourSampler;
uniform highp sampler2D SPIRV_Cross_CombinedsceneNormalTexturesceneNormalSampler;
uniform highp sampler2D SPIRV_Cross_CombinedsceneDepthTexturesceneDepthSampler;
uniform highp sampler2D SPIRV_Cross_CombinedirradianceTextureirradianceSampler;
uniform highp sampler2D SPIRV_Cross_CombinedtransmittanceTexturetransmittanceSampler;
uniform highp sampler3D SPIRV_Cross_CombinedscatteringTexturescatteringSampler;

in highp vec2 varying_TEXCOORD0;
in highp vec3 varying_TEXCOORD1;
layout(location = 0) out highp vec4 out_var_SV_Target0;
layout(location = 1) out highp vec4 out_var_SV_Target1;

vec4 _108;

void main()
{
    highp float _116 = u_fragParams.u_earthCenter.w + 60000.0;
    highp vec3 _129 = normalize(varying_TEXCOORD1);
    highp vec4 _133 = texture(SPIRV_Cross_CombinedsceneColourTexturesceneColourSampler, varying_TEXCOORD0);
    highp vec4 _137 = texture(SPIRV_Cross_CombinedsceneNormalTexturesceneNormalSampler, varying_TEXCOORD0);
    highp vec4 _141 = texture(SPIRV_Cross_CombinedsceneDepthTexturesceneDepthSampler, varying_TEXCOORD0);
    highp float _142 = _141.x;
    highp float _147 = u_cameraPlaneParams.s_CameraFarPlane - u_cameraPlaneParams.s_CameraNearPlane;
    highp float _159 = _137.z;
    highp float _160 = _137.w;
    highp vec2 _165 = _137.zw;
    highp vec3 _171 = vec3(_159, _160, float(int(sign(_137.y))) * sqrt(max(0.0, 1.0 - dot(_165, _165))));
    highp vec3 _174 = pow(abs(_133.xyz), vec3(2.2000000476837158203125));
    highp float _185 = (((u_cameraPlaneParams.u_clipZFar - u_cameraPlaneParams.u_clipZNear) * u_cameraPlaneParams.s_CameraNearPlane) / ((u_cameraPlaneParams.s_CameraFarPlane + u_cameraPlaneParams.s_CameraNearPlane) - (((u_cameraPlaneParams.s_CameraFarPlane / _147) + (((u_cameraPlaneParams.s_CameraFarPlane * u_cameraPlaneParams.s_CameraNearPlane) / (u_cameraPlaneParams.s_CameraNearPlane - u_cameraPlaneParams.s_CameraFarPlane)) / (pow(2.0, _142 * log2(u_cameraPlaneParams.s_CameraFarPlane + 1.0)) - 1.0))) * _147))) * u_cameraPlaneParams.s_CameraFarPlane;
    highp vec3 _187 = u_fragParams.u_camera.xyz + (_129 * _185);
    highp vec3 _188 = u_fragParams.u_camera.xyz - u_fragParams.u_earthCenter.xyz;
    highp float _189 = dot(_188, _129);
    highp float _191 = _189 * _189;
    highp float _193 = u_fragParams.u_earthCenter.w * u_fragParams.u_earthCenter.w;
    highp float _194 = _193 - (dot(_188, _188) - _191);
    highp float _201;
    if (_194 > 0.0)
    {
        _201 = (-_189) - sqrt(_194);
    }
    else
    {
        _201 = 0.0;
    }
    bool _202 = _201 > 0.0;
    highp vec3 _207;
    if (_202)
    {
        _207 = u_fragParams.u_camera.xyz + (_129 * _201);
    }
    else
    {
        _207 = _187;
    }
    bool _208 = _185 < 20700000.0;
    highp vec3 _818;
    highp vec3 _819;
    if (_208)
    {
        highp vec3 _224;
        if ((_159 == 0.0) && (_160 == 0.0))
        {
            _224 = normalize(_187 - u_fragParams.u_earthCenter.xyz);
        }
        else
        {
            bvec3 _220 = bvec3((_159 >= 0.99989998340606689453125) && (_160 >= 0.99989998340606689453125));
            _224 = vec3(_220.x ? u_fragParams.u_sunDirection.xyz.x : _171.x, _220.y ? u_fragParams.u_sunDirection.xyz.y : _171.y, _220.z ? u_fragParams.u_sunDirection.xyz.z : _171.z);
        }
        highp vec3 _225 = _187 - u_fragParams.u_earthCenter.xyz;
        highp float _228 = length(_225);
        highp float _230 = dot(_225, u_fragParams.u_sunDirection.xyz) / _228;
        highp float _232 = _116 - u_fragParams.u_earthCenter.w;
        highp vec4 _243 = texture(SPIRV_Cross_CombinedirradianceTextureirradianceSampler, vec2(0.0078125 + (((_230 * 0.5) + 0.5) * 0.984375), 0.03125 + (((_228 - u_fragParams.u_earthCenter.w) / _232) * 0.9375)));
        highp float _250 = u_fragParams.u_earthCenter.w / _228;
        highp float _256 = _116 * _116;
        highp float _258 = sqrt(_256 - _193);
        highp float _259 = _228 * _228;
        highp float _262 = sqrt(max(_259 - _193, 0.0));
        highp float _273 = _116 - _228;
        highp vec4 _286 = texture(SPIRV_Cross_CombinedtransmittanceTexturetransmittanceSampler, vec2(0.001953125 + (((max(((-_228) * _230) + sqrt(max((_259 * ((_230 * _230) - 1.0)) + _256, 0.0)), 0.0) - _273) / ((_262 + _258) - _273)) * 0.99609375), 0.0078125 + ((_262 / _258) * 0.984375)));
        highp vec3 _301 = (_174.xyz * 0.3183098733425140380859375) * ((vec3(1.47399997711181640625, 1.85039997100830078125, 1.91198003292083740234375) * max((_286.xyz * smoothstep(_250 * (-0.004674999974668025970458984375), _250 * 0.004674999974668025970458984375, _230 - (-sqrt(max(1.0 - (_250 * _250), 0.0))))) * max(dot(_224, u_fragParams.u_sunDirection.xyz), 0.0), vec3(0.001000000047497451305389404296875))) + ((_243.xyz * (1.0 + (dot(_224, _225) / _228))) * 0.5));
        highp float _303 = max(0.0, min(0.0, _185));
        highp vec3 _306 = normalize(_225 - _188);
        highp float _307 = length(_188);
        highp float _308 = dot(_188, _306);
        highp float _316 = (-_308) - sqrt(max(((_308 * _308) - (_307 * _307)) + _256, 0.0));
        bool _317 = _316 > 0.0;
        highp vec3 _323;
        highp float _324;
        if (_317)
        {
            _323 = _188 + (_306 * _316);
            _324 = _308 + _316;
        }
        else
        {
            _323 = _188;
            _324 = _308;
        }
        highp float _343;
        highp float _325 = _317 ? _116 : _307;
        highp float _326 = _324 / _325;
        highp float _327 = dot(_323, u_fragParams.u_sunDirection.xyz);
        highp float _328 = _327 / _325;
        highp float _329 = dot(_306, u_fragParams.u_sunDirection.xyz);
        highp float _331 = length(_225 - _323);
        highp float _333 = _325 * _325;
        highp float _336 = _333 * ((_326 * _326) - 1.0);
        bool _339 = (_326 < 0.0) && ((_336 + _193) >= 0.0);
        highp vec3 _468;
        do
        {
            _343 = (2.0 * _325) * _326;
            highp float _348 = clamp(sqrt((_331 * (_331 + _343)) + _333), u_fragParams.u_earthCenter.w, _116);
            highp float _351 = clamp((_324 + _331) / _348, -1.0, 1.0);
            if (_339)
            {
                highp float _409 = -_351;
                highp float _410 = _348 * _348;
                highp float _413 = sqrt(max(_410 - _193, 0.0));
                highp float _424 = _116 - _348;
                highp float _438 = -_326;
                highp float _441 = sqrt(max(_333 - _193, 0.0));
                highp float _452 = _116 - _325;
                _468 = min(texture(SPIRV_Cross_CombinedtransmittanceTexturetransmittanceSampler, vec2(0.001953125 + (((max(((-_348) * _409) + sqrt(max((_410 * ((_409 * _409) - 1.0)) + _256, 0.0)), 0.0) - _424) / ((_413 + _258) - _424)) * 0.99609375), 0.0078125 + ((_413 / _258) * 0.984375))).xyz / texture(SPIRV_Cross_CombinedtransmittanceTexturetransmittanceSampler, vec2(0.001953125 + (((max(((-_325) * _438) + sqrt(max((_333 * ((_438 * _438) - 1.0)) + _256, 0.0)), 0.0) - _452) / ((_441 + _258) - _452)) * 0.99609375), 0.0078125 + ((_441 / _258) * 0.984375))).xyz, vec3(1.0));
                break;
            }
            else
            {
                highp float _357 = sqrt(max(_333 - _193, 0.0));
                highp float _365 = _116 - _325;
                highp float _379 = _348 * _348;
                highp float _382 = sqrt(max(_379 - _193, 0.0));
                highp float _393 = _116 - _348;
                _468 = min(texture(SPIRV_Cross_CombinedtransmittanceTexturetransmittanceSampler, vec2(0.001953125 + (((max(((-_325) * _326) + sqrt(max(_336 + _256, 0.0)), 0.0) - _365) / ((_357 + _258) - _365)) * 0.99609375), 0.0078125 + ((_357 / _258) * 0.984375))).xyz / texture(SPIRV_Cross_CombinedtransmittanceTexturetransmittanceSampler, vec2(0.001953125 + (((max(((-_348) * _351) + sqrt(max((_379 * ((_351 * _351) - 1.0)) + _256, 0.0)), 0.0) - _393) / ((_382 + _258) - _393)) * 0.99609375), 0.0078125 + ((_382 / _258) * 0.984375))).xyz, vec3(1.0));
                break;
            }
        } while(false);
        highp float _471 = sqrt(max(_333 - _193, 0.0));
        highp float _472 = _471 / _258;
        highp float _474 = 0.015625 + (_472 * 0.96875);
        highp float _477 = ((_324 * _324) - _333) + _193;
        highp float _510;
        if (_339)
        {
            highp float _500 = _325 - u_fragParams.u_earthCenter.w;
            _510 = 0.5 - (0.5 * (0.0078125 + (((_471 == _500) ? 0.0 : ((((-_324) - sqrt(max(_477, 0.0))) - _500) / (_471 - _500))) * 0.984375)));
        }
        else
        {
            highp float _487 = _116 - _325;
            _510 = 0.5 + (0.5 * (0.0078125 + (((((-_324) + sqrt(max(_477 + (_258 * _258), 0.0))) - _487) / ((_471 + _258) - _487)) * 0.984375)));
        }
        highp float _515 = -u_fragParams.u_earthCenter.w;
        highp float _522 = _258 - _232;
        highp float _523 = (max((_515 * _328) + sqrt(max((_193 * ((_328 * _328) - 1.0)) + _256, 0.0)), 0.0) - _232) / _522;
        highp float _525 = (0.415823996067047119140625 * u_fragParams.u_earthCenter.w) / _522;
        highp float _532 = 0.015625 + ((max(1.0 - (_523 / _525), 0.0) / (1.0 + _523)) * 0.96875);
        highp float _534 = (_329 + 1.0) * 3.5;
        highp float _535 = floor(_534);
        highp float _536 = _534 - _535;
        highp float _540 = _535 + 1.0;
        highp vec4 _546 = texture(SPIRV_Cross_CombinedscatteringTexturescatteringSampler, vec3((_535 + _532) * 0.125, _510, _474));
        highp float _547 = 1.0 - _536;
        highp vec4 _550 = texture(SPIRV_Cross_CombinedscatteringTexturescatteringSampler, vec3((_540 + _532) * 0.125, _510, _474));
        highp vec4 _552 = (_546 * _547) + (_550 * _536);
        highp vec3 _553 = _552.xyz;
        highp vec3 _566;
        do
        {
            highp float _556 = _552.x;
            if (_556 == 0.0)
            {
                _566 = vec3(0.0);
                break;
            }
            _566 = (((_553 * _552.w) / vec3(_556)) * 1.5) * vec3(0.66666662693023681640625, 0.28571426868438720703125, 0.121212117373943328857421875);
            break;
        } while(false);
        highp float _568 = max(_331 - _303, 0.0);
        highp float _573 = clamp(sqrt((_568 * (_568 + _343)) + _333), u_fragParams.u_earthCenter.w, _116);
        highp float _574 = _324 + _568;
        highp float _577 = (_327 + (_568 * _329)) / _573;
        highp float _578 = _573 * _573;
        highp float _581 = sqrt(max(_578 - _193, 0.0));
        highp float _582 = _581 / _258;
        highp float _584 = 0.015625 + (_582 * 0.96875);
        highp float _587 = ((_574 * _574) - _578) + _193;
        highp float _620;
        if (_339)
        {
            highp float _610 = _573 - u_fragParams.u_earthCenter.w;
            _620 = 0.5 - (0.5 * (0.0078125 + (((_581 == _610) ? 0.0 : ((((-_574) - sqrt(max(_587, 0.0))) - _610) / (_581 - _610))) * 0.984375)));
        }
        else
        {
            highp float _597 = _116 - _573;
            _620 = 0.5 + (0.5 * (0.0078125 + (((((-_574) + sqrt(max(_587 + (_258 * _258), 0.0))) - _597) / ((_581 + _258) - _597)) * 0.984375)));
        }
        highp float _631 = (max((_515 * _577) + sqrt(max((_193 * ((_577 * _577) - 1.0)) + _256, 0.0)), 0.0) - _232) / _522;
        highp float _638 = 0.015625 + ((max(1.0 - (_631 / _525), 0.0) / (1.0 + _631)) * 0.96875);
        highp vec4 _646 = texture(SPIRV_Cross_CombinedscatteringTexturescatteringSampler, vec3((_535 + _638) * 0.125, _620, _584));
        highp vec4 _649 = texture(SPIRV_Cross_CombinedscatteringTexturescatteringSampler, vec3((_540 + _638) * 0.125, _620, _584));
        highp vec4 _651 = (_646 * _547) + (_649 * _536);
        highp vec3 _652 = _651.xyz;
        highp vec3 _665;
        do
        {
            highp float _655 = _651.x;
            if (_655 == 0.0)
            {
                _665 = vec3(0.0);
                break;
            }
            _665 = (((_652 * _651.w) / vec3(_655)) * 1.5) * vec3(0.66666662693023681640625, 0.28571426868438720703125, 0.121212117373943328857421875);
            break;
        } while(false);
        highp vec3 _772;
        if (_303 > 0.0)
        {
            highp vec3 _771;
            do
            {
                highp float _672 = clamp(_574 / _573, -1.0, 1.0);
                if (_339)
                {
                    highp float _721 = -_672;
                    highp float _732 = _116 - _573;
                    highp float _745 = -_326;
                    highp float _756 = _116 - _325;
                    _771 = min(texture(SPIRV_Cross_CombinedtransmittanceTexturetransmittanceSampler, vec2(0.001953125 + (((max(((-_573) * _721) + sqrt(max((_578 * ((_721 * _721) - 1.0)) + _256, 0.0)), 0.0) - _732) / ((_581 + _258) - _732)) * 0.99609375), 0.0078125 + (_582 * 0.984375))).xyz / texture(SPIRV_Cross_CombinedtransmittanceTexturetransmittanceSampler, vec2(0.001953125 + (((max(((-_325) * _745) + sqrt(max((_333 * ((_745 * _745) - 1.0)) + _256, 0.0)), 0.0) - _756) / ((_471 + _258) - _756)) * 0.99609375), 0.0078125 + (_472 * 0.984375))).xyz, vec3(1.0));
                    break;
                }
                else
                {
                    highp float _683 = _116 - _325;
                    highp float _706 = _116 - _573;
                    _771 = min(texture(SPIRV_Cross_CombinedtransmittanceTexturetransmittanceSampler, vec2(0.001953125 + (((max(((-_325) * _326) + sqrt(max(_336 + _256, 0.0)), 0.0) - _683) / ((_471 + _258) - _683)) * 0.99609375), 0.0078125 + (_472 * 0.984375))).xyz / texture(SPIRV_Cross_CombinedtransmittanceTexturetransmittanceSampler, vec2(0.001953125 + (((max(((-_573) * _672) + sqrt(max((_578 * ((_672 * _672) - 1.0)) + _256, 0.0)), 0.0) - _706) / ((_581 + _258) - _706)) * 0.99609375), 0.0078125 + (_582 * 0.984375))).xyz, vec3(1.0));
                    break;
                }
            } while(false);
            _772 = _771;
        }
        else
        {
            _772 = _468;
        }
        highp vec3 _774 = _553 - (_772 * _652);
        highp vec3 _776 = _566 - (_772 * _665);
        highp float _777 = _776.x;
        highp float _778 = _774.x;
        highp vec3 _793;
        do
        {
            if (_778 == 0.0)
            {
                _793 = vec3(0.0);
                break;
            }
            _793 = (((vec4(_778, _774.yz, _777).xyz * _777) / vec3(_778)) * 1.5) * vec3(0.66666662693023681640625, 0.28571426868438720703125, 0.121212117373943328857421875);
            break;
        } while(false);
        highp float _797 = 1.0 + (_329 * _329);
        highp vec3 _808 = (_301 * _468) + ((_774 * (0.0596831031143665313720703125 * _797)) + ((_793 * smoothstep(0.0, 0.00999999977648258209228515625, _328)) * ((0.01627720706164836883544921875 * _797) / pow(1.6400001049041748046875 - (1.60000002384185791015625 * _329), 1.5))));
        bvec3 _816 = bvec3(((_159 >= 0.99989998340606689453125) && (_160 >= 0.99989998340606689453125)) && (u_fragParams.u_sunSize.z < 0.5));
        _818 = _224;
        _819 = vec3(_816.x ? _808.x : _301.x, _816.y ? _808.y : _301.y, _816.z ? _808.z : _301.z);
    }
    else
    {
        _818 = _171;
        _819 = vec3(0.0);
    }
    highp vec3 _1297;
    if (_202)
    {
        highp vec3 _823 = _207 - u_fragParams.u_earthCenter.xyz;
        highp float _826 = length(_823);
        highp float _828 = dot(_823, u_fragParams.u_sunDirection.xyz) / _826;
        highp float _830 = _116 - u_fragParams.u_earthCenter.w;
        highp vec4 _841 = texture(SPIRV_Cross_CombinedirradianceTextureirradianceSampler, vec2(0.0078125 + (((_828 * 0.5) + 0.5) * 0.984375), 0.03125 + (((_826 - u_fragParams.u_earthCenter.w) / _830) * 0.9375)));
        highp float _848 = u_fragParams.u_earthCenter.w / _826;
        highp float _854 = _116 * _116;
        highp float _856 = sqrt(_854 - _193);
        highp float _857 = _826 * _826;
        highp float _860 = sqrt(max(_857 - _193, 0.0));
        highp float _871 = _116 - _826;
        highp vec4 _884 = texture(SPIRV_Cross_CombinedtransmittanceTexturetransmittanceSampler, vec2(0.001953125 + (((max(((-_826) * _828) + sqrt(max((_857 * ((_828 * _828) - 1.0)) + _854, 0.0)), 0.0) - _871) / ((_860 + _856) - _871)) * 0.99609375), 0.0078125 + ((_860 / _856) * 0.984375)));
        highp vec3 _902 = normalize(_823 - _188);
        highp float _903 = length(_188);
        highp float _904 = dot(_188, _902);
        highp float _912 = (-_904) - sqrt(max(((_904 * _904) - (_903 * _903)) + _854, 0.0));
        bool _913 = _912 > 0.0;
        highp vec3 _919;
        highp float _920;
        if (_913)
        {
            _919 = _188 + (_902 * _912);
            _920 = _904 + _912;
        }
        else
        {
            _919 = _188;
            _920 = _904;
        }
        highp float _939;
        highp float _921 = _913 ? _116 : _903;
        highp float _922 = _920 / _921;
        highp float _923 = dot(_919, u_fragParams.u_sunDirection.xyz);
        highp float _924 = _923 / _921;
        highp float _925 = dot(_902, u_fragParams.u_sunDirection.xyz);
        highp float _927 = length(_823 - _919);
        highp float _929 = _921 * _921;
        highp float _932 = _929 * ((_922 * _922) - 1.0);
        bool _935 = (_922 < 0.0) && ((_932 + _193) >= 0.0);
        highp vec3 _1064;
        do
        {
            _939 = (2.0 * _921) * _922;
            highp float _944 = clamp(sqrt((_927 * (_927 + _939)) + _929), u_fragParams.u_earthCenter.w, _116);
            highp float _947 = clamp((_920 + _927) / _944, -1.0, 1.0);
            if (_935)
            {
                highp float _1005 = -_947;
                highp float _1006 = _944 * _944;
                highp float _1009 = sqrt(max(_1006 - _193, 0.0));
                highp float _1020 = _116 - _944;
                highp float _1034 = -_922;
                highp float _1037 = sqrt(max(_929 - _193, 0.0));
                highp float _1048 = _116 - _921;
                _1064 = min(texture(SPIRV_Cross_CombinedtransmittanceTexturetransmittanceSampler, vec2(0.001953125 + (((max(((-_944) * _1005) + sqrt(max((_1006 * ((_1005 * _1005) - 1.0)) + _854, 0.0)), 0.0) - _1020) / ((_1009 + _856) - _1020)) * 0.99609375), 0.0078125 + ((_1009 / _856) * 0.984375))).xyz / texture(SPIRV_Cross_CombinedtransmittanceTexturetransmittanceSampler, vec2(0.001953125 + (((max(((-_921) * _1034) + sqrt(max((_929 * ((_1034 * _1034) - 1.0)) + _854, 0.0)), 0.0) - _1048) / ((_1037 + _856) - _1048)) * 0.99609375), 0.0078125 + ((_1037 / _856) * 0.984375))).xyz, vec3(1.0));
                break;
            }
            else
            {
                highp float _953 = sqrt(max(_929 - _193, 0.0));
                highp float _961 = _116 - _921;
                highp float _975 = _944 * _944;
                highp float _978 = sqrt(max(_975 - _193, 0.0));
                highp float _989 = _116 - _944;
                _1064 = min(texture(SPIRV_Cross_CombinedtransmittanceTexturetransmittanceSampler, vec2(0.001953125 + (((max(((-_921) * _922) + sqrt(max(_932 + _854, 0.0)), 0.0) - _961) / ((_953 + _856) - _961)) * 0.99609375), 0.0078125 + ((_953 / _856) * 0.984375))).xyz / texture(SPIRV_Cross_CombinedtransmittanceTexturetransmittanceSampler, vec2(0.001953125 + (((max(((-_944) * _947) + sqrt(max((_975 * ((_947 * _947) - 1.0)) + _854, 0.0)), 0.0) - _989) / ((_978 + _856) - _989)) * 0.99609375), 0.0078125 + ((_978 / _856) * 0.984375))).xyz, vec3(1.0));
                break;
            }
        } while(false);
        highp float _1067 = sqrt(max(_929 - _193, 0.0));
        highp float _1070 = 0.015625 + ((_1067 / _856) * 0.96875);
        highp float _1073 = ((_920 * _920) - _929) + _193;
        highp float _1106;
        if (_935)
        {
            highp float _1096 = _921 - u_fragParams.u_earthCenter.w;
            _1106 = 0.5 - (0.5 * (0.0078125 + (((_1067 == _1096) ? 0.0 : ((((-_920) - sqrt(max(_1073, 0.0))) - _1096) / (_1067 - _1096))) * 0.984375)));
        }
        else
        {
            highp float _1083 = _116 - _921;
            _1106 = 0.5 + (0.5 * (0.0078125 + (((((-_920) + sqrt(max(_1073 + (_856 * _856), 0.0))) - _1083) / ((_1067 + _856) - _1083)) * 0.984375)));
        }
        highp float _1111 = -u_fragParams.u_earthCenter.w;
        highp float _1118 = _856 - _830;
        highp float _1119 = (max((_1111 * _924) + sqrt(max((_193 * ((_924 * _924) - 1.0)) + _854, 0.0)), 0.0) - _830) / _1118;
        highp float _1121 = (0.415823996067047119140625 * u_fragParams.u_earthCenter.w) / _1118;
        highp float _1128 = 0.015625 + ((max(1.0 - (_1119 / _1121), 0.0) / (1.0 + _1119)) * 0.96875);
        highp float _1130 = (_925 + 1.0) * 3.5;
        highp float _1131 = floor(_1130);
        highp float _1132 = _1130 - _1131;
        highp float _1136 = _1131 + 1.0;
        highp vec4 _1142 = texture(SPIRV_Cross_CombinedscatteringTexturescatteringSampler, vec3((_1131 + _1128) * 0.125, _1106, _1070));
        highp float _1143 = 1.0 - _1132;
        highp vec4 _1146 = texture(SPIRV_Cross_CombinedscatteringTexturescatteringSampler, vec3((_1136 + _1128) * 0.125, _1106, _1070));
        highp vec4 _1148 = (_1142 * _1143) + (_1146 * _1132);
        highp vec3 _1149 = _1148.xyz;
        highp vec3 _1162;
        do
        {
            highp float _1152 = _1148.x;
            if (_1152 == 0.0)
            {
                _1162 = vec3(0.0);
                break;
            }
            _1162 = (((_1149 * _1148.w) / vec3(_1152)) * 1.5) * vec3(0.66666662693023681640625, 0.28571426868438720703125, 0.121212117373943328857421875);
            break;
        } while(false);
        highp float _1163 = max(_927, 0.0);
        highp float _1168 = clamp(sqrt((_1163 * (_1163 + _939)) + _929), u_fragParams.u_earthCenter.w, _116);
        highp float _1169 = _920 + _1163;
        highp float _1172 = (_923 + (_1163 * _925)) / _1168;
        highp float _1173 = _1168 * _1168;
        highp float _1176 = sqrt(max(_1173 - _193, 0.0));
        highp float _1179 = 0.015625 + ((_1176 / _856) * 0.96875);
        highp float _1182 = ((_1169 * _1169) - _1173) + _193;
        highp float _1215;
        if (_935)
        {
            highp float _1205 = _1168 - u_fragParams.u_earthCenter.w;
            _1215 = 0.5 - (0.5 * (0.0078125 + (((_1176 == _1205) ? 0.0 : ((((-_1169) - sqrt(max(_1182, 0.0))) - _1205) / (_1176 - _1205))) * 0.984375)));
        }
        else
        {
            highp float _1192 = _116 - _1168;
            _1215 = 0.5 + (0.5 * (0.0078125 + (((((-_1169) + sqrt(max(_1182 + (_856 * _856), 0.0))) - _1192) / ((_1176 + _856) - _1192)) * 0.984375)));
        }
        highp float _1226 = (max((_1111 * _1172) + sqrt(max((_193 * ((_1172 * _1172) - 1.0)) + _854, 0.0)), 0.0) - _830) / _1118;
        highp float _1233 = 0.015625 + ((max(1.0 - (_1226 / _1121), 0.0) / (1.0 + _1226)) * 0.96875);
        highp vec4 _1241 = texture(SPIRV_Cross_CombinedscatteringTexturescatteringSampler, vec3((_1131 + _1233) * 0.125, _1215, _1179));
        highp vec4 _1244 = texture(SPIRV_Cross_CombinedscatteringTexturescatteringSampler, vec3((_1136 + _1233) * 0.125, _1215, _1179));
        highp vec4 _1246 = (_1241 * _1143) + (_1244 * _1132);
        highp vec3 _1247 = _1246.xyz;
        highp vec3 _1260;
        do
        {
            highp float _1250 = _1246.x;
            if (_1250 == 0.0)
            {
                _1260 = vec3(0.0);
                break;
            }
            _1260 = (((_1247 * _1246.w) / vec3(_1250)) * 1.5) * vec3(0.66666662693023681640625, 0.28571426868438720703125, 0.121212117373943328857421875);
            break;
        } while(false);
        highp vec3 _1262 = _1149 - (_1064 * _1247);
        highp vec3 _1264 = _1162 - (_1064 * _1260);
        highp float _1265 = _1264.x;
        highp float _1266 = _1262.x;
        highp vec3 _1281;
        do
        {
            if (_1266 == 0.0)
            {
                _1281 = vec3(0.0);
                break;
            }
            _1281 = (((vec4(_1266, _1262.yz, _1265).xyz * _1265) / vec3(_1266)) * 1.5) * vec3(0.66666662693023681640625, 0.28571426868438720703125, 0.121212117373943328857421875);
            break;
        } while(false);
        highp float _1285 = 1.0 + (_925 * _925);
        _1297 = (((_174.xyz * 0.3183098733425140380859375) * ((vec3(1.47399997711181640625, 1.85039997100830078125, 1.91198003292083740234375) * max((_884.xyz * smoothstep(_848 * (-0.004674999974668025970458984375), _848 * 0.004674999974668025970458984375, _828 - (-sqrt(max(1.0 - (_848 * _848), 0.0))))) * max(dot(_818, u_fragParams.u_sunDirection.xyz), 0.0), vec3(0.001000000047497451305389404296875))) + ((_841.xyz * (1.0 + (dot(_818, _823) / _826))) * 0.5))) * _1064) + ((_1262 * (0.0596831031143665313720703125 * _1285)) + ((_1281 * smoothstep(0.0, 0.00999999977648258209228515625, _924)) * ((0.01627720706164836883544921875 * _1285) / pow(1.6400001049041748046875 - (1.60000002384185791015625 * _925), 1.5))));
    }
    else
    {
        _1297 = vec3(0.0);
    }
    highp vec3 _1469;
    highp vec3 _1470;
    do
    {
        highp float _1303 = length(_188);
        highp float _1307 = _116 * _116;
        highp float _1311 = (-_189) - sqrt(max((_191 - (_1303 * _1303)) + _1307, 0.0));
        bool _1312 = _1311 > 0.0;
        highp vec3 _1322;
        highp float _1323;
        if (_1312)
        {
            _1322 = _188 + (_129 * _1311);
            _1323 = _189 + _1311;
        }
        else
        {
            if (_1303 > _116)
            {
                _1469 = vec3(1.0);
                _1470 = vec3(0.0);
                break;
            }
            _1322 = _188;
            _1323 = _189;
        }
        highp float _1324 = _1312 ? _116 : _1303;
        highp float _1325 = _1323 / _1324;
        highp float _1327 = dot(_1322, u_fragParams.u_sunDirection.xyz) / _1324;
        highp float _1328 = dot(_129, u_fragParams.u_sunDirection.xyz);
        highp float _1330 = _1324 * _1324;
        highp float _1333 = _1330 * ((_1325 * _1325) - 1.0);
        bool _1336 = (_1325 < 0.0) && ((_1333 + _193) >= 0.0);
        highp float _1338 = sqrt(_1307 - _193);
        highp float _1341 = sqrt(max(_1330 - _193, 0.0));
        highp float _1349 = _116 - _1324;
        highp float _1352 = (_1341 + _1338) - _1349;
        highp float _1354 = _1341 / _1338;
        highp vec4 _1362 = texture(SPIRV_Cross_CombinedtransmittanceTexturetransmittanceSampler, vec2(0.001953125 + (((max(((-_1324) * _1325) + sqrt(max(_1333 + _1307, 0.0)), 0.0) - _1349) / _1352) * 0.99609375), 0.0078125 + (_1354 * 0.984375)));
        highp vec3 _1363 = _1362.xyz;
        bvec3 _1364 = bvec3(_1336);
        highp float _1367 = 0.015625 + (_1354 * 0.96875);
        highp float _1370 = ((_1323 * _1323) - _1330) + _193;
        highp float _1400;
        if (_1336)
        {
            highp float _1390 = _1324 - u_fragParams.u_earthCenter.w;
            _1400 = 0.5 - (0.5 * (0.0078125 + (((_1341 == _1390) ? 0.0 : ((((-_1323) - sqrt(max(_1370, 0.0))) - _1390) / (_1341 - _1390))) * 0.984375)));
        }
        else
        {
            _1400 = 0.5 + (0.5 * (0.0078125 + (((((-_1323) + sqrt(max(_1370 + (_1338 * _1338), 0.0))) - _1349) / _1352) * 0.984375)));
        }
        highp float _1411 = _116 - u_fragParams.u_earthCenter.w;
        highp float _1413 = _1338 - _1411;
        highp float _1414 = (max(((-u_fragParams.u_earthCenter.w) * _1327) + sqrt(max((_193 * ((_1327 * _1327) - 1.0)) + _1307, 0.0)), 0.0) - _1411) / _1413;
        highp float _1423 = 0.015625 + ((max(1.0 - (_1414 / ((0.415823996067047119140625 * u_fragParams.u_earthCenter.w) / _1413)), 0.0) / (1.0 + _1414)) * 0.96875);
        highp float _1425 = (_1328 + 1.0) * 3.5;
        highp float _1426 = floor(_1425);
        highp float _1427 = _1425 - _1426;
        highp vec4 _1437 = texture(SPIRV_Cross_CombinedscatteringTexturescatteringSampler, vec3((_1426 + _1423) * 0.125, _1400, _1367));
        highp vec4 _1441 = texture(SPIRV_Cross_CombinedscatteringTexturescatteringSampler, vec3(((_1426 + 1.0) + _1423) * 0.125, _1400, _1367));
        highp vec4 _1443 = (_1437 * (1.0 - _1427)) + (_1441 * _1427);
        highp vec3 _1444 = _1443.xyz;
        highp vec3 _1457;
        do
        {
            highp float _1447 = _1443.x;
            if (_1447 == 0.0)
            {
                _1457 = vec3(0.0);
                break;
            }
            _1457 = (((_1444 * _1443.w) / vec3(_1447)) * 1.5) * vec3(0.66666662693023681640625, 0.28571426868438720703125, 0.121212117373943328857421875);
            break;
        } while(false);
        highp float _1459 = 1.0 + (_1328 * _1328);
        _1469 = vec3(_1364.x ? vec3(0.0).x : _1363.x, _1364.y ? vec3(0.0).y : _1363.y, _1364.z ? vec3(0.0).z : _1363.z);
        _1470 = (_1444 * (0.0596831031143665313720703125 * _1459)) + (_1457 * ((0.01627720706164836883544921875 * _1459) / pow(1.6400001049041748046875 - (1.60000002384185791015625 * _1328), 1.5)));
        break;
    } while(false);
    highp vec3 _1478;
    if (dot(_129, u_fragParams.u_sunDirection.xyz) > u_fragParams.u_sunSize.y)
    {
        _1478 = _1470 + (_1469 * vec3(21467.642578125, 26949.611328125, 27846.474609375));
    }
    else
    {
        _1478 = _1470;
    }
    highp vec3 _1501 = pow(abs(vec3(1.0) - exp(((-mix(mix(mix(_1478, _1297, vec3(float(_202))), _819, vec3(float(_208) * min(1.0, 1.0 - smoothstep(0.60000002384185791015625, 0.689999997615814208984375, _142)))), _174.xyz, vec3(u_fragParams.u_sunSize.z))) / u_fragParams.u_whitePoint.xyz) * u_fragParams.u_camera.w)), vec3(0.4545454680919647216796875));
    highp vec4 _1503 = vec4(_1501.x, _1501.y, _1501.z, _108.w);
    _1503.w = 1.0;
    out_var_SV_Target0 = _1503;
    out_var_SV_Target1 = _137;
    gl_FragDepth = _142;
}


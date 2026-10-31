#version 300 es

layout(std140) uniform type_u_EveryObject
{
    layout(row_major) mat4 u_projection;
    layout(row_major) mat4 u_view;
    layout(row_major) mat4 u_viewProjection;
    vec4 u_eyePositions[4];
    vec4 u_colour;
    vec4 u_objectInfo;
    vec4 u_uvOffsetScale;
    vec4 u_demUVOffsetScale;
    vec4 u_worldNormals[4];
    vec4 u_worldBitangents[4];
} u_EveryObject;

uniform highp sampler2D SPIRV_Cross_CombineddemTexturedemSampler;

layout(location = 0) in vec3 in_var_POSITION;
out vec4 varying_COLOR0;
out vec2 varying_TEXCOORD0;
out vec2 varying_TEXCOORD1;
out vec2 varying_TEXCOORD2;
out vec2 varying_TEXCOORD3;
out vec3 varying_TEXCOORD4;
out vec3 varying_TEXCOORD5;
out vec2 varying_TEXCOORD6;

vec2 _59;

void main()
{
    vec2 _63 = in_var_POSITION.xy * 1.0;
    float _65 = _63.x;
    float _66 = floor(_65);
    float _67 = _63.y;
    float _68 = floor(_67);
    float _70 = min(1.0, _66 + 1.0);
    float _75 = _68 * 2.0;
    int _77 = int(_75 + _66);
    int _81 = int(_75 + _70);
    float _84 = min(1.0, _68 + 1.0) * 2.0;
    int _86 = int(_84 + _66);
    int _90 = int(_84 + _70);
    vec4 _93 = vec4(_65 - _66);
    vec4 _96 = vec4(_67 - _68);
    vec4 _98 = normalize(mix(mix(u_EveryObject.u_worldNormals[_77], u_EveryObject.u_worldNormals[_81], _93), mix(u_EveryObject.u_worldNormals[_86], u_EveryObject.u_worldNormals[_90], _93), _96));
    vec3 _99 = _98.xyz;
    vec4 _111 = mix(mix(u_EveryObject.u_eyePositions[_77], u_EveryObject.u_eyePositions[_81], _93), mix(u_EveryObject.u_eyePositions[_86], u_EveryObject.u_eyePositions[_90], _93), _96);
    vec2 _124 = u_EveryObject.u_demUVOffsetScale.xy + (u_EveryObject.u_demUVOffsetScale.zw * in_var_POSITION.xy);
    vec4 _156 = (vec4(mix(_111.xyz, (vec4(_99 * _111.w, 1.0) * u_EveryObject.u_view).xyz, vec3(u_EveryObject.u_objectInfo.z)), 1.0) + ((vec4(_98.xyz, 0.0) * u_EveryObject.u_view) * (textureLod(SPIRV_Cross_CombineddemTexturedemSampler, _124, 0.0).x + (in_var_POSITION.z * u_EveryObject.u_objectInfo.y)))) * u_EveryObject.u_projection;
    vec2 _167 = vec2(_156.zw);
    vec2 _170 = _59;
    _170.x = u_EveryObject.u_objectInfo.x;
    vec2 _194;
    do
    {
        if (u_EveryObject.u_viewProjection[3u].y == 0.0)
        {
            _194 = _167;
            break;
        }
        _194 = vec2(1.0 + _156.w, 0.0);
        break;
    } while(false);
    gl_Position = _156;
    varying_COLOR0 = u_EveryObject.u_colour;
    varying_TEXCOORD0 = u_EveryObject.u_uvOffsetScale.xy + (u_EveryObject.u_uvOffsetScale.zw * in_var_POSITION.xy);
    varying_TEXCOORD1 = _167;
    varying_TEXCOORD2 = _170;
    varying_TEXCOORD3 = _124;
    varying_TEXCOORD4 = _99;
    varying_TEXCOORD5 = normalize(mix(mix(u_EveryObject.u_worldBitangents[_77], u_EveryObject.u_worldBitangents[_81], _93), mix(u_EveryObject.u_worldBitangents[_86], u_EveryObject.u_worldBitangents[_90], _93), _96)).xyz;
    varying_TEXCOORD6 = _194;
}


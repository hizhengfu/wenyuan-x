<script lang="ts">
import { defineComponent, markRaw, nextTick, onMounted, PropType, ref } from 'vue';
import type { LatLngBounds, LayerGroup, LeafletEventHandlerFnMap } from 'leaflet';
import { Functions, InjectionKeys, Utilities } from '@vue-leaflet/vue-leaflet';

const { propsBinder, assertInject, WINDOW_OR_GLOBAL } = Utilities;
const { AddLayerInjection } = InjectionKeys;
const { render } = Functions.Layer;

export interface MaskOptions {
  color?: string;
  weight?: number;
  fillColor?: string;
  fillOpacity?: number;
  interactive?: boolean;
  fitBounds?: boolean;
  restrictBounds?: boolean;
}

export default defineComponent({
  name: 'LMask',
  props: {
    geojson: {
      type: [Object, String],
      default: null,
    },
    color: {
      type: String,
      default: '#3388FF',
    },
    weight: {
      type: Number,
      default: 2,
    },
    fillColor: {
      type: String,
      default: '#ffffff',
    },
    fillOpacity: {
      type: Number,
      default: 0.3,
    },
    interactive: {
      type: Boolean,
      default: false,
    },
    fitBounds: {
      type: Boolean,
      default: true,
    },
    restrictBounds: {
      type: Boolean,
      default: true,
    },
    options: {
      type: Object as PropType<MaskOptions>,
      default: () => ({}),
    },
  },
  emits: ['ready', 'update:bounds'],
  setup(props, context) {
    const leafletObject = ref<LayerGroup>();
    const ready = ref(false);
    const bounds = ref<LatLngBounds>();

    const addLayer = assertInject(AddLayerInjection);

    const getMaskOptions = () => {
      return {
        color: props.color,
        weight: props.weight,
        fillColor: props.fillColor,
        fillOpacity: props.fillOpacity,
        interactive: props.interactive,
        fitBounds: props.fitBounds,
        restrictBounds: props.restrictBounds,
        ...props.options,
      };
    };

    const methods = {
      addGeoJSON(geojson: any) {
        if (!leafletObject.value) return;

        // Reset the mask
        leafletObject.value.clearLayers();

        const L = WINDOW_OR_GLOBAL.L;

        // Create a new mask
        const mask = createMask(geojson);

        if (mask) {
          leafletObject.value.addLayer(mask);

          if (bounds.value && props.fitBounds) {
            const map = (leafletObject.value as any)._map;
            if (map) {
              map.fitBounds(bounds.value);
              if (props.restrictBounds) {
                map.setMaxBounds(bounds.value);
              }
            }
          }
        }
      },
      setGeojson(geojson: any) {
        methods.addGeoJSON(geojson);
      },
    };

    // Create mask layer
    const createMask = (geojson: any) => {
      const L = WINDOW_OR_GLOBAL.L;
      const maskPolygonCoords = [
        [
          [-360, -90],
          [-360, 90],
          [360, 90],
          [360, -90],
        ],
      ];
    //@ts-ignore
      bounds.value = new L.LatLngBounds();

      // Process GeoJSON
      if (typeof geojson === 'string') {
        request(geojson, (json) => {
          processGeoJSON(json, maskPolygonCoords, bounds.value!);
        });
        return;
      } else if (geojson) {
        processGeoJSON(geojson, maskPolygonCoords, bounds.value);
      }

      // Create the polygon with the mask coordinates
      const latlngs = coordsToLatLngs(maskPolygonCoords);
      const polygonOptions = getMaskOptions();

      return new L.Polygon(latlngs, polygonOptions);
    };

    // Process GeoJSON data
    const processGeoJSON = (json: any, maskPolygonCoords: any[], bounds: LatLngBounds) => {
      const L = WINDOW_OR_GLOBAL.L;

      if (L.Util.isArray(json)) {
        for (let i = 0, len = json.length; i < len; i++) {
          processGeoJSON(json[i], maskPolygonCoords, bounds);
        }
        return;
      }

      switch (json.type) {
        case 'FeatureCollection':
          const features = json.features;
          for (let i = 0, len = features.length; i < len; i++) {
            processGeoJSON(features[i], maskPolygonCoords, bounds);
          }
          break;
        case 'Feature':
          processGeoJSON(json.geometry, maskPolygonCoords, bounds);
          break;
        case 'GeometryCollection':
          const geometries = json.geometries;
          for (let i = 0, len = geometries.length; i < len; i++) {
            processGeoJSON(geometries[i], maskPolygonCoords, bounds);
          }
          break;
        case 'Polygon':
          addRemovalPolygonCoordinates(json.coordinates, maskPolygonCoords, bounds);
          break;
        case 'MultiPolygon':
          addRemovalMultiPolygonCoordinates(json.coordinates, maskPolygonCoords, bounds);
          break;
        default:
          break;
      }
    };

    // Add polygon coordinates to the mask
    const addRemovalPolygonCoordinates = (coords: any[], maskPolygonCoords: any[], bounds: LatLngBounds) => {
      for (let i = 0, len = coords.length; i < len; i++) {
        maskPolygonCoords.push(coords[i]);
        updateBounds(coords[i], bounds);
      }
    };

    // Add multi-polygon coordinates to the mask
    const addRemovalMultiPolygonCoordinates = (coords: any[], maskPolygonCoords: any[], bounds: LatLngBounds) => {
      for (let i = 0, len = coords.length; i < len; i++) {
        addRemovalPolygonCoordinates(coords[i], maskPolygonCoords, bounds);
      }
    };

    // Update bounds based on coordinates
    const updateBounds = (coords: any[], bounds: LatLngBounds) => {
      const L = WINDOW_OR_GLOBAL.L;
      for (let i = 0, len = coords.length; i < len; i++) {
        const coords2 = coords[i];
        for (let j = 0, lenJ = coords2.length; j < lenJ; j++) {
          bounds.extend(new L.LatLng(coords2[1], coords2[0], coords2[2]));
        }
      }

      context.emit('update:bounds', bounds);
    };

    // Convert coordinates to LatLng
    const coordsToLatLng = (coords: number[]) => {
      const L = WINDOW_OR_GLOBAL.L;
      return new L.LatLng(coords[1], coords[0], coords[2]);
    };

    // Convert coordinates to LatLngs array
    const coordsToLatLngs = (coords: any[]) => {
      const latlngs: any[] = [];
      const dimensions = dimension(coords);

      for (let i = 0, len = coords.length; i < len; i++) {
        let latlng;
        if (dimensions > 2) {
          latlng = coordsToLatLngs(coords[i]);
        } else {
          latlng = coordsToLatLng(coords[i]);
        }
        latlngs.push(latlng);
      }

      return latlngs;
    };

    // Get dimensions of array
    const dimension = (arr: any[]): number => {
      let j = 1;
      for (const i in arr) {
        if (arr[i] instanceof Array) {
          if (1 + dimension(arr[i]) > j) {
            j = j + dimension(arr[i]);
          }
        }
      }
      return j;
    };

    // HTTP request function for GeoJSON URLs
    const request = (url: string, success: (json: any) => void, error?: () => void) => {
      const xhr = new XMLHttpRequest();
      xhr.responseType = 'json';
      xhr.onreadystatechange = function () {
        if (xhr.readyState === 4) {
          if (xhr.status === 200) {
            if (success && typeof success === 'function') {
              success(xhr.response);
            }
          } else {
            if (error && typeof error === 'function') {
              error();
            }
          }
        }
      };
      xhr.open('GET', url, true);
      xhr.send(null);
    };

    onMounted(async () => {
      const L = WINDOW_OR_GLOBAL.L;
      leafletObject.value = markRaw(new L.LayerGroup());

      const remapEvents = (): LeafletEventHandlerFnMap => {
        return {};
      };

      // Add the layer to the map
      addLayer({
        ...props,
        ...methods,
        leafletObject: leafletObject.value,
      });

      // If GeoJSON is provided, add it
      if (props.geojson) {
        nextTick(() => {
          methods.addGeoJSON(props.geojson);
        });
      }

      ready.value = true;
      nextTick(() => context.emit('ready', leafletObject.value));
    });

    return { ready, leafletObject, methods, bounds };
  },
  render() {
    return render(this.ready, this.$slots);
  },
});
</script>

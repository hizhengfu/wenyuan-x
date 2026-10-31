<script>
import {onMounted, ref, inject, nextTick} from 'vue';
import {
  remapEvents,
  propsBinder,
  WINDOW_OR_GLOBAL,
  GLOBAL_LEAFLET_OPT,
} from '@vue-leaflet/vue-leaflet/src/utils';
import {props, setup as tileLayerSetup} from '@vue-leaflet/vue-leaflet/src/functions/tileLayer';
import HeatmapOverlay from 'leaflet-heatmap'


const cfg = {
  'radius': 2,
  'maxOpacity': .8,
  'scaleRadius': true,
  'useLocalExtrema': true,
  latField: 'lat',
  lngField: 'lng',
  valueField: 'count'
};

const data = {
  max: 8,
  data: [{lat: 24.6408, lng: 46.7728, count: 3}, {lat: 50.75, lng: -1.55, count: 1}]
};

export default {
  props,
  setup(props, context) {
    const leafletRef = ref({});
    const useGlobalLeaflet = inject(GLOBAL_LEAFLET_OPT);
    const addLayer = inject('addLayer');
    const {options, methods} = tileLayerSetup(props, leafletRef, context);
    onMounted(async () => {
      const {tileLayer, DomEvent} = useGlobalLeaflet
        ? WINDOW_OR_GLOBAL.L
        : await import('leaflet');
      leafletRef.value = HeatmapOverlay(cfg);
      const listeners = remapEvents(context.attrs);
      DomEvent.on(leafletRef.value, listeners);
      propsBinder(methods, leafletRef.value, props);
      addLayer({
        ...props,
        ...methods,
        leafletObject: leafletRef.value,
      });
      leafletRef.value.setData(data)
      nextTick(() => context.emit('ready', leafletRef.value));
    });
    return {leafletObject: leafletRef};
  },
  render() {
    return null;
  },
};
</script>

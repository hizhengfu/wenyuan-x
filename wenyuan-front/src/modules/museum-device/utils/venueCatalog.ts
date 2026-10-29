import { gatewayApi } from '../api/gateway';
import type { MuseumVenueVo } from '../typings';

let cachedVenues: MuseumVenueVo[] | null = null;
let pendingRequest: Promise<MuseumVenueVo[]> | null = null;

function responseList<T>(response: any): T[] {
  const data = response?.data?.data ?? response?.data ?? response;
  return Array.isArray(data) ? data : (data?.list ?? []);
}

export function loadVenueCatalog(force = false): Promise<MuseumVenueVo[]> {
  if (!force && cachedVenues) return Promise.resolve(cachedVenues);
  if (!force && pendingRequest) return pendingRequest;
  pendingRequest = gatewayApi
    .venues()
    .then((response) => {
      cachedVenues = responseList<MuseumVenueVo>(response);
      return cachedVenues;
    })
    .finally(() => {
      pendingRequest = null;
    });
  return pendingRequest;
}

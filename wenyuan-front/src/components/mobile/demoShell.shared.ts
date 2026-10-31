import type { LocationQuery, LocationQueryValue } from 'vue-router';

type DemoShellRouteState = {
  path: string;
  query?: LocationQuery;
  hash?: string | null;
};

function getQueryValue(value?: LocationQueryValue | LocationQueryValue[] | null) {
  if (Array.isArray(value)) {
    return value[0] ?? '';
  }

  return value ?? '';
}

function appendQueryValue(params: URLSearchParams, key: string, value?: LocationQueryValue | LocationQueryValue[] | null) {
  if (Array.isArray(value)) {
    value
      .filter((item): item is string => typeof item === 'string')
      .forEach((item) => {
        params.append(key, item);
      });
    return;
  }

  if (typeof value === 'string') {
    params.append(key, value);
  }
}

export function isDemoShellEnabled(target: string, query: LocationQuery = {}) {
  return target === 'yyjxwbH5' && getQueryValue(query.demoShell) === '1';
}

export function buildDemoShellHashPath(routeState: DemoShellRouteState) {
  const params = new URLSearchParams();

  Object.entries(routeState.query ?? {}).forEach(([key, value]) => {
    if (key === 'demoShell') {
      return;
    }

    appendQueryValue(params, key, value);
  });

  const queryString = params.toString();
  const normalizedHash =
    routeState.hash && routeState.hash !== '#'
      ? routeState.hash.startsWith('#')
        ? routeState.hash
        : `#${routeState.hash}`
      : '';

  return `${routeState.path}${queryString ? `?${queryString}` : ''}${normalizedHash}`;
}

export function buildDemoShellIframeSrc(currentHref: string, routeState: DemoShellRouteState) {
  const url = new URL(currentHref);
  return `${url.origin}${url.pathname}${url.search}#${buildDemoShellHashPath(routeState)}`;
}

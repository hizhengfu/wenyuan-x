export function SupportsWebGL2(): boolean {
  const canvas = document.createElement('canvas');
  let hasSupport = false;
  try {
    const ctx = canvas.getContext('webgl2');
    hasSupport = (ctx != null);
  } catch (e) {
    console.log(e);
  }
  return hasSupport
}
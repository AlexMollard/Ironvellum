export function createRouter(render) {
  const update = () => render(location.hash.slice(1) || '/');
  window.addEventListener('hashchange', update);
  return {go(path) { if (location.hash === `#${path}`) update(); else location.hash = path; }, start:update, dispose() { window.removeEventListener('hashchange',update); }};
}

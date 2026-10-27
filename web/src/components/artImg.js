import artIndex from '../generated/art-index.json';
export function artImg(name, artId) {
  const el = document.createElement('span'); el.className = 'exercise-art'; el.setAttribute('role','img'); el.setAttribute('aria-label',name || 'Exercise');
  const id = artId ?? artIndex[name];
  if (id && /^[a-zA-Z0-9_-]+$/.test(id)) { const url = `url("${import.meta.env.BASE_URL}art/${id}.svg")`; el.style.maskImage=url; el.style.webkitMaskImage=url; }
  else { el.classList.add('exercise-art-placeholder'); el.textContent = (name || '?').slice(0,1).toUpperCase(); }
  return el;
}

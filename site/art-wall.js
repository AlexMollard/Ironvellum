const wall = document.querySelector('.art-wall');
const strip = wall.querySelector('.art-strip');
const toggle = wall.querySelector('button');
const reduced = matchMedia('(prefers-reduced-motion: reduce)');
const motion = strip.animate([{ transform: 'translateX(0)' }, { transform: 'translateX(-100%)' }],
  { duration: 42000, easing: 'linear', fill: 'forwards' });
motion.pause();
let visible = false;
let paused = reduced.matches;
let ready = false;
let next = 2;

function updateMotion() {
  toggle.textContent = paused ? 'Play motion' : 'Pause motion';
  if (visible && !document.hidden && ready && !paused) motion.play();
  else motion.pause();
}

async function prepareSheets() {
  try {
    await Promise.all([...strip.children].map(image => image.decode()));
    ready = true;
    toggle.hidden = false;
    updateMotion();
  } catch {
    toggle.hidden = true;
  }
}

motion.onfinish = () => {
  ready = false;
  motion.pause();
  motion.currentTime = 0;
  const recycled = strip.firstElementChild;
  strip.append(recycled);
  recycled.src = `./exercise-wall/${next % Number(wall.dataset.sheets)}.svg`;
  next++;
  prepareSheets();
};
toggle.addEventListener('click', () => { paused = !paused; updateMotion(); });
reduced.addEventListener('change', () => { paused = reduced.matches; updateMotion(); });
document.addEventListener('visibilitychange', updateMotion);
let started = false;
new IntersectionObserver(([entry]) => {
  visible = entry.isIntersecting;
  if (visible && !started) {
    started = true;
    prepareSheets();
  }
  updateMotion();
}).observe(wall);

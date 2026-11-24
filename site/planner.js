const load = document.querySelector('#load');
const lighter = document.querySelector('#lighter');
const heavier = document.querySelector('#heavier');
const loadValue = document.querySelector('#load-value');
const topSet = document.querySelector('#top-kg');
const backSets = document.querySelectorAll('.back-kg');

function showPlan() {
  const kg = load.valueAsNumber;
  const step = Number(load.step);
  const backoff = Math.floor((kg / step) * 9 / 10) * step;
  loadValue.textContent = topSet.textContent = load.value;
  for (const set of backSets) set.textContent = backoff;
  load.setAttribute('aria-valuetext', `${kg} kg; two back-off sets at ${backoff} kg each`);
  lighter.disabled = kg <= Number(load.min);
  heavier.disabled = kg >= Number(load.max);
}

load.addEventListener('input', showPlan);
lighter.addEventListener('click', () => { load.stepDown(); showPlan(); });
heavier.addEventListener('click', () => { load.stepUp(); showPlan(); });
document.querySelector('#reset-load').addEventListener('click', () => { load.value = load.defaultValue; showPlan(); });
showPlan();
document.querySelector('#load-controls').hidden = false;

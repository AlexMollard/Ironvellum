import {it,expect} from 'vitest';
import {artImg} from '../src/components/artImg.js';
it('renders indexed SVG as a current-colour mask',()=>{const el=artImg('Pull-up','pull_up');expect(el.style.maskImage).toContain('art/pull_up.svg');expect(el.classList.contains('exercise-art')).toBe(true);});
it('renders custom exercises as a placeholder',()=>{const el=artImg('Unique custom exercise',null);expect(el.classList.contains('exercise-art-placeholder')).toBe(true);expect(el.style.maskImage).toBe('');});

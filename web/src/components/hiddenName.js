import { t } from '../i18n.js';
/** A lifter whose profile the viewer cannot read is shown under the neutral name the phone uses ("Ironbound" themed). */
export const hiddenName = name => (name != null && String(name).trim() ? String(name) : t('cloud_hidden_lifter'));

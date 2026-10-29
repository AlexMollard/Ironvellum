import { t, fromServer } from './i18n.js';

// The codes the app's Cloud.explain maps to "this part of the cloud is not set up".
const NOT_SET_UP = new Set(['42703', '42P01', 'PGRST202', 'PGRST204', 'PGRST205']);
const KEEPER_ONLY = 'Only the Keeper';
// supabase-js reports a request that never arrived as a TypeError or an AuthRetryableFetchError.
const offline = error => error instanceof TypeError || error?.name === 'AuthRetryableFetchError' || /failed to fetch|networkerror|load failed/i.test(String(error?.message ?? ''));

/**
 * One lifter-readable sentence for any error a social call can throw: the port of the app's Cloud.explain. A Postgres
 * code, a stack or a raw server message never reaches the screen; only a sentence the migrations wrote for the lifter
 * (P0001, and the Keeper's 42501) is shown, through fromServer so it speaks the current names mode.
 */
export function explain(error) {
  if (error?.name === 'NotYet') return t('cloud_error_unavailable');
  const code = error?.code == null ? '' : String(error.code), message = String(error?.message ?? '');
  if (code === '23505') return t('cloud_error_name_taken');
  if (code === '42501') return message.startsWith(KEEPER_ONLY) ? fromServer(message) : t('cloud_error_not_allowed');
  if (code === '23514') return t('cloud_error_out_of_range');
  if (code === 'P0001') return message.trim() ? fromServer(message) : t('cloud_error_refused');
  if (NOT_SET_UP.has(code)) return t('cloud_error_not_set_up');
  if (code === 'invalid_credentials') return t('cloud_error_wrong_credentials');
  if (code === 'over_request_rate_limit' || code === 'over_email_send_rate_limit') return t('cloud_error_rate_limited');
  if (offline(error)) return t('cloud_error_offline');
  return t('cloud_error_refused');
}

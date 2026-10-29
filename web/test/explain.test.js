import { describe, it, expect, afterEach } from 'vitest';
import { explain } from '../src/explain.js';
import { setWordsMode, t, fromServer } from '../src/i18n.js';

const server = message => ({ code: 'P0001', message });
afterEach(() => setWordsMode(false));

describe('explain', () => {
  it('p0001ShowsTheServerSentenceInPlainWords', () => {
    const sentences = ['Too many remarks — wait a minute.', 'Daily remark limit reached — try again tomorrow.', 'This trial has reached its remark limit.',
      'Too many ally requests today — try again tomorrow.', 'You can’t send an ally request to this Ironbound.', 'Too many reports today — try again tomorrow.'];
    for (const themed of [false, true]) {
      setWordsMode(themed);
      for (const sentence of sentences) {
        const shown = explain(server(sentence));
        expect(shown).toBe(fromServer(sentence));
        expect(shown).not.toMatch(/P0001/);
      }
    }
    setWordsMode(false);
    expect(explain(server('Too many remarks — wait a minute.'))).toBe('Too many comments — wait a minute.');
    expect(explain(server('Too many ally requests today — try again tomorrow.'))).toBe('Too many friend requests today — try again tomorrow.');
    expect(explain(server('You can’t send an ally request to this Ironbound.'))).not.toMatch(/ally|Ironbound/);
    setWordsMode(true);
    expect(explain(server('Too many remarks — wait a minute.'))).toBe('Too many remarks — wait a minute.');
  });
  it('keeperOnlyIsShownAs42501Sentence', () => {
    expect(explain({ code: '42501', message: 'Only the Keeper can set the goal' })).toBe('Only the owner can set the goal');
    setWordsMode(true);
    expect(explain({ code: '42501', message: 'Only the Keeper can set the goal' })).toBe('Only the Keeper can set the goal');
  });
  it('other42501IsNotAllowed', () => expect(explain({ code: '42501', message: 'new row violates row-level security policy' })).toBe(t('cloud_error_not_allowed')));
  it('23514IsOutOfRange', () => expect(explain({ code: '23514', message: 'violates check constraint "x"' })).toBe(t('cloud_error_out_of_range')));
  it('23505IsNameTaken', () => expect(explain({ code: '23505', message: 'duplicate key' })).toBe(t('cloud_error_name_taken')));
  it('missingObjectsAreNotSetUp', () => {
    for (const code of ['PGRST202', 'PGRST204', 'PGRST205']) expect(explain({ code, message: 'Could not find the table' })).toBe(t('cloud_error_not_set_up'));
  });
  it('authCodesAndOfflineHaveTheirOwnSentences', () => {
    expect(explain({ code: 'invalid_credentials', message: 'x' })).toBe(t('cloud_error_wrong_credentials'));
    expect(explain({ code: 'over_request_rate_limit', message: 'x' })).toBe(t('cloud_error_rate_limited'));
    expect(explain(new TypeError('Failed to fetch'))).toBe(t('cloud_error_offline'));
  });
  it('unknownIsRefused', () => {
    expect(explain({ code: 'XX000', message: 'boom' })).toBe(t('cloud_error_refused'));
    expect(explain(undefined)).toBe(t('cloud_error_refused'));
    expect(explain({ code: 'P0001', message: '  ' })).toBe(t('cloud_error_refused'));
  });
  it('neverShowsACodeOrStack', () => {
    const shown = [explain({ code: 'XX000', message: 'relation "x" does not exist', details: 'at line 4', hint: 'stack' }), explain(new Error('Error: boom\n    at foo (file.js:1:1)')), explain({ code: '42P01', message: 'relation sessions' })];
    for (const text of shown) { expect(text).not.toMatch(/XX000|42P01|\bat \w+ \(|relation|stack/i); }
  });
});

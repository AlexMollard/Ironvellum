import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
const shared = { url: 'https://shared.test', key: 'shared-key', own: false };
const auth = vi.hoisted(() => ({
  loadBackend: vi.fn(), sharedBackend: vi.fn(), saveBackend: vi.fn(), clearBackend: vi.fn(), backendClient: vi.fn(),
  signIn: vi.fn(), signInWithGoogle: vi.fn(), restoreSession: vi.fn(), observeSignOut: vi.fn(), signOut: vi.fn()
}));
vi.mock('../src/auth.js', () => auth);
let supabaseSource;
vi.mock('../src/source.js', async importActual => ({ ...(await importActual()), createSupabaseSource: vi.fn(() => supabaseSource) }));
const flush = async () => { for (let i = 0; i < 20; i++) await new Promise(resolve => setTimeout(resolve, 0)); };
const loadMain = async () => { vi.resetModules(); await import('../src/main.js'); await flush(); };
const goodSource = (extra = {}) => ({ schemaVersion: async () => 31, getSnapshot: async () => null, listWorkouts: async () => [], getSets: async () => [], ...extra });
const signedIn = () => auth.restoreSession.mockResolvedValue({ client: { auth: {} }, session: { user: { id: 'me' } } });
beforeEach(() => {
  document.body.innerHTML = '<div id="app"></div>'; history.replaceState(null, '', '/'); location.hash = '#/';
  for (const fn of Object.values(auth)) fn.mockReset();
  auth.loadBackend.mockImplementation(() => ({ ...shared })); auth.sharedBackend.mockImplementation(() => ({ ...shared }));
  auth.backendClient.mockImplementation(() => ({ auth: {} })); auth.observeSignOut.mockImplementation(() => ({ unsubscribe() {} })); auth.signOut.mockResolvedValue();
  auth.restoreSession.mockResolvedValue({ client: { auth: {} }, session: null }); supabaseSource = goodSource();
});
afterEach(() => vi.unstubAllEnvs());
describe('main flows', () => {
  it('renders the sign-in page, with Google hidden by default', async () => {
    await loadMain();
    expect(document.querySelector('h1').textContent).toBe('IRONVELLUM');
    expect(document.querySelector('form.signin-form h2').textContent).toBe('Sign in');
    expect(document.querySelector('input[type="email"]')).not.toBeNull(); expect(document.querySelector('input[type="password"]')).not.toBeNull();
    expect([...document.querySelectorAll('button')].find(b => b.textContent === 'Continue with Google').hidden).toBe(true);
  });
  it('shows the Google button on the shared backend when the flag is on', async () => {
    vi.stubEnv('VITE_GOOGLE_SIGNIN', 'true'); await loadMain();
    expect([...document.querySelectorAll('button')].find(b => b.textContent === 'Continue with Google').hidden).toBe(false);
  });
  it('cleans an OAuth error from the query and the fragment', async () => {
    history.replaceState(null, '', '/?error=access_denied&error_code=x&error_description=nope&keep=1'); await loadMain();
    expect(location.search).toBe('?keep=1'); expect(document.body.textContent).toContain('Google sign-in was cancelled or failed');
    expect(auth.restoreSession).not.toHaveBeenCalled();
    history.replaceState(null, '', '/#error=access_denied&error_description=nope'); await loadMain();
    expect(location.hash).toBe(''); expect(document.body.textContent).toContain('Google sign-in was cancelled or failed');
  });
  it('warns about an old schema', async () => {
    signedIn(); supabaseSource = goodSource({ schemaVersion: async () => 30 });
    await loadMain(); expect(document.querySelector('main .notice').textContent).toBe('Needs schema 31; this backend reports 30.');
  });
  it('warns about a newer snapshot', async () => {
    const { SnapshotTooNew } = await import('../src/source.js');
    signedIn(); supabaseSource = goodSource({ getSnapshot: async () => { throw new SnapshotTooNew(9); } });
    await loadMain(); expect(document.body.textContent).toContain('This snapshot is newer than this viewer. Update the web viewer.');
  });
  it('survives pressing Sign out twice', async () => {
    signedIn(); await loadMain();
    const button = [...document.querySelectorAll('button')].find(b => b.textContent === 'Sign out');
    button.click(); button.click(); await flush();
    expect(document.querySelector('form.signin-form')).not.toBeNull(); expect(document.querySelector('.shell')).toBeNull();
  });
  it('still attempts sign-in when the backend probe fails', async () => {
    supabaseSource = goodSource({ schemaVersion: async () => { throw new Error('offline'); } });
    auth.signIn.mockResolvedValue({ user: { id: 'me' } });
    await loadMain();
    const [email, password] = document.querySelectorAll('input[type="email"], input[type="password"]'); email.value = 'me@example.test'; password.value = 'secret';
    document.querySelector('form.signin-form').dispatchEvent(new Event('submit', { cancelable: true })); await flush();
    expect(auth.signIn).toHaveBeenCalledOnce(); expect(document.querySelector('.shell')).not.toBeNull();
  });
});

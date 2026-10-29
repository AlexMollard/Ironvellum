// A recording fake of the supabase-js PostgREST builder and rpc. Every call is kept as a plain record, so a test can assert
// the request (table, operation, body, filters, columns, conflict target) as well as what the screen did with the answer.
//
// `script` is a function (call) => answer, or an object keyed by table name (or `rpc:<name>`) whose values are the answer or a
// function of the call. An answer is: rows (array), a single row, `{ data, count }`, `{ error }`, or an error-shaped
// `{ code, message }`. A thrown or rejected value from a function answer is rethrown from the awaited query.
const MUTATIONS = new Set(['insert', 'upsert', 'update', 'delete']);
const FILTERS = ['eq', 'neq', 'lt', 'lte', 'gt', 'gte', 'in', 'is', 'ilike', 'like', 'or', 'not', 'contains'];

function normalise(answer, call) {
  if (answer === undefined) return { data: call.single ? null : [], error: null };
  if (answer instanceof Error || (answer && typeof answer === 'object' && 'code' in answer && 'message' in answer && !('data' in answer))) return { data: null, error: answer };
  if (answer && typeof answer === 'object' && ('data' in answer || 'error' in answer)) return { data: answer.data ?? null, error: answer.error ?? null, count: answer.count ?? null };
  if (call.single) return { data: Array.isArray(answer) ? (answer[0] ?? null) : answer, error: null };
  return { data: answer, error: null, count: Array.isArray(answer) ? answer.length : null };
}

export function fakeClient(script = {}) {
  const calls = [];
  const answerFor = async call => {
    const entry = typeof script === 'function' ? script : script[call.kind === 'rpc' ? `rpc:${call.name}` : call.table];
    return normalise(typeof entry === 'function' ? await entry(call) : entry, call);
  };
  const builder = (kind, target) => {
    const call = kind === 'rpc' ? { kind, name: target.name, args: target.args, filters: [] } : { kind, table: target, op: 'select', columns: '*', filters: [], order: [], single: false };
    calls.push(call);
    const chain = {
      select(columns = '*', options) { if (MUTATIONS.has(call.op)) call.returning = columns; else { call.op = 'select'; call.columns = columns; } call.options = options ?? call.options; return chain; },
      insert(values, options) { Object.assign(call, { op: 'insert', values, options }); return chain; },
      upsert(values, options) { Object.assign(call, { op: 'upsert', values, options }); return chain; },
      update(values, options) { Object.assign(call, { op: 'update', values, options }); return chain; },
      delete(options) { Object.assign(call, { op: 'delete', options }); return chain; },
      order(column, options) { call.order.push([column, options?.ascending !== false]); return chain; },
      limit(n) { call.limit = n; return chain; },
      range(from, to) { call.range = [from, to]; return chain; },
      single() { call.single = true; return chain; },
      maybeSingle() { call.single = true; return chain; },
      then(resolve, reject) { return answerFor(call).then(resolve, reject); },
    };
    for (const name of FILTERS) chain[name] = (...args) => { call.filters.push([name, ...args]); return chain; };
    return chain;
  };
  return {
    calls,
    from: table => builder('from', table),
    rpc: (name, args) => builder('rpc', { name, args }),
    // What a screen touched: for the "reads no owner-only table" checks.
    tables: () => new Set(calls.filter(c => c.kind === 'from').map(c => c.table)),
    rpcs: () => new Set(calls.filter(c => c.kind === 'rpc').map(c => c.name)),
    callsTo: table => calls.filter(c => c.table === table),
    reset: () => { calls.length = 0; },
  };
}

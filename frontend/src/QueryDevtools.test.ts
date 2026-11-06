import { readFileSync } from 'fs';
import path from 'path';
import { runInNewContext } from 'vm';
import ts from 'typescript';

// Evaluate the actual JSX guard without mounting unrelated App routes or API consumers.
const app = readFileSync(path.join(process.cwd(), 'src/App.tsx'), 'utf8');
const guard = app.match(/\{(process\.env\.NODE_ENV === 'development' && process\.env\.REACT_APP_ENABLE_QUERY_DEVTOOLS === 'true' && \([\s\S]*?<ReactQueryDevtools[\s\S]*?\))\}/)?.[1];

test.each([
  ['development', undefined, false], ['development', 'false', false], ['development', 'true', true],
  ['production', 'true', false], ['test', 'true', false], ['development', 'TRUE', false],
])('Devtools render guard %s / %s', (mode, flag, enabled) => {
  expect(guard).toBeDefined();
  const createElement = jest.fn((_component, props) => ({ props }));
  const result: { node?: { props: { initialIsOpen: boolean } } | false } = {};
  const js = ts.transpileModule(`result.node = (${guard});`, {
    compilerOptions: { target: ts.ScriptTarget.ES2017, module: ts.ModuleKind.CommonJS, jsx: ts.JsxEmit.React },
    fileName: 'guard.tsx',
  }).outputText;
  runInNewContext(js, { process: { env: { NODE_ENV: mode, REACT_APP_ENABLE_QUERY_DEVTOOLS: flag } }, React: { createElement }, ReactQueryDevtools: 'devtools', result });
  expect(createElement).toHaveBeenCalledTimes(enabled ? 1 : 0);
  expect(result.node).toEqual(enabled ? { props: { initialIsOpen: false } } : false);
});

test('App retains the existing QueryClient, provider and preview routes', () => {
  expect(app).toContain('const queryClient = new QueryClient();');
  expect(app).toContain('<QueryClientProvider client={queryClient}>');
  expect(app.match(/<ReactQueryDevtools /g)).toHaveLength(1);
  ['admin-login', 'customer-combos', 'mining-monitoring', 'mining-runs', 'mining-comparison'].forEach(route => expect(app).toContain(`path="/__preview/${route}"`));
});

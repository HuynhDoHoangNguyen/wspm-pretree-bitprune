// Shared infrastructure, independent Dataset cases; no Auth harness is served in this mode.
process.argv.push('--dataset');
await import('./run.mjs');

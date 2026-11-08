// Actual application build and test-classpath backend; no browser hooks or mocked API data.
process.argv.push('--tv2');
await import('./run.mjs');

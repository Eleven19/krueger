import { pathToFileURL } from 'node:url';

export function assertSuccessful(results, selected) {
  const failures = selected.filter(name => results[name]?.result !== 'success')
    .map(name => `${name}: ${results[name]?.result ?? 'missing'}`);
  if (failures.length) throw new Error(`Required CI work did not succeed: ${failures.join(', ')}`);
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  try {
    assertSuccessful(JSON.parse(process.env.JOB_RESULTS ?? '{}'), process.argv.slice(2));
  } catch (error) {
    console.error(error.message);
    process.exitCode = 1;
  }
}

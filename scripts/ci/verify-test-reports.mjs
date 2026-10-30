import { readFile } from 'node:fs/promises';
import { join } from 'node:path';
import { pathToFileURL } from 'node:url';

export async function verifyReports(dir, suites) {
  if (!suites.length) throw new Error('No required test suites');
  let total = 0;
  for (const suite of [...suites].sort()) {
    const xml = await readFile(join(dir, `TEST-${suite}.xml`), 'utf8').catch(() => { throw new Error(`${suite}: missing test report`); });
    const tag = xml.match(/<testsuite\s[^>]*>/)?.[0];
    if (!tag) throw new Error(`${suite}: invalid test report`);
    const attrs = Object.fromEntries([...tag.matchAll(/([\w-]+)="([^"]*)"/g)].map(m => [m[1], m[2]]));
    const count = Number(attrs.tests);
    const failures = Number(attrs.failures ?? 0) + Number(attrs.errors ?? 0);
    const skipped = Number(attrs.skipped ?? 0);
    if (!Number.isFinite(count) || !Number.isFinite(failures) || !Number.isFinite(skipped)) throw new Error(`${suite}: invalid test counts`);
    if (count - skipped <= 0) throw new Error(`${suite}: no executed tests`);
    if (failures !== 0) throw new Error(`${suite}: ${failures} failed tests`);
    total += count - skipped;
  }
  return total;
}
if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  try {
    const count = await verifyReports(process.argv[2], process.argv.slice(3));
    console.log(`Verified ${count} executed tests in ${process.argv.length - 3} suites`);
  } catch (error) {
    console.error(error.message);
    process.exitCode = 1;
  }
}

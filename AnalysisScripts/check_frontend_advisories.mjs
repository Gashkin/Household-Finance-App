import { readFileSync } from 'node:fs';
import { spawnSync } from 'node:child_process';
import { fileURLToPath, pathToFileURL } from 'node:url';

const severity = { info: 0, low: 1, moderate: 2, high: 3, critical: 4 };
const advisory = 'https://github.com/advisories/GHSA-fc5q-39xc-vjpf';

/** Evaluate npm's complete v2 report, including propagated meta-vulnerabilities. */
export function checkAudit(report, lock, policy, { now = Date.now(), latestVersion } = {}) {
  if (report?.error || report?.auditReportVersion !== 2 || !report.vulnerabilities
      || Array.isArray(report.vulnerabilities) || !report.metadata?.vulnerabilities
      || !lock?.packages || ![2, 3].includes(lock.lockfileVersion)) {
    throw Error('Incomplete or unsupported npm audit/lockfile data');
  }
  if (policy?.advisory !== advisory || policy.package !== 'picomatch'
      || policy.version !== '4.0.7' || policy.severity !== 'high'
      || !policy.developmentPackages || policy.developmentPackages.picomatch !== policy.version
      || !Number.isFinite(Date.parse(policy.expiresAt)) || !policy.approval || !policy.reason) {
    throw Error('Invalid advisory exception policy');
  }
  const findings = report.vulnerabilities;
  const counts = Object.fromEntries(Object.keys(severity).map(s => [s, 0]));
  for (const [name, finding] of Object.entries(findings)) {
    if (finding?.name !== name || !(finding.severity in severity)
        || !Array.isArray(finding.via) || finding.via.length === 0
        || !Array.isArray(finding.nodes) || finding.nodes.length === 0) {
      throw Error('Malformed npm vulnerability entry');
    }
    counts[finding.severity]++;
  }
  for (const [s, count] of Object.entries(counts)) {
    if (report.metadata.vulnerabilities[s] !== count) throw Error('Incomplete npm severity inventory');
  }
  const blocking = [], accepted = [], memo = new Map();
  function excepted(name, path = new Set()) {
    if (path.has(name)) throw Error('Cyclic npm advisory provenance');
    if (memo.has(name)) return memo.get(name);
    const finding = findings[name];
    if (!finding) throw Error('Unresolved npm advisory provenance');
    const next = new Set(path).add(name);
    // A production dependency, changed package version or new advisory cannot inherit this exception.
    let allowed = finding.severity === 'high' && finding.nodes.every(node => {
      const pkg = lock.packages[node];
      return typeof node === 'string' && (node === `node_modules/${name}` || node.endsWith(`/node_modules/${name}`))
        && pkg?.dev === true && policy.developmentPackages[name] === pkg.version;
    });
    for (const via of finding.via) {
      if (typeof via === 'string') {
        const child = findings[via];
        if (!child || severity[child.severity] > severity[finding.severity]) throw Error('Invalid npm advisory provenance');
        const childAllowed = excepted(via, next);
        allowed = childAllowed && allowed;
      } else {
        if (!via || !(via.severity in severity) || severity[via.severity] > severity[finding.severity]) {
          throw Error('Invalid direct npm advisory');
        }
        allowed = allowed && name === policy.package && via.name === policy.package
          && via.dependency === policy.package && via.url === policy.advisory
          && via.severity === policy.severity;
      }
    }
    memo.set(name, allowed);
    return allowed;
  }
  for (const [name, finding] of Object.entries(findings)) {
    if (severity[finding.severity] < severity.high) continue;
    if (excepted(name)) accepted.push(name); else blocking.push(name);
  }
  if (accepted.length) {
    if (!Number.isFinite(now) || now >= Date.parse(policy.expiresAt)) throw Error('Picomatch advisory exception expired; reassess before release');
    if (latestVersion !== policy.version) throw Error('Picomatch release changed or could not be verified; upgrade/reassess the exception');
  }
  return { accepted, blocking, expiresAt: policy.expiresAt };
}

function npm(args, cwd) {
  // Fixed arguments only: Windows npm is a batch entry point. No audit content becomes shell text.
  const command = process.platform === 'win32' ? (process.env.ComSpec || 'cmd.exe') : 'npm';
  const parameters = process.platform === 'win32' ? ['/d', '/s', '/c', `npm.cmd ${args.join(' ')}`] : args;
  const result = spawnSync(command, parameters, { cwd, encoding: 'utf8', timeout: 90000, maxBuffer: 8 * 1024 * 1024 });
  if (result.error || result.signal || ![0, 1].includes(result.status)) throw Error('npm advisory/release query failed');
  let value;
  try { value = JSON.parse(result.stdout); } catch { throw Error('npm returned invalid JSON'); }
  if (value?.error) throw Error('npm could not complete its advisory/release query');
  return value;
}

export function main() {
  const frontend = fileURLToPath(new URL('../frontend/', import.meta.url));
  const lock = JSON.parse(readFileSync(new URL('../frontend/package-lock.json', import.meta.url), 'utf8'));
  const policy = JSON.parse(readFileSync(new URL('../ops/security/npm-advisory-exception.json', import.meta.url), 'utf8'));
  const report = npm(['audit', '--json'], frontend);
  // A current release lookup is required only when the specific advisory is still present.
  const present = Object.values(report.vulnerabilities || {}).some(v => v.via?.some(a => a?.url === advisory));
  const latestVersion = present ? npm(['view', 'picomatch', 'version', '--json'], frontend) : undefined;
  const result = checkAudit(report, lock, policy, { latestVersion });
  if (result.accepted.length) {
    console.warn(`WARNING: temporary build-only exception ${advisory}; expires ${result.expiresAt}. Findings: ${result.accepted.join(', ')}`);
  }
  if (result.blocking.length) throw Error(`Blocking high/critical npm findings: ${result.blocking.join(', ')}`);
  console.log('Frontend advisory gate passed; all other high/critical findings remain blocking.');
}

if (process.argv[1] && pathToFileURL(process.argv[1]).href === import.meta.url) {
  try { main(); } catch (error) { console.error(error.message); process.exitCode = 1; }
}

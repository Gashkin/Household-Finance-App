import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { checkAudit } from './check_frontend_advisories.mjs';
const policy = JSON.parse(readFileSync(new URL('../ops/security/npm-advisory-exception.json', import.meta.url)));
const now = Date.parse('2026-11-18T12:00:00Z');
const options = { now, latestVersion: '4.0.7' };
function fixture() {
  const direct = { name: 'picomatch', dependency: 'picomatch', severity: 'high', url: policy.advisory };
  const vulnerabilities = {};
  const packages = {};
  for (const [name, via] of [['picomatch', [direct]], ['fdir', ['picomatch']], ['tinyglobby', ['fdir', 'picomatch']], ['vite', ['picomatch', 'tinyglobby']], ['@vitejs/plugin-react', ['vite']]]) {
    vulnerabilities[name] = { name, severity: 'high', via, nodes: [`node_modules/${name}`] };
    packages[`node_modules/${name}`] = { dev: true, version: policy.developmentPackages[name] };
  }
  return { report: { auditReportVersion: 2, vulnerabilities, metadata: { vulnerabilities: { info: 0, low: 0, moderate: 0, high: 5, critical: 0 } } }, lock: { lockfileVersion: 3, packages } };
}
test('accepts only the approved dev advisory and its exclusively derived findings', () => {
  const { report, lock } = fixture();
  assert.equal(checkAudit(report, lock, policy, options).accepted.length, 5);
  assert.deepEqual(checkAudit(report, lock, policy, options).blocking, []);
});
test('unrelated high advisory on the same package and its dependants still blocks', () => {
  const { report, lock } = fixture();
  report.vulnerabilities.picomatch.via.push({ name: 'picomatch', dependency: 'picomatch', severity: 'high', url: 'https://github.com/advisories/GHSA-unrelated' });
  assert.equal(checkAudit(report, lock, policy, options).blocking.length, 5);
});
test('unrelated package finding blocks without suppressing the known warning', () => {
  const { report, lock } = fixture();
  report.vulnerabilities.vite.via.push({ name: 'vite', dependency: 'vite', severity: 'high', url: 'https://github.com/advisories/GHSA-other' });
  assert.deepEqual(checkAudit(report, lock, policy, options).blocking.sort(), ['@vitejs/plugin-react', 'vite']);
});
test('production dependency and changed Picomatch version are not excepted', () => {
  for (const update of [pkg => { pkg.dev = false; }, pkg => { pkg.version = '4.0.6'; }]) {
    const { report, lock } = fixture(); update(lock.packages['node_modules/picomatch']);
    assert.equal(checkAudit(report, lock, policy, options).blocking.length, 5);
  }
});
test('critical escalation stays blocking', () => {
  const { report, lock } = fixture();
  report.vulnerabilities.vite.severity = 'critical'; report.metadata.vulnerabilities.high--; report.metadata.vulnerabilities.critical++;
  // Keep the inherited parent severity coherent as npm would.
  report.vulnerabilities['@vitejs/plugin-react'].severity = 'critical'; report.metadata.vulnerabilities.high--; report.metadata.vulnerabilities.critical++;
  assert.deepEqual(checkAudit(report, lock, policy, options).blocking.sort(), ['@vitejs/plugin-react', 'vite']);
});
test('a changed dependant version requires a fresh scope assessment', () => {
  const { report, lock } = fixture(); lock.packages['node_modules/vite'].version = '8.3.9';
  assert.deepEqual(checkAudit(report, lock, policy, options).blocking.sort(), ['@vitejs/plugin-react', 'vite']);
});
test('expiry, new release and unavailable release metadata fail closed', () => {
  const { report, lock } = fixture();
  for (const config of [{ now: Date.parse(policy.expiresAt), latestVersion: '4.0.7' }, { now, latestVersion: '4.0.8' }, { now }]) {
    assert.throws(() => checkAudit(report, lock, policy, config));
  }
});
test('clean reports pass after expiry without using the exception', () => {
  const { report, lock } = fixture(); report.vulnerabilities = {}; report.metadata.vulnerabilities.high = 0;
  assert.deepEqual(checkAudit(report, lock, policy, { now: Date.parse('2027-01-01') }).accepted, []);
});
test('scan errors, missing findings, unknown severity, unresolved provenance and cycles fail closed', () => {
  for (const update of [r => { r.error = {}; }, r => { r.auditReportVersion = 1; }, r => { delete r.vulnerabilities.picomatch; }, r => { r.vulnerabilities.vite.severity = 'unknown'; }, r => { r.vulnerabilities.vite.via = ['missing']; }, r => { r.vulnerabilities.picomatch.via = ['vite']; }]) {
    const { report, lock } = fixture(); update(report);
    assert.throws(() => checkAudit(report, lock, policy, options));
  }
});

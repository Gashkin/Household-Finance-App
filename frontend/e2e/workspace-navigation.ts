import type { Page } from '@playwright/test';

const modules = {
  'Money position': 'Overview',
  'Spending & purchases': 'Activity',
  'Review & reconcile': 'Activity',
  'Plan ahead': 'Planning',
  'Budgets & allocations': 'Planning',
  'Goals & irregular costs': 'Planning',
  'Financial plans': 'Planning',
  'Cards, loans & mortgages': 'Debt',
  'Debt decision': 'Debt',
  'Household access': 'Settings',
  'Backup & recovery': 'Settings',
  'ChatGPT connection': 'Settings',
  'Consumer': 'Settings',
} as const;

export async function openWorkspace(page: Page, label: keyof typeof modules) {
  const navigation = page.getByRole('navigation', { name: 'Modules', exact: true });
  await navigation.waitFor();
  const section = page.getByRole('navigation', { name: modules[label] + ' pages', exact: true });
  if (!await section.isVisible()) {
    await navigation.getByRole('link', { name: modules[label], exact: true }).click();
  }
  await section.getByRole('link', { name: label, exact: true }).click();
}

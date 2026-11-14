import { useEffect, useState } from 'react';

interface Account {
  id: string;
  name: string;
  type: string;
  balance: number;
}

interface Category {
  id: string;
  name: string;
  budget: string;
}

interface Commitment {
  id: string;
  title: string;
  amount: number;
  dueDate: string;
  category?: string | null;
  dayOfMonth?: number | null;
}

interface ImportedTransaction {
  rowId: string;
  date: string;
  description: string;
  amount: number;
  account: string;
  duplicate: boolean;
}

interface ImportPreview {
  importId: string;
  transactions: ImportedTransaction[];
}

interface ImportAcceptResult {
  importId: string;
  acceptedCount: number;
  skippedCount: number;
}

interface TransactionHistoryItem {
  historyId: string;
  importId: string;
  rowId: string;
  date: string;
  description: string;
  amount: number;
  account: string;
  duplicate: boolean;
  mappedFutureCommitmentId?: string | null;
  category?: string | null;
  resolution?: string | null;
}

interface PlannerData {
  accounts: Account[];
  categories: Category[];
  commitments: Commitment[];
}

interface SuggestedMatch {
  commitmentId: string;
  score: number;
}

interface EditableMonthlyCommitment {
  title: string;
  amount: string;
  dayOfMonth: string;
  category: string;
}

interface MonthlyCommitmentPayload {
  title: string;
  amount: number;
  dueDate: 'Recurring';
  category: string | null;
  dayOfMonth: number;
}

interface EditableOneOffCommitment {
  title: string;
  amount: string;
  dueDate: string;
  category: string;
}

interface OneOffCommitmentPayload {
  title: string;
  amount: number;
  dueDate: string;
  category: string | null;
  dayOfMonth: null;
}

type ForecastPeriod = 'this-month';
type PlannerTab = 'forecast-current-month' | 'monthly-commitments' | 'once-off-commitments' | 'account-management';

const commitmentCategoryLookup: Record<string, string> = {
  salary: 'Income',
  groceries: 'Food & Groceries',
  mortgage: 'Housing',
  cu: 'Savings',
  'dog food': 'Pets',
  vodafone: 'Communications',
  allianz: 'Insurance',
  'car insurance': 'Insurance',
  bin: 'Utilities',
  tv: 'Utilities',
  netlix: 'Subscriptions',
  netflix: 'Subscriptions',
  littlewoods: 'Shopping',
  ger: 'Personal',
  'utility bill': 'Utilities',
  laya: 'Health',
  'life insurance': 'Insurance',
  'disney plus': 'Subscriptions',
  disney: 'Subscriptions',
  medicine: 'Health',
  becky: 'Family',
  extra: 'Personal',
  'clothes &house': 'Clothing & Home',
  'clothes & house': 'Clothing & Home',
  tax: 'Tax',
  'private app': 'Subscriptions',
  'college fees': 'Education',
  elmwood: 'Housing',
  prop: 'Housing',
  dentist: 'Health',
};

function normalizeCommitmentTitle(title: string) {
  return title.toLowerCase().replace(/\s+/g, ' ').trim();
}

function normalizeText(value: string) {
  return (value || '').toLowerCase().replace(/[^a-z0-9\s]/g, ' ').replace(/\s+/g, ' ').trim();
}

function extractCategoryTagFromDescription(description: string) {
  const match = description.match(/\[([^\]]+)\]\s*$/);
  if (!match) {
    return '';
  }
  return normalizeText(match[1]);
}

function stripCategoryTagFromDescription(description: string) {
  return description.replace(/\s*\[[^\]]+\]\s*$/, '').trim();
}

function getCommitmentCategory(commitment: Commitment) {
  const category = commitment.category?.trim();
  if (category) {
    return category;
  }

  return commitmentCategoryLookup[normalizeCommitmentTitle(commitment.title)] ?? 'Uncategorized';
}

function getCommitmentAmountByTitle(commitments: Commitment[], title: string) {
  const target = normalizeCommitmentTitle(title);
  return commitments
    .filter((commitment) => normalizeCommitmentTitle(commitment.title) === target)
    .reduce((sum, commitment) => sum + Math.abs(commitment.amount), 0);
}

function getCommitmentAmountByAnyTitle(commitments: Commitment[], titles: string[]) {
  return titles.reduce((sum, title) => sum + getCommitmentAmountByTitle(commitments, title), 0);
}

function formatCurrency(value: number) {
  return value.toLocaleString(undefined, { style: 'currency', currency: 'EUR' });
}

function getCurrentMonthLabel(referenceDate: Date) {
  const month = new Intl.DateTimeFormat('en-US', { month: 'short', timeZone: 'UTC' }).format(referenceDate);
  const year = new Intl.DateTimeFormat('en-US', { year: '2-digit', timeZone: 'UTC' }).format(referenceDate);
  return `${month}${year}`;
}

function getTodayIsoDate(referenceDate: Date) {
  return referenceDate.toISOString().slice(0, 10);
}

function isIsoDate(value: string) {
  return /^\d{4}-\d{2}-\d{2}$/.test(value.trim());
}

function isCurrentMonthIsoDate(value: string, referenceDate: Date) {
  if (!isIsoDate(value)) {
    return false;
  }
  const parsed = new Date(`${value}T00:00:00`);
  return parsed.getUTCFullYear() === referenceDate.getUTCFullYear() && parsed.getUTCMonth() === referenceDate.getUTCMonth();
}

function toCsvRow(values: Array<string | number | null | undefined>) {
  return values
    .map((value) => {
      const text = value == null ? '' : String(value);
      if (text.includes(',') || text.includes('"') || text.includes('\n')) {
        return `"${text.replace(/"/g, '""')}"`;
      }
      return text;
    })
    .join(',');
}

function parseCsvText(text: string) {
  const rows: string[][] = [];
  let currentRow: string[] = [];
  let currentCell = '';
  let inQuotes = false;

  for (let index = 0; index < text.length; index += 1) {
    const char = text[index];
    const next = text[index + 1];

    if (char === '"') {
      if (inQuotes && next === '"') {
        currentCell += '"';
        index += 1;
      } else {
        inQuotes = !inQuotes;
      }
      continue;
    }

    if (char === ',' && !inQuotes) {
      currentRow.push(currentCell);
      currentCell = '';
      continue;
    }

    if ((char === '\n' || char === '\r') && !inQuotes) {
      if (char === '\r' && next === '\n') {
        index += 1;
      }
      currentRow.push(currentCell);
      if (currentRow.some((cell) => cell.trim() !== '')) {
        rows.push(currentRow);
      }
      currentRow = [];
      currentCell = '';
      continue;
    }

    currentCell += char;
  }

  if (currentCell.length > 0 || currentRow.length > 0) {
    currentRow.push(currentCell);
    if (currentRow.some((cell) => cell.trim() !== '')) {
      rows.push(currentRow);
    }
  }

  return rows;
}

function downloadCsvFile(fileName: string, header: string[], dataRows: string[][]) {
  const csvContent = [toCsvRow(header), ...dataRows.map((row) => toCsvRow(row))].join('\n');
  const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = fileName;
  link.click();
  URL.revokeObjectURL(url);
}

async function readFileAsText(file: File) {
  return file.text();
}

function PlannerWorkspace() {
  const [plannerData, setPlannerData] = useState<PlannerData>({ accounts: [], categories: [], commitments: [] });
  const [forecastPeriod, setForecastPeriod] = useState<ForecastPeriod>('this-month');
  const [activeTab, setActiveTab] = useState<PlannerTab>('forecast-current-month');
  const [csvInput, setCsvInput] = useState('');
  const [preview, setPreview] = useState<ImportPreview | null>(null);
  const [selectedRowIds, setSelectedRowIds] = useState<string[]>([]);
  const [importStatus, setImportStatus] = useState('');
  const [selectedAccountId, setSelectedAccountId] = useState('');
  const [accountBalanceInput, setAccountBalanceInput] = useState('');
  const [transactionHistory, setTransactionHistory] = useState<TransactionHistoryItem[]>([]);
  const [historyMapSelection, setHistoryMapSelection] = useState<Record<string, string>>({});
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [isPlannerLoading, setIsPlannerLoading] = useState(false);
  const [plannerError, setPlannerError] = useState('');
  const [monthlyCommitmentStatus, setMonthlyCommitmentStatus] = useState('');
  const [monthlyCommitmentEdits, setMonthlyCommitmentEdits] = useState<Record<string, EditableMonthlyCommitment>>({});
  const [newMonthlyCommitment, setNewMonthlyCommitment] = useState<EditableMonthlyCommitment>({
    title: '',
    amount: '',
    dayOfMonth: '1',
    category: '',
  });
  const [monthlyCsvUploadText, setMonthlyCsvUploadText] = useState('');
  const [oneOffCommitmentStatus, setOneOffCommitmentStatus] = useState('');
  const [oneOffCommitmentEdits, setOneOffCommitmentEdits] = useState<Record<string, EditableOneOffCommitment>>({});
  const [newOneOffCommitment, setNewOneOffCommitment] = useState<EditableOneOffCommitment>({
    title: '',
    amount: '',
    dueDate: getTodayIsoDate(new Date()),
    category: '',
  });
  const [oneOffCsvUploadText, setOneOffCsvUploadText] = useState('');

  const currentMonthLabel = getCurrentMonthLabel(new Date());
  const todayIsoDate = getTodayIsoDate(new Date());
  const visibleCommitments = plannerData.commitments.filter((commitment) => {
    if (forecastPeriod === 'this-month') {
      return commitment.dueDate === currentMonthLabel || isCurrentMonthIsoDate(commitment.dueDate, new Date());
    }

    return true;
  });

  const forecastCommitments = visibleCommitments.map((commitment) => ({
    ...commitment,
    resolvedCategory: getCommitmentCategory(commitment),
  }));

  const monthlyCommitments = plannerData.commitments
    .filter((commitment) => commitment.dueDate === 'Recurring')
    .map((commitment) => ({
      ...commitment,
      resolvedCategory: getCommitmentCategory(commitment),
    }))
    .sort((left, right) => Math.abs(right.amount) - Math.abs(left.amount) || left.title.localeCompare(right.title));

  const monthlyCommitmentTotal = monthlyCommitments.reduce((sum, commitment) => sum + commitment.amount, 0);

  const oneOffCommitments = plannerData.commitments
    .filter((commitment) => commitment.dueDate !== 'Recurring')
    .map((commitment) => ({
      ...commitment,
      resolvedCategory: getCommitmentCategory(commitment),
    }))
    .sort((left, right) => left.dueDate.localeCompare(right.dueDate) || left.title.localeCompare(right.title));

  const oneOffCommitmentOptions = oneOffCommitments.map((commitment) => ({
    id: commitment.id,
    label: `${commitment.title} (${commitment.dueDate})`,
  }));

  const oneOffCommitmentTotal = oneOffCommitments.reduce((sum, commitment) => sum + commitment.amount, 0);

  const suggestedMatchesByHistoryId = transactionHistory.reduce<Record<string, SuggestedMatch>>((result, item) => {
    if (item.mappedFutureCommitmentId) {
      return result;
    }

    const itemTitle = normalizeText(stripCategoryTagFromDescription(item.description));
    const itemCategoryTag = extractCategoryTagFromDescription(item.description);
    const itemAmountAbs = Math.abs(item.amount);

    let best: SuggestedMatch | null = null;

    for (const commitment of oneOffCommitments) {
      const commitmentTitle = normalizeText(commitment.title);
      const commitmentCategory = normalizeText(commitment.resolvedCategory ?? commitment.category ?? '');
      const commitmentAmountAbs = Math.abs(commitment.amount);
      const amountDelta = Math.abs(itemAmountAbs - commitmentAmountAbs);

      let score = 0;

      if (amountDelta < 0.01) {
        score += 5;
      } else if (amountDelta <= 2) {
        score += 2;
      }

      if (Math.sign(item.amount) === Math.sign(commitment.amount)) {
        score += 1;
      }

      if (itemTitle && commitmentTitle && (itemTitle.includes(commitmentTitle) || commitmentTitle.includes(itemTitle))) {
        score += 4;
      }

      if (itemCategoryTag && commitmentCategory && itemCategoryTag === commitmentCategory) {
        score += 2;
      }

      if (!best || score > best.score) {
        best = {
          commitmentId: commitment.id,
          score,
        };
      }
    }

    if (best && best.score >= 4) {
      result[item.historyId] = best;
    }

    return result;
  }, {});

  const monthlyDirtyIds = monthlyCommitments
    .filter((commitment) => {
      const edit = monthlyCommitmentEdits[commitment.id];
      if (!edit) {
        return false;
      }

      const currentCategory = commitment.category?.trim() ?? '';
      return (
        edit.title.trim() !== commitment.title
        || Number(edit.amount) !== commitment.amount
        || Number(edit.dayOfMonth) !== (commitment.dayOfMonth ?? 1)
        || edit.category.trim() !== currentCategory
      );
    })
    .map((commitment) => commitment.id);

  const oneOffDirtyIds = oneOffCommitments
    .filter((commitment) => {
      const edit = oneOffCommitmentEdits[commitment.id];
      if (!edit) {
        return false;
      }

      const currentCategory = commitment.category?.trim() ?? '';
      return (
        edit.title.trim() !== commitment.title
        || Number(edit.amount) !== commitment.amount
        || edit.dueDate.trim() !== commitment.dueDate
        || edit.category.trim() !== currentCategory
      );
    })
    .map((commitment) => commitment.id);

  const categoryTotals = Array.from(
    forecastCommitments.reduce((totals, commitment) => {
      const category = commitment.resolvedCategory;
      const currentTotal = totals.get(category) ?? 0;
      totals.set(category, currentTotal + commitment.amount);
      return totals;
    }, new Map<string, number>()).entries()
  ).sort((left, right) => right[1] - left[1] || left[0].localeCompare(right[0]));

  const openingAccountTotal = plannerData.accounts.reduce((sum, account) => sum + account.balance, 0);
  const totalIncomes = visibleCommitments
    .filter((commitment) => commitment.amount > 0)
    .reduce((sum, commitment) => sum + commitment.amount, 0);
  const totalExpenses = visibleCommitments
    .filter((commitment) => commitment.amount < 0)
    .reduce((sum, commitment) => sum + commitment.amount, 0);
  const endOfPeriodTotal = openingAccountTotal + totalIncomes + totalExpenses;
  const netMovement = totalIncomes + totalExpenses;
  const monthlyOngoingIncome = getCommitmentAmountByAnyTitle(monthlyCommitments, [
    'Salary',
    'Monthly Ongoing Commitment Income',
  ]);
  const monthlyEom = Math.abs(endOfPeriodTotal);
  const monthlyBecky = getCommitmentAmountByAnyTitle(monthlyCommitments, [
    'Becky',
    'Monthly Ongoing Commitment Becky',
  ]);
  const monthlyMortgage = getCommitmentAmountByAnyTitle(monthlyCommitments, [
    'Mortgage',
    'Morgage',
    'Monthly Ongoing Commitment Morgage',
    'Monthly Ongoing Commitment Mortgage',
  ]);
  const monthlyCu = getCommitmentAmountByAnyTitle(monthlyCommitments, [
    'CU',
    'Monthly Ongoing Commitment CU',
  ]);
  const overdraftAllowance = 1000;
  const lifeInsuranceAdjustment = getCommitmentAmountByAnyTitle(monthlyCommitments, [
    'Life Insurance',
    'Monthly Ongoing Commitment Life Insurance',
  ]) || 75;
  const creditCardRepaymentCapacity = monthlyOngoingIncome
    - monthlyEom
    - monthlyBecky
    - monthlyMortgage
    - monthlyCu
    + overdraftAllowance
    - lifeInsuranceAdjustment;

  const loadPlannerData = async () => {
    setIsPlannerLoading(true);
    setPlannerError('');

    try {
      const response = await fetch('/planner');
      if (!response.ok) {
        throw new Error('Planner request failed.');
      }

      const data = (await response.json()) as PlannerData;
      setPlannerData(data);
    } catch {
      setPlannerError('Unable to load real planner data. Ensure backend is running on port 8080.');
    } finally {
      setIsPlannerLoading(false);
    }
  };

  useEffect(() => {
    void loadPlannerData();
  }, []);

  useEffect(() => {
    if (plannerData.accounts.length === 0) {
      setSelectedAccountId('');
      setAccountBalanceInput('');
      return;
    }

    if (!selectedAccountId || !plannerData.accounts.some((account) => account.id === selectedAccountId)) {
      const preferredNames = ['Current Account', 'Credit card', 'Revolut'];
      const preferred = plannerData.accounts.find((account) => preferredNames.includes(account.name)) ?? plannerData.accounts[0];
      setSelectedAccountId(preferred.id);
      setAccountBalanceInput(String(preferred.balance));
      return;
    }

    const selected = plannerData.accounts.find((account) => account.id === selectedAccountId);
    if (selected) {
      setAccountBalanceInput(String(selected.balance));
    }
  }, [plannerData.accounts, selectedAccountId]);

  const loadTransactionHistory = async () => {
    try {
      const response = await fetch('/imports/history');
      if (!response.ok) {
        throw new Error('History request failed.');
      }
      const data = (await response.json()) as TransactionHistoryItem[];
      setTransactionHistory(data);
    } catch {
      setImportStatus('Unable to load transaction history. Ensure backend is running on port 8080.');
    }
  };

  useEffect(() => {
    void loadTransactionHistory();
  }, []);

  useEffect(() => {
    setMonthlyCommitmentEdits(
      Object.fromEntries(
        monthlyCommitments.map((commitment) => [
          commitment.id,
          {
            title: commitment.title,
            amount: String(commitment.amount),
            dayOfMonth: String(commitment.dayOfMonth ?? 1),
            category: commitment.category ?? '',
          },
        ])
      )
    );
  }, [plannerData.commitments]);

  useEffect(() => {
    setOneOffCommitmentEdits(
      Object.fromEntries(
        oneOffCommitments.map((commitment) => [
          commitment.id,
          {
            title: commitment.title,
            amount: String(commitment.amount),
            dueDate: commitment.dueDate,
            category: commitment.category ?? '',
          },
        ])
      )
    );
  }, [plannerData.commitments]);

  const previewImport = async () => {
    setIsSubmitting(true);
    setImportStatus('');
    try {
      const response = await fetch('/imports/preview', {
        method: 'POST',
        headers: {
          'Content-Type': 'text/csv',
        },
        body: csvInput,
      });

      if (!response.ok) {
        throw new Error('Preview request failed.');
      }

      const data = (await response.json()) as ImportPreview;
      setPreview(data);
      setSelectedRowIds(data.transactions.filter((transaction) => !transaction.duplicate).map((transaction) => transaction.rowId));
      setImportStatus(`Preview loaded with ${data.transactions.length} rows.`);
    } catch {
      setImportStatus('Unable to preview import. Ensure backend is running on port 8080.');
    } finally {
      setIsSubmitting(false);
    }
  };

  const toggleSelection = (rowId: string) => {
    setSelectedRowIds((current) =>
      current.includes(rowId) ? current.filter((id) => id !== rowId) : [...current, rowId]
    );
  };

  const acceptSelected = async () => {
    if (!preview || selectedRowIds.length === 0) {
      setImportStatus('Select at least one row before accepting import.');
      return;
    }

    setIsSubmitting(true);
    try {
      const response = await fetch('/imports/accept', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          importId: preview.importId,
          acceptedRowIds: selectedRowIds,
        }),
      });

      if (!response.ok) {
        throw new Error('Accept request failed.');
      }

      const result = (await response.json()) as ImportAcceptResult;
      setImportStatus(`Import accepted: ${result.acceptedCount} rows from batch ${result.importId}.`);
      await loadTransactionHistory();
    } catch {
      setImportStatus('Unable to accept import. Ensure backend is running on port 8080.');
    } finally {
      setIsSubmitting(false);
    }
  };

  const saveSelectedAccountBalance = async () => {
    const selected = plannerData.accounts.find((account) => account.id === selectedAccountId);
    if (!selected) {
      setImportStatus('Select an account first.');
      return;
    }

    const parsedBalance = Number(accountBalanceInput);
    if (Number.isNaN(parsedBalance)) {
      setImportStatus('Account balance must be numeric.');
      return;
    }

    setIsSubmitting(true);
    try {
      const response = await fetch(`/planner/accounts/${selected.id}`, {
        method: 'PUT',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          id: selected.id,
          name: selected.name,
          type: selected.type,
          balance: parsedBalance,
        }),
      });

      if (!response.ok) {
        throw new Error('Account update failed.');
      }

      setImportStatus(`Balance updated for ${selected.name}.`);
      await loadPlannerData();
    } catch {
      setImportStatus('Unable to update account balance. Ensure backend is running on port 8080.');
    } finally {
      setIsSubmitting(false);
    }
  };

  const mapHistoryToFuture = async (historyId: string, explicitCommitmentId?: string) => {
    const futureCommitmentId = explicitCommitmentId ?? historyMapSelection[historyId];
    if (!futureCommitmentId) {
      setImportStatus('Select a future one-off commitment before mapping.');
      return;
    }

    setIsSubmitting(true);
    try {
      const response = await fetch('/imports/map-to-future', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          historyId,
          futureCommitmentId,
        }),
      });

      if (!response.ok) {
        throw new Error('Map to future failed.');
      }

      setImportStatus('Transaction mapped to future commitment.');
      await loadTransactionHistory();
    } catch {
      setImportStatus('Unable to map transaction to future commitment. Ensure backend is running on port 8080.');
    } finally {
      setIsSubmitting(false);
    }
  };

  const setMonthlyField = (id: string, field: keyof EditableMonthlyCommitment, value: string) => {
    setMonthlyCommitmentEdits((current) => ({
      ...current,
      [id]: {
        ...(current[id] ?? { title: '', amount: '', dayOfMonth: '1', category: '' }),
        [field]: value,
      },
    }));
  };

  const buildMonthlyCommitmentPayload = (edit: EditableMonthlyCommitment): MonthlyCommitmentPayload | null => {
    const parsedAmount = Number(edit.amount);
    const parsedDay = Number(edit.dayOfMonth);
    if (!edit.title.trim() || Number.isNaN(parsedAmount) || Number.isNaN(parsedDay) || !Number.isInteger(parsedDay)) {
      return null;
    }

    if (parsedDay < 1 || parsedDay > 31) {
      return null;
    }

    return {
      title: edit.title.trim(),
      amount: parsedAmount,
      dueDate: 'Recurring',
      category: edit.category.trim() ? edit.category.trim() : null,
      dayOfMonth: parsedDay,
    };
  };

  const saveMonthlyCommitment = async (id: string) => {
    const edit = monthlyCommitmentEdits[id];
    if (!edit) {
      return;
    }

    const payload = buildMonthlyCommitmentPayload(edit);
    if (!payload) {
      setMonthlyCommitmentStatus('Monthly commitment requires title, numeric amount, and day of month (1-31).');
      return;
    }

    setIsSubmitting(true);
    setMonthlyCommitmentStatus('');
    try {
      const response = await fetch(`/planner/commitments/${id}`, {
        method: 'PUT',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify(payload),
      });

      if (!response.ok) {
        throw new Error('Monthly commitment update failed.');
      }

      setMonthlyCommitmentStatus('Monthly commitment saved.');
      await loadPlannerData();
    } catch {
      setMonthlyCommitmentStatus('Unable to save monthly commitment. Check backend and try again.');
    } finally {
      setIsSubmitting(false);
    }
  };

  const saveAllMonthlyCommitments = async () => {
    if (monthlyDirtyIds.length === 0) {
      setMonthlyCommitmentStatus('No unsaved monthly commitment changes.');
      return;
    }

    const payloads = monthlyDirtyIds.map((id) => {
      const edit = monthlyCommitmentEdits[id];
      if (!edit) {
        return null;
      }
      const payload = buildMonthlyCommitmentPayload(edit);
      if (!payload) {
        return null;
      }
      return { id, payload };
    });

    if (payloads.some((entry) => entry === null)) {
      setMonthlyCommitmentStatus('Fix invalid rows before page-level save (title, numeric amount, day 1-31).');
      return;
    }

    const validPayloads = payloads.filter((entry): entry is { id: string; payload: MonthlyCommitmentPayload } => entry !== null);

    setIsSubmitting(true);
    setMonthlyCommitmentStatus('');
    let savedCount = 0;
    const failedIds: string[] = [];

    try {
      for (const entry of validPayloads) {
        const response = await fetch(`/planner/commitments/${entry.id}`, {
          method: 'PUT',
          headers: {
            'Content-Type': 'application/json',
          },
          body: JSON.stringify(entry.payload),
        });

        if (!response.ok) {
          failedIds.push(entry.id);
          continue;
        }

        savedCount += 1;
      }

      if (failedIds.length > 0) {
        setMonthlyCommitmentStatus(`Saved ${savedCount} row(s). Failed: ${failedIds.join(', ')}.`);
      } else {
        setMonthlyCommitmentStatus(`Saved ${savedCount} monthly commitment row(s).`);
      }

      await loadPlannerData();
    } catch {
      setMonthlyCommitmentStatus('Unable to save all monthly commitments. Check backend and try again.');
    } finally {
      setIsSubmitting(false);
    }
  };

  const deleteMonthlyCommitment = async (id: string) => {
    setIsSubmitting(true);
    setMonthlyCommitmentStatus('');
    try {
      const response = await fetch(`/planner/commitments/${id}`, {
        method: 'DELETE',
      });

      if (!response.ok) {
        throw new Error('Monthly commitment delete failed.');
      }

      setMonthlyCommitmentStatus('Monthly commitment deleted.');
      await loadPlannerData();
    } catch {
      setMonthlyCommitmentStatus('Unable to delete monthly commitment. Check backend and try again.');
    } finally {
      setIsSubmitting(false);
    }
  };

  const createMonthlyCommitment = async () => {
    const parsedAmount = Number(newMonthlyCommitment.amount);
    const parsedDay = Number(newMonthlyCommitment.dayOfMonth);
    if (!newMonthlyCommitment.title.trim() || Number.isNaN(parsedAmount) || Number.isNaN(parsedDay)) {
      setMonthlyCommitmentStatus('New monthly commitment requires title, numeric amount, and numeric day of month.');
      return;
    }

    setIsSubmitting(true);
    setMonthlyCommitmentStatus('');
    try {
      const response = await fetch('/planner/commitments', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          id: `MC-${Date.now()}`,
          title: newMonthlyCommitment.title.trim(),
          amount: parsedAmount,
          dueDate: 'Recurring',
          category: newMonthlyCommitment.category.trim() ? newMonthlyCommitment.category.trim() : null,
          dayOfMonth: parsedDay,
        }),
      });

      if (!response.ok) {
        throw new Error('Monthly commitment create failed.');
      }

      setMonthlyCommitmentStatus('Monthly commitment created.');
      setNewMonthlyCommitment({ title: '', amount: '', dayOfMonth: '1', category: '' });
      await loadPlannerData();
    } catch {
      setMonthlyCommitmentStatus('Unable to create monthly commitment. Check backend and try again.');
    } finally {
      setIsSubmitting(false);
    }
  };

  const downloadMonthlyCommitmentsCsv = () => {
    const rows = monthlyCommitments.map((commitment) => [
      commitment.id,
      commitment.title,
      String(commitment.amount),
      String(commitment.dayOfMonth ?? 1),
      commitment.category ?? '',
    ]);
    downloadCsvFile('monthly-commitments.csv', ['id', 'title', 'amount', 'dayOfMonth', 'category'], rows);
    setMonthlyCommitmentStatus(`Downloaded ${rows.length} monthly commitment row(s) as CSV.`);
  };

  const onMonthlyCsvSelected = async (file: File | null) => {
    if (!file) {
      return;
    }
    try {
      const text = await readFileAsText(file);
      setMonthlyCsvUploadText(text);
      setMonthlyCommitmentStatus('Monthly CSV loaded. Click Upload CSV to apply changes.');
    } catch {
      setMonthlyCommitmentStatus('Unable to read monthly CSV file.');
    }
  };

  const uploadMonthlyCommitmentsCsv = async () => {
    if (!monthlyCsvUploadText.trim()) {
      setMonthlyCommitmentStatus('Select a monthly CSV file before upload.');
      return;
    }

    const rows = parseCsvText(monthlyCsvUploadText);
    if (rows.length < 2) {
      setMonthlyCommitmentStatus('Monthly CSV must include header and at least one row.');
      return;
    }

    const header = rows[0].map((cell) => cell.trim());
    const expectedHeader = ['id', 'title', 'amount', 'dayOfMonth', 'category'];
    if (header.join(',') !== expectedHeader.join(',')) {
      setMonthlyCommitmentStatus('Invalid monthly CSV header. Expected: id,title,amount,dayOfMonth,category');
      return;
    }

    setIsSubmitting(true);
    setMonthlyCommitmentStatus('');
    let created = 0;
    let updated = 0;
    let failed = 0;

    try {
      for (const row of rows.slice(1)) {
        const [idCell = '', title = '', amount = '', dayOfMonth = '', category = ''] = row;
        const payload = buildMonthlyCommitmentPayload({
          title,
          amount,
          dayOfMonth,
          category,
        });

        if (!payload) {
          failed += 1;
          continue;
        }

        const normalizedId = idCell.trim();
        if (normalizedId) {
          const response = await fetch(`/planner/commitments/${normalizedId}`, {
            method: 'PUT',
            headers: {
              'Content-Type': 'application/json',
            },
            body: JSON.stringify(payload),
          });

          if (response.ok) {
            updated += 1;
          } else {
            failed += 1;
          }
          continue;
        }

        const response = await fetch('/planner/commitments', {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
          },
          body: JSON.stringify({
            id: `MC-${Date.now()}-${created + updated + failed}`,
            ...payload,
          }),
        });

        if (response.ok) {
          created += 1;
        } else {
          failed += 1;
        }
      }

      setMonthlyCommitmentStatus(`Monthly CSV upload complete. Created ${created}, updated ${updated}, failed ${failed}.`);
      setMonthlyCsvUploadText('');
      await loadPlannerData();
    } catch {
      setMonthlyCommitmentStatus('Unable to upload monthly CSV. Check backend and try again.');
    } finally {
      setIsSubmitting(false);
    }
  };

  const setOneOffField = (id: string, field: keyof EditableOneOffCommitment, value: string) => {
    setOneOffCommitmentEdits((current) => ({
      ...current,
      [id]: {
        ...(current[id] ?? { title: '', amount: '', dueDate: todayIsoDate, category: '' }),
        [field]: value,
      },
    }));
  };

  const isValidDueDate = (value: string) => isIsoDate(value);

  const buildOneOffCommitmentPayload = (edit: EditableOneOffCommitment): OneOffCommitmentPayload | null => {
    const parsedAmount = Number(edit.amount);
    if (!edit.title.trim() || Number.isNaN(parsedAmount) || !isValidDueDate(edit.dueDate)) {
      return null;
    }

    return {
      title: edit.title.trim(),
      amount: parsedAmount,
      dueDate: edit.dueDate.trim(),
      category: edit.category.trim() ? edit.category.trim() : null,
      dayOfMonth: null,
    };
  };

  const saveOneOffCommitment = async (id: string) => {
    const edit = oneOffCommitmentEdits[id];
    if (!edit) {
      return;
    }

    const payload = buildOneOffCommitmentPayload(edit);
    if (!payload) {
      setOneOffCommitmentStatus('One-off commitment requires title, numeric amount, and due date like YYYY-MM-DD.');
      return;
    }

    setIsSubmitting(true);
    setOneOffCommitmentStatus('');
    try {
      const response = await fetch(`/planner/commitments/${id}`, {
        method: 'PUT',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify(payload),
      });

      if (!response.ok) {
        throw new Error('One-off commitment update failed.');
      }

      setOneOffCommitmentStatus('One-off commitment saved.');
      await loadPlannerData();
    } catch {
      setOneOffCommitmentStatus('Unable to save one-off commitment. Check backend and try again.');
    } finally {
      setIsSubmitting(false);
    }
  };

  const saveAllOneOffCommitments = async () => {
    if (oneOffDirtyIds.length === 0) {
      setOneOffCommitmentStatus('No unsaved one-off commitment changes.');
      return;
    }

    const payloads = oneOffDirtyIds.map((id) => {
      const edit = oneOffCommitmentEdits[id];
      if (!edit) {
        return null;
      }
      const payload = buildOneOffCommitmentPayload(edit);
      if (!payload) {
        return null;
      }
      return { id, payload };
    });

    if (payloads.some((entry) => entry === null)) {
      setOneOffCommitmentStatus('Fix invalid one-off rows before page-level save (title, numeric amount, due date YYYY-MM-DD).');
      return;
    }

    const validPayloads = payloads.filter((entry): entry is { id: string; payload: OneOffCommitmentPayload } => entry !== null);

    setIsSubmitting(true);
    setOneOffCommitmentStatus('');
    let savedCount = 0;
    const failedIds: string[] = [];

    try {
      for (const entry of validPayloads) {
        const response = await fetch(`/planner/commitments/${entry.id}`, {
          method: 'PUT',
          headers: {
            'Content-Type': 'application/json',
          },
          body: JSON.stringify(entry.payload),
        });

        if (!response.ok) {
          failedIds.push(entry.id);
          continue;
        }

        savedCount += 1;
      }

      if (failedIds.length > 0) {
        setOneOffCommitmentStatus(`Saved ${savedCount} row(s). Failed: ${failedIds.join(', ')}.`);
      } else {
        setOneOffCommitmentStatus(`Saved ${savedCount} one-off commitment row(s).`);
      }

      await loadPlannerData();
    } catch {
      setOneOffCommitmentStatus('Unable to save all one-off commitments. Check backend and try again.');
    } finally {
      setIsSubmitting(false);
    }
  };

  const deleteOneOffCommitment = async (id: string) => {
    setIsSubmitting(true);
    setOneOffCommitmentStatus('');
    try {
      const response = await fetch(`/planner/commitments/${id}`, {
        method: 'DELETE',
      });

      if (!response.ok) {
        throw new Error('One-off commitment delete failed.');
      }

      setOneOffCommitmentStatus('One-off commitment deleted.');
      await loadPlannerData();
    } catch {
      setOneOffCommitmentStatus('Unable to delete one-off commitment. Check backend and try again.');
    } finally {
      setIsSubmitting(false);
    }
  };

  const createOneOffCommitment = async () => {
    const payload = buildOneOffCommitmentPayload(newOneOffCommitment);
    if (!payload) {
      setOneOffCommitmentStatus('New one-off commitment requires title, numeric amount, and due date like YYYY-MM-DD.');
      return;
    }

    setIsSubmitting(true);
    setOneOffCommitmentStatus('');
    try {
      const response = await fetch('/planner/commitments', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          id: `OF-${Date.now()}`,
          ...payload,
        }),
      });

      if (!response.ok) {
        throw new Error('One-off commitment create failed.');
      }

      setOneOffCommitmentStatus('One-off commitment created.');
      setNewOneOffCommitment({ title: '', amount: '', dueDate: todayIsoDate, category: '' });
      await loadPlannerData();
    } catch {
      setOneOffCommitmentStatus('Unable to create one-off commitment. Check backend and try again.');
    } finally {
      setIsSubmitting(false);
    }
  };

  const downloadOneOffCommitmentsCsv = () => {
    const rows = oneOffCommitments.map((commitment) => [
      commitment.id,
      commitment.title,
      String(commitment.amount),
      commitment.dueDate,
      commitment.category ?? '',
    ]);
    downloadCsvFile('onceoff-commitments.csv', ['id', 'title', 'amount', 'dueDate', 'category'], rows);
    setOneOffCommitmentStatus(`Downloaded ${rows.length} one-off commitment row(s) as CSV.`);
  };

  const onOneOffCsvSelected = async (file: File | null) => {
    if (!file) {
      return;
    }
    try {
      const text = await readFileAsText(file);
      setOneOffCsvUploadText(text);
      setOneOffCommitmentStatus('One-off CSV loaded. Click Upload CSV to apply changes.');
    } catch {
      setOneOffCommitmentStatus('Unable to read one-off CSV file.');
    }
  };

  const uploadOneOffCommitmentsCsv = async () => {
    if (!oneOffCsvUploadText.trim()) {
      setOneOffCommitmentStatus('Select a one-off CSV file before upload.');
      return;
    }

    const rows = parseCsvText(oneOffCsvUploadText);
    if (rows.length < 2) {
      setOneOffCommitmentStatus('One-off CSV must include header and at least one row.');
      return;
    }

    const header = rows[0].map((cell) => cell.trim());
    const expectedHeader = ['id', 'title', 'amount', 'dueDate', 'category'];
    if (header.join(',') !== expectedHeader.join(',')) {
      setOneOffCommitmentStatus('Invalid one-off CSV header. Expected: id,title,amount,dueDate,category');
      return;
    }

    setIsSubmitting(true);
    setOneOffCommitmentStatus('');
    let created = 0;
    let updated = 0;
    let failed = 0;

    try {
      for (const row of rows.slice(1)) {
        const [idCell = '', title = '', amount = '', dueDate = '', category = ''] = row;
        const payload = buildOneOffCommitmentPayload({
          title,
          amount,
          dueDate,
          category,
        });

        if (!payload) {
          failed += 1;
          continue;
        }

        const normalizedId = idCell.trim();
        if (normalizedId) {
          const response = await fetch(`/planner/commitments/${normalizedId}`, {
            method: 'PUT',
            headers: {
              'Content-Type': 'application/json',
            },
            body: JSON.stringify(payload),
          });

          if (response.ok) {
            updated += 1;
          } else {
            failed += 1;
          }
          continue;
        }

        const response = await fetch('/planner/commitments', {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
          },
          body: JSON.stringify({
            id: `OF-${Date.now()}-${created + updated + failed}`,
            ...payload,
          }),
        });

        if (response.ok) {
          created += 1;
        } else {
          failed += 1;
        }
      }

      setOneOffCommitmentStatus(`One-off CSV upload complete. Created ${created}, updated ${updated}, failed ${failed}.`);
      setOneOffCsvUploadText('');
      await loadPlannerData();
    } catch {
      setOneOffCommitmentStatus('Unable to upload one-off CSV. Check backend and try again.');
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <section>
      <h2>Planner workspace</h2>
      <p>Manage accounts, categories, commitments, and transaction imports for the planner role.</p>

      <div className="planner-tabs" role="tablist" aria-label="Planner tabs">
        <button
          type="button"
          role="tab"
          aria-selected={activeTab === 'forecast-current-month'}
          className={activeTab === 'forecast-current-month' ? 'active' : ''}
          onClick={() => setActiveTab('forecast-current-month')}
        >
          Forecast and Current Month
        </button>
        <button
          type="button"
          role="tab"
          aria-selected={activeTab === 'monthly-commitments'}
          className={activeTab === 'monthly-commitments' ? 'active' : ''}
          onClick={() => setActiveTab('monthly-commitments')}
        >
          Monthly Commitments
        </button>
        <button
          type="button"
          role="tab"
          aria-selected={activeTab === 'once-off-commitments'}
          className={activeTab === 'once-off-commitments' ? 'active' : ''}
          onClick={() => setActiveTab('once-off-commitments')}
        >
          Once Off Commitments
        </button>
        <button
          type="button"
          role="tab"
          aria-selected={activeTab === 'account-management'}
          className={activeTab === 'account-management' ? 'active' : ''}
          onClick={() => setActiveTab('account-management')}
        >
          Account Management
        </button>
      </div>

      {activeTab === 'forecast-current-month' && (
        <>
          <div className="workspace-card forecast-card">
            <div className="forecast-title-row">
              <div>
                <h3>Forecast working</h3>
                <p>Trial balance style view for the current month.</p>
              </div>
              <div className="forecast-controls">
                <label htmlFor="forecast-period">Forecast</label>
                <select
                  id="forecast-period"
                  value={forecastPeriod}
                  onChange={(event) => setForecastPeriod(event.target.value as ForecastPeriod)}
                >
                  <option value="this-month">This month</option>
                </select>
              </div>
            </div>

            <div className="forecast-formula">
              <span>Working:</span>
              <strong>{formatCurrency(openingAccountTotal)} + {formatCurrency(totalIncomes)} + {formatCurrency(totalExpenses)} = {formatCurrency(endOfPeriodTotal)}</strong>
            </div>

            <div className="forecast-summary-grid">
              <div className="summary-card">
                <h3>Opening balances</h3>
                <p>{formatCurrency(openingAccountTotal)}</p>
              </div>
              <div className="summary-card highlight">
                <h3>Total incomes</h3>
                <p className="cashflow-positive">{formatCurrency(totalIncomes)}</p>
              </div>
              <div className="summary-card highlight-negative">
                <h3>Total expenses</h3>
                <p className="cashflow-negative">{formatCurrency(totalExpenses)}</p>
              </div>
              <div className="summary-card highlight">
                <h3>End of period total</h3>
                <p>{formatCurrency(endOfPeriodTotal)}</p>
              </div>
            </div>

            <div className="forecast-controls">
              <span className="forecast-note">This month excludes recurring items already counted elsewhere.</span>
            </div>
          </div>

          <div className="planner-grid">
            <div className="planner-left-column">
              <div className="workspace-card">
                <h3>Accounts</h3>
                {isPlannerLoading && <p>Loading real account data...</p>}
                {!isPlannerLoading && plannerError && <p>{plannerError}</p>}
                <p className="ledger-caption">Opening balances + current month income and expenses = end of period total.</p>
                <table>
                  <thead>
                    <tr>
                      <th>Working line</th>
                      <th>Amount</th>
                      <th>Maths</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr>
                      <td>Opening account balances</td>
                      <td>{formatCurrency(openingAccountTotal)}</td>
                      <td>{plannerData.accounts.map((account) => `${account.name} ${formatCurrency(account.balance)}`).join(' + ')}</td>
                    </tr>
                    <tr>
                      <td>Total incomes</td>
                      <td className="cashflow-positive">{formatCurrency(totalIncomes)}</td>
                      <td>-</td>
                    </tr>
                    <tr>
                      <td>Total expenses</td>
                      <td className="cashflow-negative">{formatCurrency(totalExpenses)}</td>
                      <td>-</td>
                    </tr>
                    <tr className="ledger-total-row">
                      <td>End of period total</td>
                      <td>{formatCurrency(endOfPeriodTotal)}</td>
                      <td>{formatCurrency(openingAccountTotal)} + {formatCurrency(totalIncomes)} + {formatCurrency(totalExpenses)}</td>
                    </tr>
                  </tbody>
                </table>
              </div>

              <div className="workspace-card">
                <h3>Categories</h3>
                {isPlannerLoading && <p>Loading real category data...</p>}
                <p className="ledger-caption">Each category is the sum of the commitments assigned to it in the selected period.</p>
                <table>
                  <thead>
                    <tr>
                      <th>Category</th>
                      <th>Total</th>
                    </tr>
                  </thead>
                  <tbody>
                    {categoryTotals.map(([categoryName, total]) => (
                      <tr key={categoryName}>
                        <td>{categoryName}</td>
                        <td>{formatCurrency(total)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>

            <div className="workspace-card credit-card-repayment-card">
              <h3>Credit Card Repayment</h3>
              <p className="ledger-caption">Salary - End of period total - Becky - Mortgage - CU + Overdraft - Life insurance.</p>
              <table>
                <thead>
                  <tr>
                    <th>Working line</th>
                    <th>Amount</th>
                  </tr>
                </thead>
                <tbody>
                  <tr>
                    <td>Monthly Ongoing Commitment Income</td>
                    <td>{formatCurrency(monthlyOngoingIncome)}</td>
                  </tr>
                  <tr>
                    <td>Minus Monthly EOM</td>
                    <td className="cashflow-negative">{formatCurrency(-monthlyEom)}</td>
                  </tr>
                  <tr>
                    <td>Minus Monthly Ongoing Commitment Becky</td>
                    <td className="cashflow-negative">{formatCurrency(-monthlyBecky)}</td>
                  </tr>
                  <tr>
                    <td>Minus Monthly Ongoing Commitment Morgage</td>
                    <td className="cashflow-negative">{formatCurrency(-monthlyMortgage)}</td>
                  </tr>
                  <tr>
                    <td>Minus Monthly Ongoing Commitment CU</td>
                    <td className="cashflow-negative">{formatCurrency(-monthlyCu)}</td>
                  </tr>
                  <tr>
                    <td>Plus overdraft</td>
                    <td className="cashflow-positive">{formatCurrency(overdraftAllowance)}</td>
                  </tr>
                  <tr>
                    <td>Minus life insurance</td>
                    <td className="cashflow-negative">{formatCurrency(-lifeInsuranceAdjustment)}</td>
                  </tr>
                  <tr className="ledger-total-row">
                    <td>Credit card repayment capacity</td>
                    <td>{formatCurrency(creditCardRepaymentCapacity)}</td>
                  </tr>
                </tbody>
              </table>
            </div>

            <div className="workspace-card">
              <h3>Commitments</h3>
              {isPlannerLoading && <p>Loading real commitment data...</p>}
              <p className="ledger-caption">Filtered to {forecastPeriod === 'this-month' ? currentMonthLabel : 'all periods'}.</p>
              <table>
                <thead>
                  <tr>
                    <th>Commitment</th>
                    <th>Amount</th>
                    <th>Due</th>
                    <th>Category</th>
                  </tr>
                </thead>
                <tbody>
                  {forecastCommitments.map((commitment) => (
                    <tr key={commitment.id}>
                      <td>{commitment.title}</td>
                      <td>{formatCurrency(commitment.amount)}</td>
                      <td>{commitment.dueDate}</td>
                      <td>{commitment.resolvedCategory}</td>
                    </tr>
                  ))}
                </tbody>
                <tfoot>
                  <tr>
                    <td>Net movement</td>
                    <td>{formatCurrency(netMovement)}</td>
                    <td colSpan={2}>{formatCurrency(totalIncomes)} + {formatCurrency(totalExpenses)}</td>
                  </tr>
                </tfoot>
              </table>
            </div>
          </div>
        </>
      )}

      {activeTab === 'monthly-commitments' && (
        <div className="workspace-card">
          <h3>Monthly commitments grid</h3>
          <p className="ledger-caption">Recurring commitments are defined here. Conversion to future transaction instances is handled by a monthly batch job.</p>
          {isPlannerLoading && <p>Loading recurring commitments...</p>}
          {!isPlannerLoading && plannerError && <p>{plannerError}</p>}
          {monthlyCommitmentStatus && <p className="import-status">{monthlyCommitmentStatus}</p>}

          <div className="monthly-commitment-toolbar">
            <span>{monthlyDirtyIds.length} unsaved change(s)</span>
            <div className="bulk-csv-actions">
              <button type="button" className="grid-action" onClick={saveAllMonthlyCommitments} disabled={isSubmitting || monthlyDirtyIds.length === 0}>
                Save all changes
              </button>
              <button type="button" className="grid-action" onClick={downloadMonthlyCommitmentsCsv} disabled={isSubmitting}>
                Download CSV
              </button>
              <input
                type="file"
                accept=".csv,text/csv"
                className="csv-file-input"
                onChange={(event) => void onMonthlyCsvSelected(event.target.files?.[0] ?? null)}
                disabled={isSubmitting}
              />
              <button type="button" className="grid-action" onClick={uploadMonthlyCommitmentsCsv} disabled={isSubmitting}>
                Upload CSV
              </button>
            </div>
          </div>

          <div className="monthly-commitment-create-row">
            <input
              className="grid-input"
              value={newMonthlyCommitment.title}
              placeholder="New commitment title"
              onChange={(event) => setNewMonthlyCommitment((current) => ({ ...current, title: event.target.value }))}
            />
            <input
              className="grid-input"
              value={newMonthlyCommitment.amount}
              placeholder="Amount"
              onChange={(event) => setNewMonthlyCommitment((current) => ({ ...current, amount: event.target.value }))}
            />
            <input
              className="grid-input"
              value={newMonthlyCommitment.dayOfMonth}
              placeholder="Day"
              onChange={(event) => setNewMonthlyCommitment((current) => ({ ...current, dayOfMonth: event.target.value }))}
            />
            <input
              className="grid-input"
              value={newMonthlyCommitment.category}
              placeholder="Category"
              onChange={(event) => setNewMonthlyCommitment((current) => ({ ...current, category: event.target.value }))}
            />
            <button type="button" className="grid-action" onClick={createMonthlyCommitment} disabled={isSubmitting}>
              Add
            </button>
          </div>

          <table>
            <thead>
              <tr>
                <th>Commitment</th>
                <th>Amount</th>
                <th>Day of month</th>
                <th>Category</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              {monthlyCommitments.map((commitment) => (
                <tr key={commitment.id} className={monthlyDirtyIds.includes(commitment.id) ? 'dirty-row' : ''}>
                  <td>
                    <input
                      className="grid-input"
                      value={monthlyCommitmentEdits[commitment.id]?.title ?? ''}
                      onChange={(event) => setMonthlyField(commitment.id, 'title', event.target.value)}
                    />
                  </td>
                  <td>
                    <input
                      className="grid-input"
                      value={monthlyCommitmentEdits[commitment.id]?.amount ?? ''}
                      onChange={(event) => setMonthlyField(commitment.id, 'amount', event.target.value)}
                    />
                  </td>
                  <td>
                    <input
                      className="grid-input"
                      value={monthlyCommitmentEdits[commitment.id]?.dayOfMonth ?? '1'}
                      onChange={(event) => setMonthlyField(commitment.id, 'dayOfMonth', event.target.value)}
                    />
                  </td>
                  <td>
                    <input
                      className="grid-input"
                      value={monthlyCommitmentEdits[commitment.id]?.category ?? ''}
                      onChange={(event) => setMonthlyField(commitment.id, 'category', event.target.value)}
                    />
                  </td>
                  <td>
                    <div className="grid-action-row">
                      <button type="button" className="grid-action" onClick={() => saveMonthlyCommitment(commitment.id)} disabled={isSubmitting}>
                        Save
                      </button>
                      <button type="button" className="grid-action danger" onClick={() => deleteMonthlyCommitment(commitment.id)} disabled={isSubmitting}>
                        Delete
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
              {monthlyCommitments.length === 0 && (
                <tr>
                  <td colSpan={5}>No recurring commitments found.</td>
                </tr>
              )}
            </tbody>
            <tfoot>
              <tr>
                <td>Total recurring monthly amount</td>
                <td>{formatCurrency(monthlyCommitmentTotal)}</td>
                <td colSpan={3}>Recurring definition total</td>
              </tr>
            </tfoot>
          </table>
        </div>
      )}

      {activeTab === 'account-management' && (
        <div className="workspace-card import-card">
          <h3>Account management</h3>
          <p>Toggle account balances, import transactions, review transaction history, and map imported rows to future one-off commitments.</p>

          <div className="account-management-panel">
            <label htmlFor="account-select">Account</label>
            <select
              id="account-select"
              value={selectedAccountId}
              onChange={(event) => {
                const nextId = event.target.value;
                setSelectedAccountId(nextId);
                const nextAccount = plannerData.accounts.find((account) => account.id === nextId);
                setAccountBalanceInput(nextAccount ? String(nextAccount.balance) : '');
              }}
            >
              {plannerData.accounts.map((account) => (
                <option key={account.id} value={account.id}>{account.name}</option>
              ))}
            </select>
            <label htmlFor="account-balance">Balance</label>
            <input
              id="account-balance"
              className="grid-input"
              value={accountBalanceInput}
              onChange={(event) => setAccountBalanceInput(event.target.value)}
            />
            <button type="button" className="grid-action" onClick={saveSelectedAccountBalance} disabled={isSubmitting || !selectedAccountId}>
              Save balance
            </button>
          </div>

          <h4>Import transactions</h4>
          <p>Paste CSV content, preview duplicates, and accept selected rows.</p>

          <textarea
            className="import-input"
            value={csvInput}
            onChange={(event) => setCsvInput(event.target.value)}
            rows={7}
          />

          <div className="import-actions">
            <button type="button" onClick={previewImport} disabled={isSubmitting}>
              Preview import
            </button>
            <button type="button" onClick={acceptSelected} disabled={isSubmitting || !preview}>
              Accept selected rows
            </button>
          </div>

          {importStatus && <p className="import-status">{importStatus}</p>}

          {preview && (
            <table>
              <thead>
                <tr>
                  <th>Accept</th>
                  <th>Date</th>
                  <th>Description</th>
                  <th>Amount</th>
                  <th>Account</th>
                  <th>Status</th>
                </tr>
              </thead>
              <tbody>
                {preview.transactions.map((transaction) => (
                  <tr key={transaction.rowId} className={transaction.duplicate ? 'duplicate-row' : ''}>
                    <td>
                      <input
                        type="checkbox"
                        checked={selectedRowIds.includes(transaction.rowId)}
                        onChange={() => toggleSelection(transaction.rowId)}
                      />
                    </td>
                    <td>{transaction.date}</td>
                    <td>{transaction.description}</td>
                    <td>{transaction.amount.toLocaleString(undefined, { style: 'currency', currency: 'EUR' })}</td>
                    <td>{transaction.account}</td>
                    <td>{transaction.duplicate ? 'Duplicate' : 'New'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}

          <h4>Transaction history</h4>
          {transactionHistory.length === 0 && <p className="ledger-caption">No imported transaction history yet.</p>}
          {transactionHistory.length > 0 && (
            <table>
              <thead>
                <tr>
                  <th>Date</th>
                  <th>Description</th>
                  <th>Amount</th>
                  <th>Account</th>
                  <th>Mapped future</th>
                  <th>Suggested</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                {transactionHistory.map((item) => (
                  <tr key={item.historyId}>
                    <td>{item.date}</td>
                    <td>{item.description}</td>
                    <td>{formatCurrency(item.amount)}</td>
                    <td>{item.account}</td>
                    <td>{item.mappedFutureCommitmentId ?? 'Not mapped'}</td>
                    <td>{suggestedMatchesByHistoryId[item.historyId]?.commitmentId ?? 'No suggestion'}</td>
                    <td>
                      <div className="grid-action-row">
                        <select
                          value={historyMapSelection[item.historyId] ?? suggestedMatchesByHistoryId[item.historyId]?.commitmentId ?? ''}
                          onChange={(event) => setHistoryMapSelection((current) => ({ ...current, [item.historyId]: event.target.value }))}
                        >
                          <option value="">Select future one-off</option>
                          {oneOffCommitmentOptions.map((option) => (
                            <option key={option.id} value={option.id}>{option.label}</option>
                          ))}
                        </select>
                        <button type="button" className="grid-action" onClick={() => void mapHistoryToFuture(item.historyId)} disabled={isSubmitting}>
                          Map
                        </button>
                        <button
                          type="button"
                          className="grid-action"
                          onClick={() => void mapHistoryToFuture(item.historyId, suggestedMatchesByHistoryId[item.historyId]?.commitmentId)}
                          disabled={isSubmitting || !suggestedMatchesByHistoryId[item.historyId]}
                        >
                          Use suggestion
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      )}

      {activeTab === 'once-off-commitments' && (
        <div className="workspace-card">
          <h3>Once off commitments grid</h3>
          <p className="ledger-caption">Future one-off commitments are maintained as transaction rows with due month labels.</p>
          {isPlannerLoading && <p>Loading future one-off commitments...</p>}
          {!isPlannerLoading && plannerError && <p>{plannerError}</p>}
          {oneOffCommitmentStatus && <p className="import-status">{oneOffCommitmentStatus}</p>}

          <div className="monthly-commitment-toolbar">
            <span>{oneOffDirtyIds.length} unsaved change(s)</span>
            <div className="bulk-csv-actions">
              <button type="button" className="grid-action" onClick={saveAllOneOffCommitments} disabled={isSubmitting || oneOffDirtyIds.length === 0}>
                Save all changes
              </button>
              <button type="button" className="grid-action" onClick={downloadOneOffCommitmentsCsv} disabled={isSubmitting}>
                Download CSV
              </button>
              <input
                type="file"
                accept=".csv,text/csv"
                className="csv-file-input"
                onChange={(event) => void onOneOffCsvSelected(event.target.files?.[0] ?? null)}
                disabled={isSubmitting}
              />
              <button type="button" className="grid-action" onClick={uploadOneOffCommitmentsCsv} disabled={isSubmitting}>
                Upload CSV
              </button>
            </div>
          </div>

          <div className="onceoff-commitment-create-row">
            <input
              className="grid-input"
              value={newOneOffCommitment.title}
              placeholder="New one-off title"
              onChange={(event) => setNewOneOffCommitment((current) => ({ ...current, title: event.target.value }))}
            />
            <input
              className="grid-input"
              value={newOneOffCommitment.amount}
              placeholder="Amount"
              onChange={(event) => setNewOneOffCommitment((current) => ({ ...current, amount: event.target.value }))}
            />
            <input
              className="grid-input"
              value={newOneOffCommitment.dueDate}
              placeholder="Due date (YYYY-MM-DD)"
              type="date"
              onChange={(event) => setNewOneOffCommitment((current) => ({ ...current, dueDate: event.target.value }))}
            />
            <input
              className="grid-input"
              value={newOneOffCommitment.category}
              placeholder="Category"
              onChange={(event) => setNewOneOffCommitment((current) => ({ ...current, category: event.target.value }))}
            />
            <button type="button" className="grid-action" onClick={createOneOffCommitment} disabled={isSubmitting}>
              Add
            </button>
          </div>

          <table>
            <thead>
              <tr>
                <th>Commitment</th>
                <th>Amount</th>
                <th>Due month</th>
                <th>Category</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              {oneOffCommitments.map((commitment) => (
                <tr key={commitment.id} className={oneOffDirtyIds.includes(commitment.id) ? 'dirty-row' : ''}>
                  <td>
                    <input
                      className="grid-input"
                      value={oneOffCommitmentEdits[commitment.id]?.title ?? ''}
                      onChange={(event) => setOneOffField(commitment.id, 'title', event.target.value)}
                    />
                  </td>
                  <td>
                    <input
                      className="grid-input"
                      value={oneOffCommitmentEdits[commitment.id]?.amount ?? ''}
                      onChange={(event) => setOneOffField(commitment.id, 'amount', event.target.value)}
                    />
                  </td>
                  <td>
                    <input
                      className="grid-input"
                      type="date"
                      value={oneOffCommitmentEdits[commitment.id]?.dueDate ?? todayIsoDate}
                      onChange={(event) => setOneOffField(commitment.id, 'dueDate', event.target.value)}
                    />
                  </td>
                  <td>
                    <input
                      className="grid-input"
                      value={oneOffCommitmentEdits[commitment.id]?.category ?? ''}
                      onChange={(event) => setOneOffField(commitment.id, 'category', event.target.value)}
                    />
                  </td>
                  <td>
                    <div className="grid-action-row">
                      <button type="button" className="grid-action" onClick={() => saveOneOffCommitment(commitment.id)} disabled={isSubmitting}>
                        Save
                      </button>
                      <button type="button" className="grid-action danger" onClick={() => deleteOneOffCommitment(commitment.id)} disabled={isSubmitting}>
                        Delete
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
              {oneOffCommitments.length === 0 && (
                <tr>
                  <td colSpan={5}>No one-off commitments found.</td>
                </tr>
              )}
            </tbody>
            <tfoot>
              <tr>
                <td>Total future one-off amount</td>
                <td>{formatCurrency(oneOffCommitmentTotal)}</td>
                <td colSpan={3}>One-off future transaction total</td>
              </tr>
            </tfoot>
          </table>
        </div>
      )}
    </section>
  );
}

export default PlannerWorkspace;

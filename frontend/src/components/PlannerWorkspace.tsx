import { useState } from 'react';

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

const accounts: Account[] = [
  { id: 'A1', name: 'Checking', type: 'Bank', balance: 4120.75 },
  { id: 'A2', name: 'Savings', type: 'Bank', balance: 12500.0 },
  { id: 'A3', name: 'Credit Card', type: 'Credit', balance: -850.1 },
];

const categories: Category[] = [
  { id: 'C1', name: 'Housing', budget: '$1,200' },
  { id: 'C2', name: 'Groceries', budget: '$450' },
  { id: 'C3', name: 'Utilities', budget: '$250' },
];

const commitments: Commitment[] = [
  { id: 'M1', title: 'Rent', amount: 1200.0, dueDate: '2026-07-01' },
  { id: 'M2', title: 'Electricity', amount: 95.6, dueDate: '2026-07-05' },
  { id: 'M3', title: 'Internet', amount: 65.0, dueDate: '2026-07-10' },
];

const sampleCsv = `date,description,amount,account
2026-06-01,Groceries,-54.20,Checking
2026-06-01,Groceries,-54.20,Checking
2026-06-03,Fuel,-68.11,Credit Card
2026-06-04,Salary,3200.00,Checking`;

function PlannerWorkspace() {
  const [csvInput, setCsvInput] = useState(sampleCsv);
  const [preview, setPreview] = useState<ImportPreview | null>(null);
  const [selectedRowIds, setSelectedRowIds] = useState<string[]>([]);
  const [importStatus, setImportStatus] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);

  const previewImport = async () => {
    setIsSubmitting(true);
    setImportStatus('');
    try {
      const response = await fetch('http://localhost:8080/imports/preview', {
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
      const response = await fetch('http://localhost:8080/imports/accept', {
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
    } catch {
      setImportStatus('Unable to accept import. Ensure backend is running on port 8080.');
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <section>
      <h2>Planner workspace</h2>
      <p>Manage accounts, categories, commitments, and transaction imports for the planner role.</p>

      <div className="planner-grid">
        <div className="workspace-card">
          <h3>Accounts</h3>
          <table>
            <thead>
              <tr>
                <th>Name</th>
                <th>Type</th>
                <th>Balance</th>
              </tr>
            </thead>
            <tbody>
              {accounts.map((account) => (
                <tr key={account.id}>
                  <td>{account.name}</td>
                  <td>{account.type}</td>
                  <td>{account.balance.toLocaleString(undefined, { style: 'currency', currency: 'USD' })}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>

        <div className="workspace-card">
          <h3>Categories</h3>
          <table>
            <thead>
              <tr>
                <th>Category</th>
                <th>Budget</th>
              </tr>
            </thead>
            <tbody>
              {categories.map((category) => (
                <tr key={category.id}>
                  <td>{category.name}</td>
                  <td>{category.budget}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>

        <div className="workspace-card">
          <h3>Commitments</h3>
          <table>
            <thead>
              <tr>
                <th>Commitment</th>
                <th>Amount</th>
                <th>Due</th>
              </tr>
            </thead>
            <tbody>
              {commitments.map((commitment) => (
                <tr key={commitment.id}>
                  <td>{commitment.title}</td>
                  <td>{commitment.amount.toLocaleString(undefined, { style: 'currency', currency: 'USD' })}</td>
                  <td>{commitment.dueDate}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>

      <div className="workspace-card import-card">
        <h3>Transaction import and review</h3>
        <p>Paste CSV content, preview duplicates, then accept selected rows.</p>

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
                  <td>{transaction.amount.toLocaleString(undefined, { style: 'currency', currency: 'USD' })}</td>
                  <td>{transaction.account}</td>
                  <td>{transaction.duplicate ? 'Duplicate' : 'New'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </section>
  );
}

export default PlannerWorkspace;

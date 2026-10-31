/** Collect only archive availability fields from an already authorized detail response. */
export function collectArchiveIssues(data: unknown): string[] {
  const issues = new Set<string>();
  const visited = new WeakSet<object>();
  function add(value: unknown) {
    if (typeof value === 'string' && value.trim()) issues.add(value.trim());
    else if (Array.isArray(value)) value.forEach(add);
  }
  function visit(value: unknown) {
    if (!value || typeof value !== 'object' || visited.has(value)) return;
    visited.add(value);
    if (Array.isArray(value)) { value.forEach(visit); return; }
    for (const [key, child] of Object.entries(value)) {
      if (['evidenceIssue', 'evidenceErrors', 'archiveIssues'].includes(key)) add(child);
      else visit(child);
    }
  }
  visit(data);
  return [...issues];
}

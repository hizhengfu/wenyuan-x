import { describe, expect, it } from 'vitest';
import { alarmHandlingProgress } from '../alarmHandlingProgress';
const record = (action: string, occurredAt: number, id = String(occurredAt)) => ({ action, occurredAt, id });
describe('acquisition event handling progress', () => {
  it.each([
    [[], 1, 'CONFIRMED'],
    [[record('CONFIRMED', 1)], 2, 'PROCESSING'],
    [[record('PROCESSING', 1)], 3, 'PROCESSED'],
    [[record('PROCESSED', 1)], 4, 'PROCESSED'],
  ])('suggests the next handling stage from %j', (records, step, action) => {
    expect(alarmHandlingProgress(records as ReturnType<typeof record>[])).toMatchObject({ step, action });
  });
  it('ignores comments and respects time and ID order without changing the input', () => {
    const records = [record('COMMENTED', 9), record('PROCESSING', 2, 'b'), record('CONFIRMED', 2, 'a')];
    const original = [...records];
    expect(alarmHandlingProgress(records)).toMatchObject({ step: 3, action: 'PROCESSED' });
    expect(records).toEqual(original);
  });
  it('shows an explicitly restarted handling round instead of carrying an old result forward', () => {
    expect(alarmHandlingProgress([record('PROCESSED', 1), record('CONFIRMED', 2)])).toMatchObject({
      step: 2,
      action: 'PROCESSING',
    });
  });
});

it('external completion uses the processed stage and late callbacks do not restart handling', () => {
  expect(alarmHandlingProgress([
    { ...record('PROCESSED', 1), sourceCode: 'VIDEO' }, { ...record('PROCESSING', 2), applied: false },
  ])).toMatchObject({ step: 4, action: 'COMMENTED', externallyCompleted: true });
});
it('server result ignores later comments and unapplied audit records', () => {
  expect(alarmHandlingProgress([record('PROCESSING', 9)], { status: 'PROCESSED', sourceCode: 'VIDEO' }))
    .toMatchObject({ action: 'COMMENTED', externallyCompleted: true });
});
it('a running external flow cannot be completed from the local form', () => {
  expect(alarmHandlingProgress([], { status: 'PROCESSING', flowStatus: 'RUNNING' }))
    .toMatchObject({ action: 'COMMENTED', externallyCompleted: false });
});

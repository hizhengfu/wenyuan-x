import { describe, expect, it, vi } from 'vitest';

vi.mock('../../api/rfid', () => ({ rfidApi: {}, rfidRequestId: () => 'test-event-id' }));

import { sikeMatchesReaderIdentity, sikeTagReads, sikeWriteData } from '../sikeRfidWorkstation';

describe('Sike C5 and 5A WebSocket payloads', () => {
  it('writes the EPC with the demo PC word and 16-bit padding', () => {
    expect(sikeWriteData('e28068940000501a00000001')).toBe('3000E28068940000501A00000001');
    expect(sikeWriteData('123456')).toBe('100012345600');
    expect(() => sikeWriteData('12ZZ')).toThrow();
  });

  it('normalizes both single and batched EPC/TID events and rejects malformed tags', () => {
    expect(sikeTagReads({ epc: ' abcd ', tid: ' 1234 ', antId: 2, rssi: -48, readerSerialNumber: 'C5-01' })).toEqual([
      { epc: 'ABCD', tid: '1234', antenna: 2, rssi: -48, readerSerialNumber: 'C5-01' },
    ]);
    expect(sikeTagReads([{ epc: 'ABCD', antId: 1 }, { epc: 'X', tid: '11' }, null])).toEqual([
      { epc: 'ABCD', tid: undefined, antenna: 1, rssi: undefined, readerSerialNumber: undefined },
    ]);
  });

  it('requires the reader info response to match the registered physical serial', () => {
    expect(sikeMatchesReaderIdentity({ readerSerialNumber: ' c5-001 ' }, 'C5-001')).toBe(true);
    expect(sikeMatchesReaderIdentity({ readerSerialNumber: '5A-001' }, 'C5-001')).toBe(false);
    expect(sikeMatchesReaderIdentity({}, 'C5-001')).toBe(false);
  });
});

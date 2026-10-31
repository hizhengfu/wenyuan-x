export interface HighlightSegment {
  text: string;
  highlighted: boolean;
}

/** Split plain text into safe render segments. The caller renders text nodes, never HTML. */
export function splitHighlightedText(text: string, keyword: string): HighlightSegment[] {
  if (!text || !keyword.trim()) return [{ text, highlighted: false }];
  const terms = keyword
    .trim()
    .split(/\s+/)
    .filter(Boolean)
    .sort((left, right) => right.length - left.length);
  if (!terms.length) return [{ text, highlighted: false }];

  const pattern = new RegExp(`(${terms.map(escapeRegExp).join('|')})`, 'gi');
  return text
    .split(pattern)
    .filter(Boolean)
    .map((part) => ({
      text: part,
      highlighted: terms.some((term) => term.toLocaleLowerCase() === part.toLocaleLowerCase()),
    }));
}

function escapeRegExp(value: string) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

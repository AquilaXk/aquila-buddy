export type ClassValue = string | number | boolean | null | undefined;

function hasNonWhitespaceChar(value: string): boolean {
  for (let i = 0; i < value.length; i += 1) {
    if (value.charCodeAt(i) > 32) {
      return true;
    }
  }
  return false;
}

export function cn(...inputs: ClassValue[]): string {
  let result = "";
  for (let i = 0; i < inputs.length; i += 1) {
    const value = inputs[i];
    if (typeof value !== "string" || value.length === 0) {
      continue;
    }
    if (value.charCodeAt(0) <= 32 || value.charCodeAt(value.length - 1) <= 32) {
      if (!hasNonWhitespaceChar(value)) {
        continue;
      }
      const trimmed = value.trim();
      result = result.length === 0 ? trimmed : `${result} ${trimmed}`;
      continue;
    }
    result = result.length === 0 ? value : `${result} ${value}`;
  }
  return result;
}

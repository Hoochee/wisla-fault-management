const STORAGE_KEY = 'fm_source_ingest_keys';

function loadMap(): Record<string, string> {
  try {
    const raw = sessionStorage.getItem(STORAGE_KEY);
    if (!raw) return {};
    const parsed = JSON.parse(raw) as unknown;
    if (parsed && typeof parsed === 'object' && !Array.isArray(parsed)) {
      return parsed as Record<string, string>;
    }
  } catch {
    // ignore corrupt storage
  }
  return {};
}

export function getIngestApiKey(sourceId: string): string | null {
  return loadMap()[sourceId] ?? null;
}

export function setIngestApiKey(sourceId: string, key: string): void {
  const map = loadMap();
  map[sourceId] = key;
  sessionStorage.setItem(STORAGE_KEY, JSON.stringify(map));
}

export function clearIngestApiKey(sourceId: string): void {
  const map = loadMap();
  delete map[sourceId];
  sessionStorage.setItem(STORAGE_KEY, JSON.stringify(map));
}

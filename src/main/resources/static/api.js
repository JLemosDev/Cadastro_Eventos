export async function api(path, options = {}) {
  const response = await fetch(path, { cache: 'no-store', ...options,
    headers: { ...(options.body ? { 'Content-Type': 'application/json' } : {}), ...options.headers } });
  const data = response.status === 204 ? null : await response.json();
  if (!response.ok) {
    const error = new Error([data.message, ...(data.details || [])].filter(Boolean).join(' · ') || `HTTP ${response.status}`);
    error.status = response.status;
    throw error;
  }
  return data;
}

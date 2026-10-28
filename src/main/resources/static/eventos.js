import { api } from './api.js';
import { setupTerminal } from './terminal.js';

const el = id => document.getElementById(id);
const number = new Intl.NumberFormat('pt-BR');
let currentPage = 1, pages = 1, events = [], controller, requestId = 0, searchTimer;
let selectedEvent = null, people = [], deletion = null;

function errorAt(id, message) { el(id).textContent = message || ''; el(id).hidden = !message; }
function notice(message) { errorAt('notice', message); }
function busy(id, active, label) { el(id).disabled = active; el(id).textContent = active ? 'Aguarde…' : label; }
function cell(text, className = '') { const td = document.createElement('td'); td.textContent = text; td.className = className; return td; }
function action(text, handler, className = '') {
  const button = document.createElement('button'); button.type = 'button'; button.textContent = text;
  button.className = className; button.addEventListener('click', handler); return button;
}
function formatDate(value) { return value ? new Intl.DateTimeFormat('pt-BR').format(new Date(value + 'T12:00:00')) : 'Sem data'; }

function render(data) {
  events = data.items; currentPage = data.page; pages = data.pages;
  const rows = document.createDocumentFragment();
  for (const event of events) {
    const row = document.createElement('tr'), title = cell(event.titulo);
    const subtitle = document.createElement('small'); subtitle.className = 'row-detail';
    subtitle.textContent = [event.categoria, event.local].filter(Boolean).join(' · '); title.append(subtitle);
    const occupancy = cell(''), badge = document.createElement('span'); badge.className = 'badge';
    badge.textContent = `${number.format(event.inscritos)} / ${number.format(event.capacidade)}`; occupancy.append(badge);
    const actions = cell('', 'row-actions');
    actions.append(action('Editar', () => editEvent(event)), action('Pessoas', () => openPeople(event)),
      action('Excluir', () => confirmDelete(`/eventos/${event.id}`, `Excluir o evento “${event.titulo}”?`, false), 'danger'));
    row.append(cell('#' + event.id), title, cell(formatDate(event.data) + (event.horario ? ' · ' + event.horario : '')), occupancy, actions);
    rows.append(row);
  }
  if (!events.length) { const row = document.createElement('tr'), td = cell('Nenhum evento encontrado. Ajuste os filtros ou cadastre um novo evento.', 'empty'); td.colSpan = 5; row.append(td); rows.append(row); }
  el('rows').replaceChildren(rows);
  el('total').textContent = number.format(data.resumo.eventos);
  el('participants').textContent = number.format(data.resumo.capacidade);
  el('matches').textContent = number.format(data.resumo.inscritos);
  const start = (data.page - 1) * data.size;
  el('range').textContent = data.total ? `${number.format(start + 1)}–${number.format(start + events.length)} de ${number.format(data.total)} eventos` : '0 eventos';
  el('page').textContent = `${data.page} / ${number.format(data.pages)}`;
  el('previous').disabled = data.page <= 1; el('next').disabled = data.page >= data.pages;
}

async function load() {
  const id = ++requestId;
  controller?.abort();
  const pending = new AbortController(); controller = pending;
  let timedOut = false;
  const timeout = setTimeout(() => { timedOut = true; pending.abort(); }, 15000);
  busy('refresh', true, '↻ Atualizar'); el('panel').setAttribute('aria-busy', 'true'); errorAt('error', '');
  const params = new URLSearchParams({ page: currentPage, size: el('size').value, sort: el('sort').value,
    titulo: el('search').value.trim(), categoria: el('category').value.trim(), periodo: el('period').value });
  try {
    const data = await api('/eventos?' + params, { signal: pending.signal });
    if (id !== requestId) return;
    render(data); el('updated').textContent = 'Atualizado às ' + new Date().toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' });
  } catch (error) {
    if (id === requestId && (timedOut || error.name !== 'AbortError')) errorAt('error', timedOut ? 'A consulta demorou demais. Clique em Atualizar para tentar novamente.' : 'Não foi possível carregar os eventos. ' + error.message);
  } finally {
    clearTimeout(timeout);
    if (id === requestId) { busy('refresh', false, '↻ Atualizar'); el('panel').setAttribute('aria-busy', 'false'); }
  }
}

function editEvent(event = null) {
  el('event-form').reset(); el('event-id').value = event?.id || '';
  const fields = { title: 'titulo', capacity: 'capacidade', date: 'data', time: 'horario', location: 'local', category: 'categoria', description: 'descricao' };
  for (const [id, key] of Object.entries(fields)) el('event-' + id).value = event?.[key] ?? '';
  el('event-dialog-title').textContent = event ? 'Editar evento' : 'Novo evento';
  errorAt('event-error', ''); el('event-dialog').showModal();
}

el('event-form').addEventListener('submit', async event => {
  event.preventDefault(); if (el('event-save').disabled) return;
  const id = el('event-id').value;
  const body = { titulo: el('event-title').value, capacidade: Number(el('event-capacity').value), data: el('event-date').value,
    horario: el('event-time').value, local: el('event-location').value, categoria: el('event-category').value, descricao: el('event-description').value };
  busy('event-save', true, 'Salvar evento'); errorAt('event-error', '');
  try {
    await api('/eventos' + (id ? '/' + id : ''), { method: id ? 'PUT' : 'POST', body: JSON.stringify(body) });
    el('event-dialog').close(); notice(id ? 'Evento atualizado.' : 'Evento cadastrado.'); await load();
  } catch (error) { errorAt('event-error', error.message); }
  finally { busy('event-save', false, 'Salvar evento'); }
});

function resetPerson() {
  el('person-form').reset(); el('person-id').value = ''; el('person-event').value = selectedEvent?.id || '';
  el('person-save').textContent = 'Inscrever pessoa'; errorAt('people-error', '');
}
async function refreshPeople() {
  if (!selectedEvent) return;
  const id = selectedEvent.id;
  const [event, list] = await Promise.all([api('/eventos/' + id), api('/pessoas?eventoId=' + id)]);
  if (selectedEvent?.id !== id) return;
  selectedEvent = event; people = list;
  el('people-title').textContent = 'Inscrições · ' + event.titulo;
  el('people-summary').textContent = `${event.inscritos} inscritos de ${event.capacidade} vagas · ${event.capacidade - event.inscritos} disponíveis`;
  const rows = document.createDocumentFragment();
  for (const person of people) {
    const row = document.createElement('tr'), actions = cell('', 'row-actions');
    actions.append(action('Editar', () => {
      el('person-id').value = person.id; el('person-name').value = person.nome; el('person-email').value = person.email;
      el('person-event').value = person.eventoId; el('person-save').textContent = 'Salvar pessoa'; el('person-name').focus(); errorAt('people-error', '');
    }), action('Excluir', () => confirmDelete('/pessoas/' + person.id, `Excluir a inscrição de “${person.nome}”?`, true), 'danger'));
    row.append(cell(person.nome), cell(person.email), actions); rows.append(row);
  }
  if (!people.length) { const row = document.createElement('tr'), td = cell('Nenhuma pessoa inscrita neste evento.', 'empty'); td.colSpan = 3; row.append(td); rows.append(row); }
  el('people-rows').replaceChildren(rows);
}
async function openPeople(event) {
  selectedEvent = event; resetPerson(); el('people-dialog').showModal();
  el('people-title').textContent = 'Inscrições · ' + event.titulo; el('people-summary').textContent = 'Carregando…'; el('people-rows').replaceChildren();
  try { await refreshPeople(); } catch (error) { errorAt('people-error', error.message); }
}
el('person-form').addEventListener('submit', async event => {
  event.preventDefault(); if (el('person-save').disabled) return;
  const id = el('person-id').value;
  const body = { nome: el('person-name').value, email: el('person-email').value, eventoId: Number(el('person-event').value) };
  busy('person-save', true, id ? 'Salvar pessoa' : 'Inscrever pessoa'); errorAt('people-error', '');
  try {
    await api('/pessoas' + (id ? '/' + id : ''), { method: id ? 'PUT' : 'POST', body: JSON.stringify(body) });
    resetPerson(); await Promise.all([refreshPeople(), load()]); notice(id ? 'Pessoa atualizada.' : 'Pessoa inscrita.');
  } catch (error) { errorAt('people-error', error.message); }
  finally { busy('person-save', false, el('person-id').value ? 'Salvar pessoa' : 'Inscrever pessoa'); }
});

function confirmDelete(path, message, person) {
  deletion = { path, person }; el('delete-message').textContent = message; errorAt('delete-error', ''); el('delete-dialog').showModal();
}
el('delete-form').addEventListener('submit', async event => {
  event.preventDefault(); if (!deletion || el('delete-confirm').disabled) return;
  busy('delete-confirm', true, 'Excluir'); errorAt('delete-error', '');
  try {
    await api(deletion.path, { method: 'DELETE' }); el('delete-dialog').close();
    if (deletion.person) { resetPerson(); await refreshPeople(); }
    notice('Registro excluído.'); await load();
  } catch (error) { errorAt('delete-error', error.message); }
  finally { busy('delete-confirm', false, 'Excluir'); }
});

el('new-event').addEventListener('click', () => editEvent());
el('person-cancel').addEventListener('click', resetPerson);
document.querySelectorAll('[data-close]').forEach(button => button.addEventListener('click', () => el(button.dataset.close).close()));
for (const id of ['search', 'category']) el(id).addEventListener('input', () => { clearTimeout(searchTimer); searchTimer = setTimeout(() => { currentPage = 1; load(); }, 250); });
for (const id of ['sort', 'size', 'period']) el(id).addEventListener('change', () => { currentPage = 1; load(); });
el('previous').addEventListener('click', () => { currentPage = Math.max(1, currentPage - 1); load(); });
el('next').addEventListener('click', () => { currentPage = Math.min(pages, currentPage + 1); load(); });
el('refresh').addEventListener('click', load);
setupTerminal(async () => { await load(); if (el('people-dialog').open) { try { await refreshPeople(); } catch (error) { errorAt('people-error', error.message); } } });
load();

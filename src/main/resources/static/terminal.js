export function setupTerminal(refresh) {
 const el = id => document.getElementById(id);
 const number = new Intl.NumberFormat('pt-BR');
 const load = refresh;
      const output = el('terminal-output'), input = el('terminal-command');
      const history = [];
      let historyPosition = 0, draft = '', running = false;
      const help = `Comandos disponíveis:
GET /eventos
GET /eventos?page=2&size=25
GET /eventos?titulo=teatro
GET /eventos/1
POST /eventos {"titulo":"Sarau","capacidade":100}
PUT /eventos/1 {"titulo":"Sarau atualizado","capacidade":200}
DELETE /eventos/1

GET /pessoas
GET /pessoas?nome=Ana
GET /pessoas/1
POST /pessoas {"nome":"Ana","email":"ana@example.com","eventoId":1}
PUT /pessoas/1 {"nome":"Ana Maria","email":"ana@example.com","eventoId":1}
DELETE /pessoas/1

GET /health
ajuda — mostra estes exemplos
limpar — limpa a saída

POST cadastra, PUT atualiza e DELETE exclui de verdade.
Use o ID retornado no cadastro nas próximas operações.
Listagens mostram os primeiros 20 registros no terminal; use a busca ou consulte pelo ID.`;

      function print(text, style = '') {
        const entry = document.createElement('pre');
        entry.className = 'terminal-entry ' + style; entry.textContent = text;
        output.append(entry);
        while (output.children.length > 60) output.firstElementChild.remove();
        output.scrollTop = output.scrollHeight;
      }

      function parse(command) {
        const match = command.match(/^(GET|POST|PUT|DELETE)\s+(\/\S*)(?:\s+([\s\S]+))?$/i);
        if (!match) throw new Error('Use MÉTODO /rota e, para POST ou PUT, um objeto JSON. Digite ajuda para ver exemplos.');
        const method = match[1].toUpperCase(), path = match[2], raw = match[3];
        const url = new URL(path, window.location.origin);
        if (url.origin !== window.location.origin || url.hash || !/^\/(eventos|pessoas)(\/\d+)?$/.test(url.pathname) && url.pathname !== '/health' && url.pathname !== '/') {
          throw new Error('Use uma rota desta API: /eventos, /pessoas, /health ou /.');
        }
        const collection = /^\/(eventos|pessoas)$/.test(url.pathname);
        const item = /^\/(eventos|pessoas)\/\d+$/.test(url.pathname);
        if (method === 'POST' && !collection || ['PUT', 'DELETE'].includes(method) && !item) {
          throw new Error('POST usa /eventos ou /pessoas. PUT e DELETE precisam do ID: /eventos/1 ou /pessoas/1.');
        }
        if (method !== 'GET' && url.search) throw new Error('Use filtros na URL apenas com GET.');
        const options = { method, cache: 'no-store' };
        if (['POST', 'PUT'].includes(method)) {
          let data;
          try { data = JSON.parse(raw); } catch (_) { throw new Error('JSON inválido. Use aspas duplas nos campos e textos.'); }
          if (!data || Array.isArray(data) || typeof data !== 'object') throw new Error('O corpo deve ser um objeto JSON.');
          options.headers = { 'Content-Type': 'application/json' }; options.body = JSON.stringify(data);
        } else if (raw) throw new Error('GET e DELETE não recebem corpo JSON.');
        return { url, options };
      }

      async function execute(command) {
        if (running) return;
        print('› ' + command);
        if (['ajuda', 'help'].includes(command.toLowerCase())) { print(help); return; }
        if (['limpar', 'clear'].includes(command.toLowerCase())) { output.replaceChildren(); return; }
        let request;
        try { request = parse(command); } catch (error) { print(error.message, 'error-output'); return; }
        running = true; el('terminal-run').disabled = true; el('terminal-run').textContent = 'Enviando…';
        const controller = new AbortController(), timeout = setTimeout(() => controller.abort(), 20000);
        const start = performance.now();
        try {
          const response = await fetch(request.url.href, { ...request.options, signal: controller.signal });
          const text = await response.text();
          let data = text, note = '';
          if (text) {
            try { data = JSON.parse(text); } catch (_) { }
            if (data && Array.isArray(data.items)) {
 note = '\nPágina ' + data.page + ' de ' + data.pages + '; total: ' + number.format(data.total) + '. Use page e size para navegar.';
 if (data.items.length > 20) { data = {...data, items: data.items.slice(0,20)}; note += '\nPrévia dos primeiros 20 itens da página.'; }
 }
 if (Array.isArray(data) && data.length > 20) {
              note = `\nExibindo 20 de ${number.format(data.length)} registros. Use um filtro ou consulte pelo ID.`;
              data = data.slice(0, 20);
            }
          }
          const body = response.status === 204 ? '(sem conteúdo)' : typeof data === 'string' ? data : JSON.stringify(data, null, 2);
          print(`HTTP ${response.status} ${response.statusText} · ${Math.round(performance.now() - start)} ms\n${body}${note}`,
            response.ok ? 'success-output' : 'error-output');
          if (response.ok && request.options.method !== 'GET' && /^\/(eventos|pessoas)/.test(request.url.pathname)) await load();
        } catch (error) {
          print(error.name === 'AbortError'
            ? 'Tempo de espera excedido. Consulte os registros antes de repetir uma alteração: ela pode ter sido aplicada.'
            : 'Falha de conexão. Verifique se a API está ativa. Antes de repetir uma alteração, consulte os registros.', 'error-output');
        } finally {
          clearTimeout(timeout); running = false; el('terminal-run').disabled = false; el('terminal-run').textContent = 'Executar';
        }
      }

      el('terminal-form').addEventListener('submit', async event => {
        event.preventDefault();
        const command = input.value.trim();
        if (!command || running) return;
        history.push(command); historyPosition = history.length; draft = ''; input.value = '';
        await execute(command);
      });
      input.addEventListener('keydown', event => {
        if (!['ArrowUp', 'ArrowDown'].includes(event.key) || !history.length) return;
        event.preventDefault();
        if (historyPosition === history.length) draft = input.value;
        historyPosition = Math.max(0, Math.min(history.length, historyPosition + (event.key === 'ArrowUp' ? -1 : 1)));
        input.value = historyPosition === history.length ? draft : history[historyPosition];
      });
      document.querySelectorAll('[data-command]').forEach(button => button.addEventListener('click', () => {
        input.value = button.dataset.command; input.focus();
      }));
      el('terminal-clear').addEventListener('click', () => output.replaceChildren());
      print('Terminal pronto. Digite ajuda para ver os comandos ou escolha um exemplo acima.');
    }

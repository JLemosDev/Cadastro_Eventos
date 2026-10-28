const { test, expect } = require('playwright/test');

test('cadastro, edição, inscrições, capacidade e exclusão pelos formulários', async ({ page }) => {
  await page.goto('/painel');
  await page.getByRole('button', { name: '+ Novo evento' }).click();
  const editor = page.getByRole('dialog', { name: 'Novo evento', exact: true });
  await editor.getByLabel('Título *', { exact: true }).fill('Sarau do navegador');
  await editor.getByLabel('Capacidade *', { exact: true }).fill('1');
  await editor.getByLabel('Categoria', { exact: true }).fill('Literatura');
  await editor.getByLabel('Data', { exact: true }).fill('2027-06-01');
  await editor.getByLabel('Horário', { exact: true }).fill('19:30');
  await editor.getByLabel('Local', { exact: true }).fill('Auditório');
  await editor.getByLabel('Descrição', { exact: true }).fill('Evento criado pelo teste de interface.');
  await editor.getByRole('button', { name: 'Salvar evento', exact: true }).click();
  await expect(editor).not.toBeVisible();
  let row = page.locator('#rows tr').filter({ hasText: 'Sarau do navegador' });
  await expect(row).toContainText('01/06/2027');
  await row.getByRole('button', { name: 'Editar', exact: true }).click();
  await page.getByLabel('Título *', { exact: true }).fill('Sarau editado');
  await page.getByRole('button', { name: 'Salvar evento', exact: true }).click();
  row = page.locator('#rows tr').filter({ hasText: 'Sarau editado' });
  await expect(row).toBeVisible();
  await row.getByRole('button', { name: 'Pessoas', exact: true }).click();
  const people = page.locator('#people-dialog');
  await people.getByLabel('Nome *', { exact: true }).fill('Ana');
  await people.getByLabel('E-mail *', { exact: true }).fill('ana@example.com');
  await people.getByRole('button', { name: 'Inscrever pessoa' }).click();
  await expect(people.locator('#people-rows')).toContainText('Ana');
  await people.getByLabel('Nome *', { exact: true }).fill('Bia');
  await people.getByLabel('E-mail *', { exact: true }).fill('bia@example.com');
  await people.getByRole('button', { name: 'Inscrever pessoa' }).click();
  await expect(people.locator('#people-error')).toContainText('capacidade máxima');
  await people.locator('#people-rows').getByRole('button', { name: 'Editar', exact: true }).click();
  await people.getByLabel('Nome *', { exact: true }).fill('Ana Maria');
  await people.getByRole('button', { name: 'Salvar pessoa' }).click();
  await expect(people.locator('#people-rows')).toContainText('Ana Maria');
  await people.getByRole('button', { name: 'Fechar inscrições' }).click();
  await row.getByRole('button', { name: 'Excluir', exact: true }).click();
  await page.locator('#delete-dialog').getByRole('button', { name: 'Excluir', exact: true }).click();
  await expect(page.locator('#delete-error')).toContainText('vinculadas');
  await page.locator('#delete-dialog').getByRole('button', { name: 'Cancelar' }).click();
  await row.getByRole('button', { name: 'Pessoas', exact: true }).click();
  await people.locator('#people-rows').getByRole('button', { name: 'Excluir', exact: true }).click();
  await page.locator('#delete-dialog').getByRole('button', { name: 'Excluir', exact: true }).click();
  await expect(people.locator('#people-rows')).toContainText('Nenhuma pessoa');
  await people.getByRole('button', { name: 'Fechar inscrições' }).click();
  await row.getByRole('button', { name: 'Excluir', exact: true }).click();
  await page.locator('#delete-dialog').getByRole('button', { name: 'Excluir', exact: true }).click();
  await expect(row).toHaveCount(0);
});

test('paginação, busca sem acentos, filtros e ordenação no servidor', async ({ page, request }) => {
  for (let i=0; i<28; i++) {
    const response = await request.post('/eventos', { data: { titulo: `Música página ${String(i).padStart(2,'0')}`, capacidade: 10,
      data: '2099-01-01', categoria: 'Arte' } });
    expect(response.status()).toBe(201);
  }
  await page.goto('/painel');
  await page.getByLabel('Buscar evento', { exact: true }).fill('musica pagina');
  await expect(page.locator('#range')).toContainText('de 28 eventos');
  await expect(page.locator('#rows tr')).toHaveCount(25);
  await page.getByRole('button', { name: 'Próxima página', exact: true }).click();
  await expect(page.locator('#rows tr')).toHaveCount(3);
  await page.getByLabel('Por página').selectOption('50');
  await expect(page.locator('#rows tr')).toHaveCount(28);
  await page.getByLabel('Ordenar por').selectOption('newest');
  await expect(page.locator('#rows tr').first()).toContainText('Música página 27');
  await page.locator('#category').fill('Arte');
  await page.getByLabel('Período', { exact: true }).selectOption('passados');
  await expect(page.locator('#range')).toHaveText('0 eventos');
  await page.getByLabel('Período', { exact: true }).selectOption('futuros');
  await expect(page.locator('#range')).toContainText('de 28 eventos');
  await page.getByLabel('Buscar evento', { exact: true }).fill('inexistente');
  await expect(page.locator('#rows')).toContainText('Nenhum evento encontrado');
});

test('preferência de tema persiste e terminal executa CRUD com erros', async ({ page }) => {
  await page.goto('/painel');
  await expect(page.locator('html')).toHaveAttribute('data-theme','dark');
  await page.getByRole('button', { name: 'Ativar tema claro' }).click();
  await page.reload();
  await expect(page.locator('html')).toHaveAttribute('data-theme','light');
  await page.getByRole('button', { name: 'Ativar tema escuro' }).click();
  const command = page.getByRole('textbox', { name: 'Comando da API', exact: true });
  async function run(text) { await command.fill(text); await page.getByRole('button', {name:'Executar',exact:true}).click(); await expect(page.getByRole('button',{name:'Executar',exact:true})).toBeEnabled(); }
  await run('POST /eventos {"titulo":"Terminal navegador","capacidade":1}');
  await expect(page.locator('#terminal-output')).toContainText('HTTP 201');
  const result = await page.locator('#terminal-output .terminal-entry').last().textContent();
  const id = JSON.parse(result.slice(result.indexOf('\n')+1)).id;
  await run(`PUT /eventos/${id} {"titulo":"Terminal alterado","capacidade":2}`);
  await expect(page.locator('#terminal-output .terminal-entry').last()).toContainText('HTTP 200');
  await run('POST /eventos {"titulo":"Inválido","capacidade":5001}');
  await expect(page.locator('#terminal-output .terminal-entry').last()).toContainText('HTTP 422');
  await run(`DELETE /eventos/${id}`);
  await expect(page.locator('#terminal-output .terminal-entry').last()).toContainText('HTTP 204');
  await command.press('ArrowUp'); await expect(command).toHaveValue(`DELETE /eventos/${id}`);
});

test('layout utilizável em tela pequena', async ({ page }) => {
  await page.setViewportSize({ width:390, height:844 });
  await page.goto('/painel');
  await expect(page.getByRole('button', { name:'+ Novo evento' })).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBeTruthy();
  await page.getByRole('button', { name:'+ Novo evento' }).click();
  await expect(page.getByRole('button', { name:'Salvar evento', exact:true })).toBeVisible();
});

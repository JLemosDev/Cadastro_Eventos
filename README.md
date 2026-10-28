# Cadastro de Eventos — Centro Cultural

API REST em Java 21 com painel de gerenciamento, terminal HTTP e banco SQLite local. Usa o framework didático mini-spring, com controllers, services e repositories separados e injeção por construtor.

## Executar

Requisitos: Java 21 e Maven 3.9+. O banco é criado automaticamente; não é necessário instalar um servidor de banco de dados.

```powershell
mvn test
mvn package
java -jar target/cadastro-eventos-1.0.0.jar
```

Abra o [painel](http://localhost:8080/painel). A API aceita conexões locais em 127.0.0.1:8080. Configure a porta, o nome da aplicação e a pasta de dados em [application.properties](src/main/resources/application.properties).

Para usar outro local de armazenamento ou porta:

```powershell
java "-Ddata.directory=C:/dados/centro-cultural" "-Dserver.port=8081" -jar target/cadastro-eventos-1.0.0.jar
```

## Painel

- Busca por título ou ID, sem diferenciar acentos ou maiúsculas.
- Paginação na API: 25, 50 ou 100 eventos por página.
- Ordenação por ID, título, capacidade, inscritos ou data.
- Filtros por categoria e período: futuros (inclui hoje), passados ou sem data. O período usa a data de São Paulo.
- Novo evento, edição e exclusão com confirmação.
- Botão **Pessoas** em cada evento: consultar inscrições, inscrever, editar, transferir ou excluir pessoas. Para transferir, informe o ID do evento de destino no formulário.
- Totais globais de eventos, capacidade e inscrições; a contagem dos resultados filtrados aparece abaixo da tabela.
- Tema escuro por padrão e opção de tema claro, com preferência salva no navegador quando disponível.

A paleta usa azul meia-noite **#1A2930**, azul profundo **#1B263B** e laranja vibrante **#F77F00**. O tema claro tem superfícies brancas e fundo **#F8F9FA**.

Os arquivos da interface ficam em [src/main/resources/static](src/main/resources/static): HTML, CSS, tema, cliente HTTP, painel e terminal em arquivos separados, sem etapa de compilação de frontend.

## Eventos e inscrições

| Campo de evento | Regra |
|---|---|
| id | Gerado pelo banco e preservado nas atualizações |
| titulo | Obrigatório, até 200 caracteres; espaços das extremidades são removidos |
| capacidade | Inteiro de 1 a 5.000 |
| inscritos | Calculado a partir das pessoas vinculadas; não deve ser enviado no cadastro |
| data | Opcional, formato AAAA-MM-DD; deve ser uma data válida |
| horario | Opcional, formato HH:mm |
| local | Opcional, até 200 caracteres |
| categoria | Opcional, até 80 caracteres |
| descricao | Opcional, até 5.000 caracteres |

Os registros antigos continuam sem data, horário, local, categoria ou descrição até serem editados. A capacidade inicial corresponde ao antigo campo participantes.

Uma pessoa tem nome obrigatório (até 200 caracteres), e-mail válido (até 254 caracteres) e eventoId de um evento existente. O número de inscritos aumenta ou diminui automaticamente conforme as inscrições.

Regras de consistência, também garantidas pelo banco:

- Não é possível excluir um evento com pessoas vinculadas: **409 Conflict**. Transfira ou exclua as inscrições primeiro.
- Novas inscrições ou transferências para eventos lotados retornam **409**.
- A capacidade não pode ser reduzida abaixo do número de inscritos: **409**.
- Uma pessoa não pode apontar para um evento inexistente: **422**.
- Validações rejeitadas não alteram os dados. Os testes cobrem tentativas concorrentes de exceder a capacidade.

## API

| Método | Rota | Resultado |
|---|---|---|
| GET | /eventos | Página de eventos, metadados e resumo global |
| GET | /eventos/{id} | Evento com capacidade, inscritos e detalhes |
| POST | /eventos | Cadastra; 201 com Location |
| PUT | /eventos/{id} | Substitui os campos editáveis; 200 |
| DELETE | /eventos/{id} | Exclui se não houver inscrições; 204 |
| GET | /pessoas | Lista; aceita nome e eventoId como filtros |
| GET | /pessoas/{id} | Consulta por ID |
| POST | /pessoas | Inscreve; 201 com Location |
| PUT | /pessoas/{id} | Atualiza ou transfere; 200 |
| DELETE | /pessoas/{id} | Exclui a inscrição; 204 |
| GET | /painel | Interface HTML |
| GET | /health | Saúde da aplicação |
| GET | / | Informações da aplicação |

Exemplo de cadastro:

```json
{
  "titulo": "Festival de Música",
  "capacidade": 300,
  "data": "2027-06-01",
  "horario": "19:30",
  "local": "Auditório",
  "categoria": "Música",
  "descricao": "Programação cultural aberta ao público."
}
```

PUT recebe os mesmos campos de POST; detalhes opcionais omitidos são limpos. Como compatibilidade, a entrada ainda aceita participantes como nome antigo de capacidade e a resposta mantém esse alias. Prefira capacidade e não envie os dois nomes juntos.

### Paginação e filtros

**Mudança de contrato:** GET /eventos retorna um objeto paginado, em vez do array antigo. Clientes devem ler items e total.

```text
GET /eventos?page=1&size=25&sort=data&titulo=musica&categoria=Arte&periodo=futuros
```

| Parâmetro | Valores |
|---|---|
| page | Inteiro a partir de 1; padrão 1. Uma página além do fim é ajustada à última |
| size | De 1 a 100; padrão 25 |
| sort | id, newest, title, capacidade, inscritos, data; padrão id. participants é um alias de capacidade |
| titulo | Parte do título ou ID exato |
| categoria | Categoria exata, ignorando acentos e maiúsculas |
| periodo | Vazio, futuros, passados ou sem-data |

Exemplo de resposta para uma página com um evento:

```json
{
  "items": [{"id": 1, "titulo": "Sarau", "capacidade": 100, "inscritos": 2, "data": "", "horario": "", "local": "", "categoria": "", "descricao": "", "participantes": 100}],
  "total": 1,
  "page": 1,
  "size": 25,
  "pages": 1,
  "resumo": {"eventos": 1, "capacidade": 100, "inscritos": 2}
}
```

Erros: **400** para campos ou parâmetros inválidos, **404** para registros inexistentes, **409** para conflitos de inscrições e **422** para capacidade acima de 5.000 ou vínculo inválido. O corpo inclui mensagem, detalhes e requestId. Exemplos completos: [requests.http](requests.http) e [requests-pessoas.http](requests-pessoas.http).

## Terminal da API

O terminal do painel executa requisições HTTP às rotas da própria aplicação. Digite ajuda para exemplos, limpar para limpar a saída e use ↑/↓ para recuperar o histórico da sessão. Os botões de exemplo apenas preenchem o comando; **Executar** ou **Enter** envia a requisição.

```text
GET /eventos?page=2&size=25
GET /eventos/1
POST /eventos {"titulo":"Sarau","capacidade":100}
PUT /eventos/1 {"titulo":"Sarau atualizado","capacidade":200}
GET /pessoas?eventoId=1
POST /pessoas {"nome":"Ana","email":"ana@example.com","eventoId":1}
DELETE /pessoas/1
GET /health
```

Use os IDs retornados pela API. As alterações são reais e persistidas. A saída mostra status HTTP, duração, resposta e erros. Listagens mostram uma prévia de até 20 itens; page e size permitem navegar nos eventos. Operações bem-sucedidas atualizam o painel.

## Banco e migração dos dados

O arquivo principal é **data/cadastro-eventos.db**, relativo ao diretório de execução. O SQLite atualiza os registros em transações e mantém os vínculos entre pessoas e eventos. A implementação usa [SQLite JDBC](https://github.com/xerial/sqlite-jdbc) e ativa as [restrições de chave estrangeira](https://www.sqlite.org/foreignkeys.html) em cada conexão.

Na primeira execução, eventos.json e pessoas.json, quando presentes na pasta configurada, são importados numa única transação. IDs e sequências são preservados. A migração é marcada no banco e não se repete ao reiniciar, mesmo se registros importados forem excluídos. Os JSONs originais continuam no disco, mas deixam de receber atualizações. Arquivos inválidos ou vínculos incompatíveis interrompem a migração sem apagar os originais; corrija os dados e reinicie.

Os **5.000 eventos fictícios locais** são preservados pela migração. Banco, JSONs e backups ficam fora do Git: uma cópia limpa do repositório começa sem cadastros.

Para backup manual, **pare a aplicação e copie a pasta de dados inteira**. Restaure-a com a aplicação parada. Não copie somente o JSON antigo esperando recuperar alterações feitas depois da migração. Para reverter a implantação, mantenha também a versão anterior do JAR junto com os JSONs originais.

## Testes

### Java

```powershell
mvn test
```

- ApiIntegrationTest: CRUD HTTP, contrato paginado, filtros, validação dos novos campos, vínculos, capacidade, entrega do HTML/CSS/JavaScript e erros.
- DatabaseTest: migração e rollback, recuperação dos dados, sequências, restrições, concorrência de inscrições e paginação SQL.
- EventoServiceTest e PessoaServiceTest: regras de negócio isoladas, usando repositórios em memória que ficam apenas em src/test.
- RouterTest, ApplicationContextTest e UnexpectedErrorIntegrationTest: framework.

Os testes usam bases temporárias e não alteram a pasta data.

### Navegador

Requer Node.js, npm e Google Chrome instalado. Os testes usam [Playwright](https://playwright.dev/docs/test-configuration), iniciam uma API separada na porta 8082 e usam uma pasta de dados nova em target/ui-tests a cada execução. A porta 8082 deve estar livre.

```powershell
mvn package
npm install
npm run test:ui
```

Para usar Chromium instalado pelo Playwright em vez do Chrome:

```powershell
npx playwright install chromium
$env:PLAYWRIGHT_CHANNEL = "chromium"
npm run test:ui
```

A suíte cobre formulários de eventos e pessoas, lotação, exclusão bloqueada, busca sem acentos, paginação, filtros, ordenação, persistência do tema, terminal e layout em tela pequena. Relatório em target/browser-report/index.html; rastros e capturas de falhas em target/browser-results.

## Arquitetura

```text
Navegador / cliente HTTP
  → DispatcherServlet → Router → Controllers
  → Services (validação e regras)
  → SqliteEventoRepository / SqlitePessoaRepository
  → Database (transações, migração e restrições)
  → SQLite: data/cadastro-eventos.db
```

O servidor entrega a interface e os assets pelo InfoController. SQL usa parâmetros para os valores fornecidos pelo cliente; ordenações e arquivos estáticos têm listas explícitas de opções permitidas.

## Integrantes

| Nome | Responsabilidade |
|---|---|
| _Nome 1_ | _preencher_ |
| _Nome 2_ | _preencher_ |
| _Nome 3_ | _preencher_ |

## Uso de IA

A versão inicial documentava apoio de Claude para revisão e testes. As alterações de persistência, painel, terminal, migração SQLite, formulários e testes de navegador receberam apoio do Codex. A equipe deve revisar e validar a entrega antes de apresentá-la.

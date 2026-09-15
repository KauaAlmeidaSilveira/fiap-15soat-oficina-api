# Modelo Relacional — Explicação dos Relacionamentos

> Complementa o diagrama ER (`docs/diagrama-er.png`, gerado a partir de
> `docs/diagrama-er.mmd` via Mermaid) — atende ao requisito do desafio de "ajustes no
> modelo relacional, com diagramas ER e explicação dos relacionamentos"
> (`docs/requisitos-fase3.md`). Cada relacionamento abaixo é descrito a partir do código
> real (`dataprovider/persistence/entity/`), não de um desenho à parte — schema e texto
> nascem da mesma fonte. O `.mmd` segue o mesmo princípio: é texto, não um desenho
> pintado à mão, então para atualizar o diagrama depois de mudar uma entidade basta
> editar o `.mmd` e re-renderizar (`npx @mermaid-js/mermaid-cli -i docs/diagrama-er.mmd
> -o docs/diagrama-er.png -b white -w 2200`), sem risco de erro de digitação na imagem.

## Visão geral

O schema tem 10 tabelas, divididas em dois grupos:

- **Domínio da oficina**: `cliente`, `veiculo`, `cliente_veiculo`, `ordem_servico`,
  `os_item`, `produto`, `saldo_estoque`, `movimentacao_estoque`.
- **Autenticação**: `users`, `roles`, `users_roles`.

Os dois grupos se tocam num único ponto: `ordem_servico.user_id` aponta para `users`,
registrando qual usuário logado processou aquela OS.

## Ajuste recente ao modelo

`cliente` ganhou a coluna `status` (`ATIVO`/`INATIVO`) — mesclado na `master` via PR #2
(`feat/status-cliente`). É o único ajuste estrutural desta rodada; o resto do modelo
vem da Fase 2. Ver [RFC-0002](rfc/0002-escolha-do-banco-de-dados-rds-postgresql.md)
para a motivação de "consistência e performance" do banco como um todo.

## Relacionamentos

### `cliente` — N:N — `veiculo` (via `cliente_veiculo`)

Um cliente pode ter vários veículos e um veículo pode ter mais de um proprietário ao
longo do tempo (ex.: venda de um carro entre dois clientes cadastrados, ou um veículo de
frota vinculado a mais de um responsável) — por isso a relação não é 1:N direta, e sim
resolvida por `cliente_veiculo`, uma tabela associativa com sua própria chave primária e
um `vinculado_em` (quando o vínculo foi criado). A constraint
`UNIQUE(cliente_id, veiculo_id)` impede vincular o mesmo par duas vezes. O cascade é
`ALL` a partir de `cliente` com `orphanRemoval`: excluir um cliente remove seus vínculos
(não os veículos em si, que continuam existindo e podem ter outro proprietário).

### `cliente` — 1:N — `ordem_servico`

Toda OS pertence a exatamente um cliente (`cliente_id` não-nulo). É a FK que a Function
Serverless de autenticação (Seção 1 do `CHECKLIST.md`) vai precisar respeitar: hoje
`OrdemServicoUseCase.criar()` já rejeita a criação se `cliente.status == INATIVO`,
lançando `RegraDeNegocioException` — a regra de negócio existe no domínio, não depende
da Lambda para ser aplicada.

### `veiculo` — 1:N — `ordem_servico`

Toda OS também referencia exatamente um veículo (`veiculo_id` não-nulo) — é o carro que
efetivamente entrou na oficina. Diferente do vínculo cliente↔veículo (que é sobre posse),
este é sobre "qual veículo está sendo atendido nesta ordem".

### `users` — 1:N — `ordem_servico` (opcional)

`user_id` é a única FK nula por padrão em `ordem_servico` (`@ManyToOne` sem
`optional = false`). Registra qual usuário autenticado (tipicamente um `OPERADOR`)
processou a OS, mas o campo pode ficar em branco — o modelo não força que toda OS já
nasça atribuída a alguém.

### `ordem_servico` — 1:N — `os_item`

Cada OS tem uma lista de itens (peças ou serviços cobrados). `os_item` guarda o
`preco_unitario` e a `quantidade` no momento da adição — não o preço atual do produto —
porque o valor de uma OS não pode mudar retroativamente se o preço do catálogo mudar
depois. Cascade `ALL` + `orphanRemoval`: apagar a OS apaga seus itens; remover um item da
lista em memória remove a linha do banco.

### `produto` — 1:N — `os_item`

Cada item de OS referencia o produto (peça ou serviço) que originou aquela linha —
`produto_id` não-nulo, sem cascade (excluir um produto não é permitido enquanto ele
estiver referenciado por algum item de OS; a "exclusão" de produto no sistema é sempre
soft delete via `Produto.ativo`, nunca `DELETE` físico).

### `produto` — 1:1 — `saldo_estoque` (opcional)

Só produtos do tipo `PECA` têm saldo de estoque — um `SERVICO` nunca ganha uma linha em
`saldo_estoque` (a aplicação simplesmente não cria uma ao registrar o produto). É 1:1
porque cada peça tem exatamente um saldo corrente; o histórico de como esse saldo mudou
fica em `movimentacao_estoque`, não aqui. `produto_id` é `UNIQUE` para garantir que nunca
existam dois saldos para a mesma peça.

### `produto` — 1:N — `movimentacao_estoque`

Toda entrada ou saída de estoque (`tipo`: `ENTRADA`/`SAIDA`) referencia o produto
afetado. É o histórico completo — `saldo_estoque` é a foto atual (resultado agregado),
`movimentacao_estoque` é o extrato.

### `ordem_servico` — 1:N — `movimentacao_estoque` (opcional)

Uma movimentação de estoque pode opcionalmente estar amarrada à OS que a originou
(`ordem_servico_id` nulo permitido). Isso cobre dois casos reais do sistema: saída
automática de peças ao aprovar uma OS (`ordemServicoId` preenchido, rastreável) e ajuste
manual de estoque por perda/avaria/reposição, sem relação com nenhuma OS
(`ordemServicoId` nulo).

### `users` — N:N — `roles` (via `users_roles`)

Um usuário pode ter mais de uma role e uma role é compartilhada por vários usuários —
modelo clássico de RBAC. `users_roles` é uma tabela puramente associativa (sem entidade
JPA própria, só `@JoinTable`), sem colunas além das duas FKs. As roles reais são fixas
via `Role.Values` (`ADMIN=1`, `OPERADOR=2`, `RECEPCAO=3`), semeadas pelo `DataLoader` —
não existe fluxo de criar uma role nova pela API.

## O que fica de fora deste documento

O ajuste que a Fase 3 ainda vai exigir — o campo que a Lambda vai gravar/ler para
correlacionar a autenticação por CPF a um cliente específico, além do `status` que já
existe — depende da decisão pendente do RFC de autenticação (ver `CHECKLIST.md`,
Seção 1). Este documento cobre o modelo como ele está hoje, já com o `status`
implementado; será revisado de novo quando aquela decisão fechar.

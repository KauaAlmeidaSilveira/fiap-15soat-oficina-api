# Plano — Notificação por E-mail e Aprovação/Recusa de Orçamento

## Contexto

Atualmente o endpoint `PATCH /ordens-servico/{id}/aprovar` exige role `OPERADOR` (aprovação interna).
Este plano implementa o fluxo real: o sistema notifica o cliente por e-mail quando o orçamento está pronto,
e o cliente aprova ou recusa através de links com token seguro.

---

## Fluxo Completo

```
Mecânico avança: EM_DIAGNOSTICO → AGUARDANDO_APROVACAO
        │
        ▼ (gatilho automático)
Sistema gera AprovacaoToken (UUID, expira em 48h) + persiste no banco
        │
        ▼
NotificacaoEmailService envia e-mail ao cliente
        │
   ┌────┴──────┐
   ▼           ▼
APROVA       RECUSA
   │           │
   ▼           ▼
GET /api/ordens-servico/aprovar?token=xxx
GET /api/ordens-servico/recusar?token=xxx
   │           │
   ▼           ▼
EM_EXECUCAO   ORCAMENTO_RECUSADO (terminal)
+ saída        (sem movimentação
  estoque       de estoque)
```

---

## Passo 1 — `StatusOS` (enum)

- Adicionar `ORCAMENTO_RECUSADO` como status terminal.
- Atualizar o switch de `avancarStatus` para lançar exceção se status atual for `ORCAMENTO_RECUSADO`.

---

## Passo 2 — Entidade `AprovacaoToken`

Nova tabela `aprovacao_token`:

| Campo | Tipo | Detalhe |
|-------|------|---------|
| `id` | Long | PK |
| `token` | String (UUID) | único |
| `ordemServico` | FK | `@ManyToOne` |
| `expiresAt` | LocalDateTime | `now() + 48h` |
| `usado` | boolean | evita reuso |

Novo `AprovacaoTokenRepository` com `findByToken(String token)`.

---

## Passo 3 — `NotificacaoEmailService`

Método único: `enviarOrcamento(OrdemServico os, String token)`.

Monta e envia e-mail HTML com:
- Dados da OS: número, veículo, itens (nome, quantidade, preço unitário), valor total
- Link **APROVAR** → `{app.base-url}/api/ordens-servico/aprovar?token=<uuid>`
- Link **RECUSAR** → `{app.base-url}/api/ordens-servico/recusar?token=<uuid>`
- Telefone da oficina para dúvidas
- Aviso de expiração (48h)

Dependência: `spring-boot-starter-mail` + `JavaMailSender`.

---

## Passo 4 — `OrdemServicoService` (alterações)

### `avancarStatus` — gatilho ao entrar em `AGUARDANDO_APROVACAO`
```
gera AprovacaoToken → persiste → envia e-mail
```

### Novo método `aprovarViaToken(String token)`
1. Busca token no banco → 404 se não encontrado
2. Valida `usado == false` → 409 se já usado
3. Valida `expiresAt.isAfter(now())` → 422 se expirado
4. Valida OS em `AGUARDANDO_APROVACAO` → 422 se não estiver
5. Seta status `EM_EXECUCAO` + `dataAprovacao` + `dataInicio`
6. Chama `registrarSaidasEstoque`
7. Marca token `usado = true`

### Novo método `recusarViaToken(String token)`
1. Mesmas validações dos itens 1–4 acima
2. Seta status `ORCAMENTO_RECUSADO`
3. Marca token `usado = true`

### Tabela de erros

| Situação | HTTP |
|----------|------|
| Token não encontrado | 404 |
| Token já usado | 409 |
| Token expirado | 422 |
| OS não está em `AGUARDANDO_APROVACAO` | 422 |

---

## Passo 5 — `OrdemServicoController` (dois novos endpoints públicos)

```
GET /api/ordens-servico/aprovar?token={uuid}  → 200 com OS atualizada
GET /api/ordens-servico/recusar?token={uuid}  → 200 com OS atualizada
```

Sem `@PreAuthorize` — são públicos (acesso do cliente via link de e-mail).

---

## Passo 6 — `SecurityConfig`

Adicionar os dois novos paths ao bloco `permitAll()`.

---

## Passo 7 — Configurações

### `application.properties`
```properties
app.base-url=http://localhost:8080
spring.mail.host=
spring.mail.port=587
spring.mail.username=
spring.mail.password=
spring.mail.properties.mail.smtp.auth=true
spring.mail.properties.mail.smtp.starttls.enable=true
```

### `application-dev.properties`
Apontar para MailHog (SMTP fake local):
```properties
app.base-url=http://localhost:8080
spring.mail.host=localhost
spring.mail.port=1025
spring.mail.username=
spring.mail.password=
spring.mail.properties.mail.smtp.auth=false
spring.mail.properties.mail.smtp.starttls.enable=false
```

### `docker-compose.yml`
- Adicionar serviço `mailhog` (imagem `mailhog/mailhog`, portas `1025` e `8025`)
- Adicionar variáveis de e-mail nas env vars da API

---

## Passo 8 — Testes

### ITs existentes
Adicionar `@MockBean JavaMailSender` nos ITs que testem fluxo de OS,
para não tentar envio real ao avançar para `AGUARDANDO_APROVACAO`.

### Novos testes em `OrdemServicoControllerIT`

| Cenário | Esperado |
|---------|----------|
| Avanço para `AGUARDANDO_APROVACAO` | Token gerado + `verify(mailSender).send(...)` |
| `GET /aprovar?token=<válido>` | 200, status `EM_EXECUCAO` |
| `GET /recusar?token=<válido>` | 200, status `ORCAMENTO_RECUSADO` |
| `GET /aprovar?token=<inexistente>` | 404 |
| `GET /aprovar?token=<já usado>` | 409 |
| `GET /aprovar?token=<expirado>` | 422 |
| OS não em `AGUARDANDO_APROVACAO` | 422 |

---

## Observações

- `Cliente.email` já existe na entidade — nenhuma migration necessária para esse campo.
- O endpoint interno `PATCH /{id}/aprovar` (role OPERADOR) pode ser mantido como bypass
  para aprovações presenciais/telefônicas, sem conflito com o fluxo de e-mail.
- O status `ORCAMENTO_RECUSADO` é terminal: nenhum avanço de status é possível a partir dele.
- Em produção, substituir MailHog por um provedor real (SendGrid, AWS SES, Gmail SMTP).

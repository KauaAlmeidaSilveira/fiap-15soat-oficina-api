# Diagrama de Sequência — Abertura de Ordem de Serviço

> Cobre só o fluxo de **criação** da OS (`POST /api/ordens-servico`), conforme pedido em
> `docs/requisitos-fase3.md` ("fluxo de... abertura de ordens de serviço"). O ciclo
> completo de status da OS (RECEBIDA → ... → ENTREGUE) já está documentado em texto no
> `README.md`, seção "Fluxo de status da OS".
>
> O diagrama de sequência da **autenticação** (CPF → API Gateway → Lambda → JWT → API)
> fica para quando o RFC da estratégia de autenticação — hoje bloqueado — for fechado (ver
> `CHECKLIST.md`, Seção 1).

```mermaid
sequenceDiagram
    actor Cliente as Recepção/Admin (front-end)
    participant API as OrdemServicoController
    participant Sec as Filtro JWT (SecurityConfig)
    participant UC as OrdemServicoUseCase
    participant CliGw as ClienteGateway
    participant VeiGw as VeiculoGateway
    participant OsGw as OrdemServicoGateway
    participant DB as PostgreSQL (RDS)
    participant Metricas as MetricasGateway (New Relic)

    Cliente->>API: POST /api/ordens-servico (Bearer JWT, clienteId, veiculoId, itens...)
    API->>Sec: valida token JWT
    Sec-->>API: token válido, role ADMIN ou RECEPCAO
    alt token inválido ou role sem permissão
        Sec-->>Cliente: 401/403
    end
    API->>UC: criar(clienteId, veiculoId, descricaoProblema, observacoes, itens)
    UC->>CliGw: buscarPorId(clienteId)
    CliGw->>DB: SELECT cliente
    DB-->>CliGw: cliente encontrado
    CliGw-->>UC: Cliente
    alt cliente não encontrado
        UC-->>API: RecursoNaoEncontradoException
        API-->>Cliente: 404
    end
    UC->>VeiGw: buscarPorId(veiculoId)
    VeiGw->>DB: SELECT veiculo
    DB-->>VeiGw: veiculo encontrado
    VeiGw-->>UC: Veiculo
    alt veículo não encontrado
        UC-->>API: RecursoNaoEncontradoException
        API-->>Cliente: 404
    end
    UC->>UC: monta OrdemServico (status = RECEBIDA, itens, total)
    UC->>OsGw: salvar(os)
    OsGw->>DB: INSERT ordem_servico (+ itens)
    DB-->>OsGw: OS persistida (id, numero)
    OsGw-->>UC: OrdemServico criada
    UC->>Metricas: ordemServicoCriada()
    UC-->>API: OrdemServico criada
    API-->>Cliente: 201 Created (OrdemServicoResponse)
```

## Notas sobre o fluxo real

- Role exigida: `ADMIN` ou `RECEPCAO` (`@PreAuthorize("hasAnyRole('ADMIN', 'RECEPCAO')")`
  em `OrdemServicoController.criar`).
- A OS nasce sempre com status `RECEBIDA` — a transição para os demais status
  (`EM_DIAGNOSTICO`, `AGUARDANDO_APROVACAO`, ...) acontece em chamadas separadas, fora do
  escopo deste diagrama.
- `MetricasGateway.ordemServicoCriada()` é o ponto de instrumentação usado pelo dashboard
  de "Volume diário de ordens de serviço" no New Relic (ver Seção 3 do
  `CHECKLIST.md`).
- Se `clienteId` ou `veiculoId` não existirem, o use case lança
  `RecursoNaoEncontradoException`, traduzida pelo `GlobalExceptionHandler` em `404` — não
  chega a persistir nada.

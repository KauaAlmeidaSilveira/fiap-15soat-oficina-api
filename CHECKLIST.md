# Checklist de Entrega — Tech Challenge Fase 1

## Funcionalidades obrigatórias

### Criação da Ordem de Serviço (OS)
- [x] Identificação do cliente por CPF/CNPJ
- [x] Cadastro de veículo (placa, marca, modelo, ano)
- [x] Inclusão dos serviços solicitados
- [x] Possibilidade de incluir peças e insumos necessários
- [x] Orçamento gerado automaticamente com base nos serviços e peças
- [x] Envio do orçamento ao cliente para aprovação

### Acompanhamento da OS
- [x] Status: Recebida
- [x] Status: Em diagnóstico
- [x] Status: Aguardando aprovação
- [x] Status: Em execução
- [x] Status: Finalizada
- [x] Status: Entregue
- [x] Alteração automática dos status conforme ações no sistema
- [x] Consulta por parte do cliente via API para acompanhar o progresso

### Gestão administrativa
- [x] CRUD de clientes
- [x] CRUD de veículos
- [x] CRUD de serviços
- [x] CRUD de peças e insumos com controle de estoque
- [x] Listagem e detalhamento de ordens de serviço
- [x] Monitoramento do tempo médio de execução dos serviços

### Segurança e qualidade
- [x] Implementação de autenticação JWT para APIs administrativas
- [x] Validação dos dados sensíveis (CPF/CNPJ, placa de veículo)
- [x] Em relação a validação de dados sensíveis Como os documentos CPF CNPJ E a placa do veículo Devem ser validados utilizando bibliotecas para validação de CPF/CNPJ e placa de veículo, garantindo que os dados inseridos estejam no formato correto e sejam válidos.
- [x] Testes unitários para os principais fluxos
- [x] Testes de integração para os principais fluxos
- [x] Cobertura mínima de 80% nos domínios críticos

## Requisitos técnicos
- [x] Back-end monolítico com arquitetura em camadas
- [x] Justificativa da escolha do banco de dados (README.md)
- [x] APIs RESTful documentadas via Swagger
- [x] Dockerfile para build da aplicação
- [x] docker-compose.yml para orquestrar ambiente completo
- [x] Cobertura de testes mínima de 80% nos domínios críticos
- [x] README.md completo com instruções de uso
- [ ] Repositório privado com acesso ao usuário `soat-architecture`

## Entregáveis da Fase 1
- [ ] Revisar mensagens salvas no discord para garantir que os requisitos foram atendidos
- [ ] Vídeo de até 15 minutos demonstrando todos os pontos
- [ ] Documentação DDD (Miro ou equivalente)
  - [ ] Event Storming — fluxo de criação e acompanhamento da OS
  - [ ] Event Storming — gestão de peças e insumos
  - [ ] Diagramas conforme disciplina de DDD
  - [ ] Linguagem Ubíqua aplicada
- [x] Código-fonte no repositório
  - [x] APIs conforme requisitos
  - [x] Dockerfile e docker-compose configurados
  - [x] README.md completo com instruções de uso e objetivos
- [ ] Relatório com análise de vulnerabilidades (scan do código)
- [ ] Documento de entrega (PDF) contendo:
  - [ ] Nome do grupo
  - [ ] Participantes e usernames no Discord
  - [ ] Link da documentação DDD
  - [ ] Link do repositório
  - [ ] Relatório com análise de vulnerabilidades

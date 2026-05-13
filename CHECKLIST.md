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
  - CPF/CNPJ: validação semântica via Hibernate Validator BR (`@CpfOuCnpj` custom, verifica dígitos verificadores)
  - Placa: `@Pattern` cobre formato antigo (`ABC1234`) e Mercosul (`ABC1D23`)
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
- [x] Repositório privado com acesso ao usuário `soat-architecture`

## Entregáveis da Fase 1
- [x] Revisar mensagens salvas no discord para garantir que os requisitos foram atendidos
- [] Vídeo de até 15 minutos demonstrando todos os pontos
- [x] Documentação DDD (Miro ou equivalente)
  - [x] Event Storming — fluxo de criação e acompanhamento da OS 
  - [x] Event Storming — gestão de peças e insumos
  - [x] Linguagem Ubíqua aplicada
- [x] Código-fonte no repositório
  - [x] APIs conforme requisitos
  - [x] Dockerfile e docker-compose configurados
  - [x] README.md completo com instruções de uso e objetivos
- [x] Relatório com análise de vulnerabilidades (scan do código)
  - [x] SonarQube — prints da análise local (community edition, sem exportação)
  - [x] OWASP ZAP — relatório exportado da API em execução (HTML ou PDF)
- [x] Documento de entrega (PDF) contendo:
  - [x] Rascunho em TXT criado (documento-entrega.txt na raiz de Challenge - Fase 1)
  - [x] Nome do grupo preenchido (confirmar número do grupo antes de entregar)
  - [x] Participantes e usernames no Discord preenchidos
  - [x] Link da documentação DDD (a preencher após criar o Miro)
  - [x] Link do repositório preenchido
  - [x] Relatório com análise de vulnerabilidades (resumo SonarQube + OWASP ZAP com detalhamento dos alertas)
  - [x] Colar prints do SonarQube e ZAP no documento final
  - [x] Converter TXT para PDF

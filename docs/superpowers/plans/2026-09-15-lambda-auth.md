# Lambda de Autenticação e API Gateway: Plano de Implementação

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Criar o repositório `fiap-15soat-oficina-lambda-auth` com a Lambda Node.js que autentica funcionários (e-mail + senha) e clientes (CPF + senha), e o API Gateway HTTP que expõe a Lambda e encaminha `/api/**` para a API no EKS com validação de JWT.

**Architecture:** Lambda `oficina-auth` em Node 22 dentro das subnets privadas, lendo chave RSA e senha do banco no Secrets Manager e consultando o RDS com `pg`. Emite JWT RS256 (`aud = oficina-api`, `iss` = URL do Gateway) e serve discovery/JWKS. O API Gateway HTTP roteia `/auth/*` e `/.well-known/*` para a Lambda e `/api/**` (com JWT authorizer nativo) por VPC Link até o NLB interno da API. Tudo provisionado por Terraform no próprio repositório.

**Tech Stack:** Node.js 22 (ESM), `pg`, `bcryptjs`, `jose`, `@aws-sdk/client-secrets-manager`, `node --test`, Terraform ≥ 1.5 com provider AWS ~> 5.60, GitHub Actions.

**Spec:** `docs/superpowers/specs/2026-09-15-api-gateway-lambda-auth-design.md` (no repositório `fiap-15soat-oficina-api`)

**Plano irmão:** `docs/superpowers/plans/2026-09-15-api-autenticacao-gateway.md` — cria a coluna `cliente.senha_hash`, a regra `CLIENTE` e o NLB interno que este plano consome.

## Global Constraints

- Diretório do repositório: `/Users/juliocavalcanti/workspace/fiap-15soat-oficina-lambda-auth` (já existe, `git init` sem commits, remote `git@github.com:KauaAlmeidaSilveira/fiap-15soat-oficina-lambda-auth.git`). Branch principal: `master` (é a branch padrão do repositório no GitHub; o workflow dispara em `main` e `master`).
- Node **22** local (a máquina tem 18; use `nvm install 22 && nvm use 22`). Runtime da Lambda: `nodejs22.x`.
- Código ESM (`"type": "module"`). Nomes de variáveis, mensagens de erro e testes em português.
- Commits em **inglês**, Conventional Commits, **só a linha de assunto** — sem corpo e sem linha de coautoria.
- AWS Academy: `iam:CreateRole` bloqueado. Toda role é a `LabRole`, com ARN montado a partir de `aws_caller_identity`.
- Contrato de nomes compartilhado: `project_name = "oficina"` → VPC `oficina-vpc`, subnets `oficina-vpc-private-*`, RDS `oficina-db`, banco `oficina`, usuário `oficina`.
- NLB da API descoberto pela tag `kubernetes.io/service-name = oficina/oficina-api`, listener na porta 80.
- Tokens: RS256 com `kid`; `aud = "oficina-api"`; `iss = "https://" + requestContext.domainName`. Funcionário: `sub = username`, `roles` do banco, 28800 s. Cliente: `sub = String(id)`, `roles = ["CLIENTE"]`, 3600 s.
- BCrypt custo 10, compatível com os hashes `$2a$` gravados pelo Spring.
- Nunca logar senha, hash, token ou chave.
- Um checkpoint por tarefa: testes da tarefa passando + commit, antes de seguir.

---

### Task 1: Spike de permissões no AWS Academy (descartável)

Nada deste passo vira código. O objetivo é descobrir, antes de escrever o resto, se a `LabRole` permite o que o desenho exige. **Se qualquer verificação falhar, pare e leve o resultado ao usuário** — o desenho precisa mudar.

**Pré-requisito:** credenciais do Academy válidas no terminal (`aws sts get-caller-identity` responde). Não precisa do EKS no ar: o spike usa a VPC default.

- [ ] **Step 1: Variáveis do spike**

```bash
export AWS_REGION=us-east-1
CONTA=$(aws sts get-caller-identity --query Account --output text)
ROLE="arn:aws:iam::${CONTA}:role/LabRole"
VPC=$(aws ec2 describe-vpcs --filters Name=is-default,Values=true --query 'Vpcs[0].VpcId' --output text)
SUBNETS=$(aws ec2 describe-subnets --filters Name=vpc-id,Values=$VPC Name=default-for-az,Values=true --query 'Subnets[0:2].SubnetId' --output text | tr '\t' ',')
SG=$(aws ec2 describe-security-groups --filters Name=vpc-id,Values=$VPC Name=group-name,Values=default --query 'SecurityGroups[0].GroupId' --output text)
echo "$CONTA $VPC $SUBNETS $SG"
```
Expected: quatro valores não vazios.

- [ ] **Step 2: Secrets Manager**

```bash
aws secretsmanager create-secret --name oficina/spike --secret-string '{"teste":"ok"}'
aws secretsmanager get-secret-value --secret-id oficina/spike --query SecretString --output text
```
Expected: `{"teste":"ok"}`.

- [ ] **Step 3: Lambda fora da VPC lendo o segredo com a `LabRole`**

```bash
mkdir -p /tmp/spike && cd /tmp/spike
cat > index.mjs <<'EOF'
import { SecretsManagerClient, GetSecretValueCommand } from '@aws-sdk/client-secrets-manager';
export const handler = async () => {
  const r = await new SecretsManagerClient({}).send(new GetSecretValueCommand({ SecretId: 'oficina/spike' }));
  return { statusCode: 200, body: r.SecretString };
};
EOF
zip -q spike.zip index.mjs
aws lambda create-function --function-name oficina-spike --runtime nodejs22.x --role "$ROLE" \
  --handler index.handler --zip-file fileb://spike.zip --timeout 10
aws lambda wait function-active-v2 --function-name oficina-spike
aws lambda invoke --function-name oficina-spike /tmp/spike/saida.json && cat /tmp/spike/saida.json
```
Expected: `{"statusCode":200,"body":"{\"teste\":\"ok\"}"}`.

- [ ] **Step 4: Lambda dentro de VPC**

```bash
aws lambda update-function-configuration --function-name oficina-spike \
  --vpc-config SubnetIds=$SUBNETS,SecurityGroupIds=$SG
aws lambda wait function-updated-v2 --function-name oficina-spike
aws lambda get-function-configuration --function-name oficina-spike --query 'VpcConfig.VpcId' --output text
```
Expected: o id da VPC default, sem `AccessDenied` (a criação de ENI pela `LabRole` funciona). A invocação dentro da VPC default não alcança o Secrets Manager (não há NAT), e isso é esperado.

- [ ] **Step 5: HTTP API + VPC Link + JWT authorizer**

```bash
LINK=$(aws apigatewayv2 create-vpc-link --name oficina-spike --subnet-ids ${SUBNETS//,/ } --security-group-ids $SG --query VpcLinkId --output text)
for i in $(seq 1 30); do s=$(aws apigatewayv2 get-vpc-link --vpc-link-id $LINK --query VpcLinkStatus --output text); echo $s; [ "$s" = AVAILABLE ] && break; sleep 10; done
API=$(aws apigatewayv2 create-api --name oficina-spike --protocol-type HTTP --query ApiId --output text)
aws apigatewayv2 create-authorizer --api-id $API --name spike-jwt --authorizer-type JWT \
  --identity-source '$request.header.Authorization' \
  --jwt-configuration "Audience=oficina-api,Issuer=https://${API}.execute-api.${AWS_REGION}.amazonaws.com"
```
Expected: VPC Link chega a `AVAILABLE`; `create-authorizer` devolve um `AuthorizerId`.

- [ ] **Step 6: Limpar tudo**

```bash
aws apigatewayv2 delete-api --api-id $API
aws apigatewayv2 delete-vpc-link --vpc-link-id $LINK
aws lambda delete-function --function-name oficina-spike
aws secretsmanager delete-secret --secret-id oficina/spike --force-delete-without-recovery
rm -rf /tmp/spike
```
Expected: sem erros. (As ENIs da Lambda somem sozinhas em alguns minutos.)

- [ ] **Step 7: Registrar o resultado**

Anote para o usuário, em uma linha por verificação, o que passou e o que falhou (Steps 2 a 5). Siga para a Task 2 **só se tudo passou**.

---

### Task 2: Estrutura do repositório e validação de CPF

**Files:**
- Create: `package.json`, `.nvmrc`, `.gitignore`
- Create: `src/cpf.js`
- Test: `test/unit/cpf.test.js`

**Interfaces:**
- Produces: `normalizarDocumento(valor: unknown): string` (só dígitos) e `cpfValido(cpf: string): boolean` (espera 11 dígitos já normalizados).

- [ ] **Step 1: Preparar o repositório**

```bash
cd /Users/juliocavalcanti/workspace/fiap-15soat-oficina-lambda-auth
nvm use 22 || nvm install 22
```

`.nvmrc`:
```
22
```

`.gitignore`:
```
node_modules/
dist/
coverage/
.env
*.pem
*.key
terraform/.terraform/
terraform/*.tfstate*
terraform/terraform.tfvars
terraform/tfplan
```

`package.json`:
```json
{
  "name": "fiap-15soat-oficina-lambda-auth",
  "version": "1.0.0",
  "private": true,
  "type": "module",
  "engines": { "node": ">=22" },
  "scripts": {
    "test": "node --test \"test/unit/**/*.test.js\"",
    "test:integracao": "node --test \"test/integracao/**/*.test.js\"",
    "token:local": "node scripts/emitir-token-local.js",
    "empacotar": "sh scripts/empacotar.sh"
  }
}
```

- [ ] **Step 2: Escrever o teste**

`test/unit/cpf.test.js`:
```js
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { cpfValido, normalizarDocumento } from '../../src/cpf.js';

test('normaliza removendo pontuação', () => {
  assert.equal(normalizarDocumento('529.982.247-25'), '52998224725');
});

test('normaliza valores ausentes para string vazia', () => {
  assert.equal(normalizarDocumento(undefined), '');
  assert.equal(normalizarDocumento(null), '');
});

test('aceita CPF com dígitos verificadores corretos', () => {
  assert.equal(cpfValido('52998224725'), true);
  assert.equal(cpfValido('71498053297'), true);
  assert.equal(cpfValido('98765432100'), true);
});

test('rejeita CPF com dígito verificador errado', () => {
  assert.equal(cpfValido('12345678901'), false);
  assert.equal(cpfValido('52998224724'), false);
});

test('rejeita sequência de dígitos repetidos', () => {
  assert.equal(cpfValido('11111111111'), false);
});

test('rejeita tamanho diferente de 11', () => {
  assert.equal(cpfValido('5299822472'), false);
  assert.equal(cpfValido('12345678000199'), false);
  assert.equal(cpfValido(''), false);
});
```

- [ ] **Step 3: Rodar e ver falhar**

Run: `npm test`
Expected: FAIL — `Cannot find module '.../src/cpf.js'`.

- [ ] **Step 4: Implementar**

`src/cpf.js`:
```js
export function normalizarDocumento(valor) {
  return String(valor ?? '').replace(/\D/g, '');
}

function digitoVerificador(base) {
  const soma = [...base].reduce((acc, digito, i) => acc + Number(digito) * (base.length + 1 - i), 0);
  const resto = soma % 11;
  return resto < 2 ? 0 : 11 - resto;
}

export function cpfValido(cpf) {
  if (!/^\d{11}$/.test(cpf) || /^(\d)\1{10}$/.test(cpf)) {
    return false;
  }
  return digitoVerificador(cpf.slice(0, 9)) === Number(cpf[9])
    && digitoVerificador(cpf.slice(0, 10)) === Number(cpf[10]);
}
```

- [ ] **Step 5: Rodar e ver passar**

Run: `npm test`
Expected: PASS, 6 testes.

- [ ] **Step 6: Commit**

```bash
git add .nvmrc .gitignore package.json src/cpf.js test/unit/cpf.test.js
git commit -m "feat: add cpf normalization and check digit validation"
```

---

### Task 3: Regras de autenticação

**Files:**
- Create: `src/erros.js`
- Create: `src/autenticacao.js`
- Test: `test/unit/autenticacao.test.js`

**Interfaces:**
- Consumes: `normalizarDocumento`, `cpfValido` (Task 2).
- Produces:
  - `class ErroHttp extends Error { status: number; titulo: string; detalhe: string }` — `new ErroHttp(status, titulo, detalhe)`.
  - `criarAutenticacao({ repositorio, senhas, tokens })` devolvendo:
    - `loginFuncionario(corpo, issuer) → Promise<{ token, expiresIn }>`
    - `loginCliente(corpo, issuer) → Promise<{ token, expiresIn }>`
    - `primeiroAcesso(corpo, issuer) → Promise<{ token, expiresIn }>`
  - Contratos esperados das dependências (implementados nas Tasks 4 e 5):
    - `repositorio.buscarFuncionario(username) → Promise<{ username, senhaHash, roles: string[] } | null>`
    - `repositorio.buscarClientePorCpf(cpf) → Promise<{ id: number, status: string, senhaHash: string | null } | null>`
    - `repositorio.definirSenhaPrimeiroAcesso(cpf, email, senhaHash) → Promise<number | null>`
    - `senhas.conferir(senha, hashOuNull) → Promise<boolean>` (com `null`, compara contra hash fictício e devolve `false`)
    - `senhas.gerarHash(senha) → Promise<string>`
    - `tokens.emitir({ sub, roles, expiraEmSegundos, issuer }) → Promise<string>`

- [ ] **Step 1: Escrever os testes**

`test/unit/autenticacao.test.js`:
```js
import { test, describe } from 'node:test';
import assert from 'node:assert/strict';
import { criarAutenticacao } from '../../src/autenticacao.js';
import { ErroHttp } from '../../src/erros.js';

const ISSUER = 'https://abc123.execute-api.us-east-1.amazonaws.com';
const CPF = '52998224725';

function montar({ funcionario = null, cliente = null, idPrimeiroAcesso = null } = {}) {
  const chamadas = { conferir: [], emitir: [], definirSenha: [] };
  const repositorio = {
    buscarFuncionario: async (username) => (funcionario && funcionario.username === username ? funcionario : null),
    buscarClientePorCpf: async (cpf) => (cliente && cpf === CPF ? cliente : null),
    definirSenhaPrimeiroAcesso: async (cpf, email, hash) => {
      chamadas.definirSenha.push({ cpf, email, hash });
      return idPrimeiroAcesso;
    },
  };
  const senhas = {
    conferir: async (senha, hash) => {
      chamadas.conferir.push({ senha, hash });
      return hash !== null && hash === `hash:${senha}`;
    },
    gerarHash: async (senha) => `hash:${senha}`,
  };
  const tokens = {
    emitir: async (claims) => {
      chamadas.emitir.push(claims);
      return 'token-assinado';
    },
  };
  return { autenticacao: criarAutenticacao({ repositorio, senhas, tokens }), chamadas };
}

async function assertErro(promessa, status) {
  await assert.rejects(promessa, (erro) => erro instanceof ErroHttp && erro.status === status);
}

describe('loginFuncionario', () => {
  const funcionario = { username: 'kaua@gmail.com', senhaHash: 'hash:Admin@123', roles: ['ADMIN'] };

  test('emite token de 8 h com as roles do banco e username normalizado', async () => {
    const { autenticacao, chamadas } = montar({ funcionario });

    const resultado = await autenticacao.loginFuncionario({ username: '  Kaua@Gmail.com ', senha: 'Admin@123' }, ISSUER);

    assert.deepEqual(resultado, { token: 'token-assinado', expiresIn: 28800 });
    assert.deepEqual(chamadas.emitir[0], { sub: 'kaua@gmail.com', roles: ['ADMIN'], expiraEmSegundos: 28800, issuer: ISSUER });
  });

  test('senha errada responde 401', async () => {
    const { autenticacao } = montar({ funcionario });
    await assertErro(autenticacao.loginFuncionario({ username: 'kaua@gmail.com', senha: 'errada' }, ISSUER), 401);
  });

  test('usuário inexistente responde 401 e ainda executa a comparação de senha', async () => {
    const { autenticacao, chamadas } = montar({ funcionario });
    await assertErro(autenticacao.loginFuncionario({ username: 'nao@existe.com', senha: 'x' }, ISSUER), 401);
    assert.equal(chamadas.conferir.length, 1);
    assert.equal(chamadas.conferir[0].hash, null);
  });

  test('corpo sem username ou senha responde 400', async () => {
    const { autenticacao } = montar({ funcionario });
    await assertErro(autenticacao.loginFuncionario({ senha: 'x' }, ISSUER), 400);
    await assertErro(autenticacao.loginFuncionario({ username: 'kaua@gmail.com' }, ISSUER), 400);
    await assertErro(autenticacao.loginFuncionario(null, ISSUER), 400);
  });
});

describe('loginCliente', () => {
  const clienteAtivo = { id: 7, status: 'ATIVO', senhaHash: 'hash:Cliente@123' };

  test('emite token de 1 h com role CLIENTE e sub igual ao id', async () => {
    const { autenticacao, chamadas } = montar({ cliente: clienteAtivo });

    const resultado = await autenticacao.loginCliente({ cpf: '529.982.247-25', senha: 'Cliente@123' }, ISSUER);

    assert.deepEqual(resultado, { token: 'token-assinado', expiresIn: 3600 });
    assert.deepEqual(chamadas.emitir[0], { sub: '7', roles: ['CLIENTE'], expiraEmSegundos: 3600, issuer: ISSUER });
  });

  test('CPF com dígito verificador inválido responde 400', async () => {
    const { autenticacao } = montar({ cliente: clienteAtivo });
    await assertErro(autenticacao.loginCliente({ cpf: '12345678901', senha: 'x' }, ISSUER), 400);
  });

  test('CNPJ responde 400 com mensagem própria', async () => {
    const { autenticacao } = montar({ cliente: clienteAtivo });
    await assert.rejects(
      autenticacao.loginCliente({ cpf: '12.345.678/0001-99', senha: 'x' }, ISSUER),
      (erro) => erro.status === 400 && /apenas para CPF/.test(erro.detalhe),
    );
  });

  test('cliente inexistente, sem senha e senha errada respondem o mesmo 401', async () => {
    const inexistente = montar();
    const semSenha = montar({ cliente: { ...clienteAtivo, senhaHash: null } });
    const senhaErrada = montar({ cliente: clienteAtivo });

    const erros = [];
    for (const [{ autenticacao }, senha] of [[inexistente, 'Cliente@123'], [semSenha, 'Cliente@123'], [senhaErrada, 'errada']]) {
      await autenticacao.loginCliente({ cpf: CPF, senha }, ISSUER).catch((e) => erros.push(e));
    }

    assert.equal(erros.length, 3);
    assert.ok(erros.every((e) => e.status === 401 && e.detalhe === erros[0].detalhe));
  });

  test('cliente inativo com senha correta responde 403', async () => {
    const { autenticacao } = montar({ cliente: { ...clienteAtivo, status: 'INATIVO' } });
    await assertErro(autenticacao.loginCliente({ cpf: CPF, senha: 'Cliente@123' }, ISSUER), 403);
  });

  test('cliente inativo com senha errada responde 401, sem revelar o status', async () => {
    const { autenticacao } = montar({ cliente: { ...clienteAtivo, status: 'INATIVO' } });
    await assertErro(autenticacao.loginCliente({ cpf: CPF, senha: 'errada' }, ISSUER), 401);
  });
});

describe('primeiroAcesso', () => {
  test('grava o hash e devolve token do cliente', async () => {
    const { autenticacao, chamadas } = montar({ idPrimeiroAcesso: 7 });

    const resultado = await autenticacao.primeiroAcesso({ cpf: '529.982.247-25', email: 'joao@email.com', senha: 'Cliente@123' }, ISSUER);

    assert.deepEqual(resultado, { token: 'token-assinado', expiresIn: 3600 });
    assert.deepEqual(chamadas.definirSenha[0], { cpf: CPF, email: 'joao@email.com', hash: 'hash:Cliente@123' });
    assert.deepEqual(chamadas.emitir[0], { sub: '7', roles: ['CLIENTE'], expiraEmSegundos: 3600, issuer: ISSUER });
  });

  test('senha com menos de 8 caracteres responde 400 sem tocar no banco', async () => {
    const { autenticacao, chamadas } = montar({ idPrimeiroAcesso: 7 });
    await assertErro(autenticacao.primeiroAcesso({ cpf: CPF, email: 'joao@email.com', senha: 'curta' }, ISSUER), 400);
    assert.equal(chamadas.definirSenha.length, 0);
  });

  test('CPF inválido ou e-mail ausente responde 400', async () => {
    const { autenticacao } = montar({ idPrimeiroAcesso: 7 });
    await assertErro(autenticacao.primeiroAcesso({ cpf: '12345678901', email: 'joao@email.com', senha: 'Cliente@123' }, ISSUER), 400);
    await assertErro(autenticacao.primeiroAcesso({ cpf: CPF, senha: 'Cliente@123' }, ISSUER), 400);
  });

  test('nenhuma linha atualizada responde 422 genérico', async () => {
    const { autenticacao, chamadas } = montar({ idPrimeiroAcesso: null });
    await assertErro(autenticacao.primeiroAcesso({ cpf: CPF, email: 'outro@email.com', senha: 'Cliente@123' }, ISSUER), 422);
    assert.equal(chamadas.emitir.length, 0);
  });
});
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `npm test`
Expected: FAIL — `Cannot find module '.../src/autenticacao.js'`.

- [ ] **Step 3: Implementar `src/erros.js`**

```js
export class ErroHttp extends Error {
  constructor(status, titulo, detalhe) {
    super(detalhe);
    this.status = status;
    this.titulo = titulo;
    this.detalhe = detalhe;
  }
}
```

- [ ] **Step 4: Implementar `src/autenticacao.js`**

```js
import { cpfValido, normalizarDocumento } from './cpf.js';
import { ErroHttp } from './erros.js';

const EXPIRACAO_FUNCIONARIO = 28800;
const EXPIRACAO_CLIENTE = 3600;
const TAMANHO_MINIMO_SENHA = 8;

function textoPreenchido(valor) {
  return typeof valor === 'string' && valor.trim().length > 0;
}

function dadosInvalidos(detalhe) {
  return new ErroHttp(400, 'Dados inválidos', detalhe);
}

function exigirCpf(valor) {
  const documento = normalizarDocumento(valor);
  if (documento.length === 14) {
    throw dadosInvalidos('Login disponível apenas para CPF.');
  }
  if (!cpfValido(documento)) {
    throw dadosInvalidos('CPF inválido.');
  }
  return documento;
}

export function criarAutenticacao({ repositorio, senhas, tokens }) {
  async function tokenDoCliente(id, issuer) {
    const token = await tokens.emitir({ sub: String(id), roles: ['CLIENTE'], expiraEmSegundos: EXPIRACAO_CLIENTE, issuer });
    return { token, expiresIn: EXPIRACAO_CLIENTE };
  }

  return {
    async loginFuncionario(corpo, issuer) {
      if (!textoPreenchido(corpo?.username) || !textoPreenchido(corpo?.senha)) {
        throw dadosInvalidos('Informe username e senha.');
      }
      const username = corpo.username.trim().toLowerCase();
      const funcionario = await repositorio.buscarFuncionario(username);
      const senhaConfere = await senhas.conferir(corpo.senha, funcionario?.senhaHash ?? null);
      if (!funcionario || !senhaConfere) {
        throw new ErroHttp(401, 'Credenciais inválidas', 'Usuário ou senha inválidos.');
      }
      const token = await tokens.emitir({
        sub: funcionario.username, roles: funcionario.roles, expiraEmSegundos: EXPIRACAO_FUNCIONARIO, issuer,
      });
      return { token, expiresIn: EXPIRACAO_FUNCIONARIO };
    },

    async loginCliente(corpo, issuer) {
      if (!textoPreenchido(corpo?.senha)) {
        throw dadosInvalidos('Informe CPF e senha.');
      }
      const cpf = exigirCpf(corpo.cpf);
      const cliente = await repositorio.buscarClientePorCpf(cpf);
      const senhaConfere = await senhas.conferir(corpo.senha, cliente?.senhaHash ?? null);
      if (!cliente || !senhaConfere) {
        throw new ErroHttp(401, 'Credenciais inválidas', 'CPF ou senha inválidos.');
      }
      if (cliente.status !== 'ATIVO') {
        throw new ErroHttp(403, 'Cliente inativo', 'Cliente inativo. Procure a oficina.');
      }
      return tokenDoCliente(cliente.id, issuer);
    },

    async primeiroAcesso(corpo, issuer) {
      if (!textoPreenchido(corpo?.email)) {
        throw dadosInvalidos('Informe CPF, e-mail e senha.');
      }
      if (typeof corpo.senha !== 'string' || corpo.senha.length < TAMANHO_MINIMO_SENHA) {
        throw dadosInvalidos(`A senha deve ter no mínimo ${TAMANHO_MINIMO_SENHA} caracteres.`);
      }
      const cpf = exigirCpf(corpo.cpf);
      const hash = await senhas.gerarHash(corpo.senha);
      const id = await repositorio.definirSenhaPrimeiroAcesso(cpf, corpo.email.trim(), hash);
      if (id === null) {
        throw new ErroHttp(422, 'Primeiro acesso não concluído',
          'Não foi possível concluir o primeiro acesso. Verifique os dados informados ou procure a oficina.');
      }
      return tokenDoCliente(id, issuer);
    },
  };
}
```

- [ ] **Step 5: Rodar e ver passar**

Run: `npm test`
Expected: PASS em todos os testes de `cpf` e `autenticacao`.

- [ ] **Step 6: Commit**

```bash
git add src/erros.js src/autenticacao.js test/unit/autenticacao.test.js
git commit -m "feat: add staff and customer authentication rules"
```

---

### Task 4: Senhas, tokens e script de token local

**Files:**
- Create: `src/senhas.js`
- Create: `src/tokens.js`
- Create: `scripts/emitir-token-local.js`
- Test: `test/unit/senhas.test.js`
- Test: `test/unit/tokens.test.js`

**Interfaces:**
- Produces:
  - `senhas` com `gerarHash(senha): Promise<string>` e `conferir(senha, hashOuNull): Promise<boolean>` (contrato da Task 3).
  - `AUDIENCIA = 'oficina-api'`.
  - `criarTokens(chavePrivadaPem: string)` → `{ emitir({ sub, roles, expiraEmSegundos, issuer }): Promise<string>, jwks(): { keys: [...] } }`. Aceita PEM PKCS#8 ou PKCS#1.

- [ ] **Step 1: Instalar dependências**

```bash
npm install pg@8 bcryptjs@3 jose@6 @aws-sdk/client-secrets-manager@3
```
Expected: `package.json` com as quatro em `dependencies` e `package-lock.json` criado.

- [ ] **Step 2: Escrever os testes**

`test/unit/senhas.test.js`:
```js
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { senhas } from '../../src/senhas.js';

const HASH_GERADO_PELO_SPRING = '$2a$10$FlXMmy.Fkbk8TIc4HtSHa.M7WtITMtW3/0NHj6VPWFvmK4ttTI4T2';

test('hash gerado confere com a senha original', async () => {
  const hash = await senhas.gerarHash('Cliente@123');
  assert.match(hash, /^\$2[aby]\$10\$/);
  assert.equal(await senhas.conferir('Cliente@123', hash), true);
  assert.equal(await senhas.conferir('errada', hash), false);
});

test('confere hash $2a$ no formato gravado pelo BCryptPasswordEncoder do Spring', async () => {
  assert.equal(await senhas.conferir('Admin@123', HASH_GERADO_PELO_SPRING), true);
});

test('hash ausente nunca confere', async () => {
  assert.equal(await senhas.conferir('qualquer', null), false);
});
```

> Se o teste do hash do Spring falhar, regenere o fixture com
> `htpasswd -bnBC 10 "" 'Admin@123' | tr -d ':\n' | sed 's/^\$2y/$2a/'` e substitua a constante.

`test/unit/tokens.test.js`:
```js
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { generateKeyPairSync } from 'node:crypto';
import { createLocalJWKSet, decodeProtectedHeader, jwtVerify } from 'jose';
import { AUDIENCIA, criarTokens } from '../../src/tokens.js';

const ISSUER = 'https://abc123.execute-api.us-east-1.amazonaws.com';

function chavePem(tipo) {
  return generateKeyPairSync('rsa', {
    modulusLength: 2048,
    privateKeyEncoding: { type: tipo, format: 'pem' },
    publicKeyEncoding: { type: 'spki', format: 'pem' },
  }).privateKey;
}

test('emite JWT RS256 verificável pelo JWKS publicado', async () => {
  const tokens = await criarTokens(chavePem('pkcs8'));

  const token = await tokens.emitir({ sub: '7', roles: ['CLIENTE'], expiraEmSegundos: 3600, issuer: ISSUER });
  const { payload } = await jwtVerify(token, createLocalJWKSet(tokens.jwks()), { issuer: ISSUER, audience: AUDIENCIA });

  assert.equal(payload.sub, '7');
  assert.deepEqual(payload.roles, ['CLIENTE']);
  assert.equal(payload.exp - payload.iat, 3600);
});

test('header e JWKS compartilham o mesmo kid', async () => {
  const tokens = await criarTokens(chavePem('pkcs8'));
  const token = await tokens.emitir({ sub: 'kaua@gmail.com', roles: ['ADMIN'], expiraEmSegundos: 60, issuer: ISSUER });

  const { alg, kid } = decodeProtectedHeader(token);
  const [chave] = tokens.jwks().keys;

  assert.equal(alg, 'RS256');
  assert.equal(kid, chave.kid);
  assert.equal(chave.use, 'sig');
  assert.equal(chave.d, undefined);
});

test('aceita chave privada em PKCS#1', async () => {
  const tokens = await criarTokens(chavePem('pkcs1'));
  assert.equal(tokens.jwks().keys.length, 1);
});
```

- [ ] **Step 3: Rodar e ver falhar**

Run: `npm test`
Expected: FAIL — `Cannot find module '.../src/senhas.js'` e `.../src/tokens.js`.

- [ ] **Step 4: Implementar `src/senhas.js`**

```js
import bcrypt from 'bcryptjs';

const CUSTO = 10;
const HASH_FICTICIO = bcrypt.hashSync('senha-ficticia-para-tempo-constante', CUSTO);

export const senhas = {
  gerarHash(senha) {
    return bcrypt.hash(senha, CUSTO);
  },

  async conferir(senha, hash) {
    const confere = await bcrypt.compare(senha, hash ?? HASH_FICTICIO);
    return hash != null && confere;
  },
};
```

- [ ] **Step 5: Implementar `src/tokens.js`**

```js
import { createPrivateKey, createPublicKey } from 'node:crypto';
import { SignJWT, calculateJwkThumbprint, exportJWK } from 'jose';

export const AUDIENCIA = 'oficina-api';

export async function criarTokens(chavePrivadaPem) {
  const chavePrivada = createPrivateKey(chavePrivadaPem);
  const jwkPublico = await exportJWK(createPublicKey(chavePrivada));
  const kid = await calculateJwkThumbprint(jwkPublico);
  const jwks = { keys: [{ ...jwkPublico, kid, alg: 'RS256', use: 'sig' }] };

  return {
    jwks: () => jwks,

    emitir({ sub, roles, expiraEmSegundos, issuer }) {
      const agora = Math.floor(Date.now() / 1000);
      return new SignJWT({ roles })
        .setProtectedHeader({ alg: 'RS256', typ: 'JWT', kid })
        .setIssuer(issuer)
        .setAudience(AUDIENCIA)
        .setSubject(sub)
        .setIssuedAt(agora)
        .setExpirationTime(agora + expiraEmSegundos)
        .sign(chavePrivada);
    },
  };
}
```

- [ ] **Step 6: Rodar e ver passar**

Run: `npm test`
Expected: PASS em todos os testes.

- [ ] **Step 7: Script de token para desenvolvimento local**

Sem Gateway, a API local (`http://localhost:8080`) não tem onde fazer login. `scripts/emitir-token-local.js`:

```js
import { readFileSync } from 'node:fs';
import { parseArgs } from 'node:util';
import { criarTokens } from '../src/tokens.js';

const { values } = parseArgs({
  options: {
    sub: { type: 'string' },
    roles: { type: 'string', default: 'ADMIN' },
    chave: { type: 'string', default: '../fiap-15soat-oficina-api/src/main/resources/jwt.private.key' },
    issuer: { type: 'string', default: 'http://localhost:8080' },
    expira: { type: 'string', default: '3600' },
  },
});

if (!values.sub) {
  console.error('Uso: npm run token:local -- --sub <username ou id do cliente> [--roles ADMIN,RECEPCAO] [--chave caminho.pem]');
  process.exit(1);
}

const tokens = await criarTokens(readFileSync(values.chave, 'utf8'));
console.log(await tokens.emitir({
  sub: values.sub,
  roles: values.roles.split(','),
  expiraEmSegundos: Number(values.expira),
  issuer: values.issuer,
}));
```

Run (com as chaves geradas no repositório da API): `npm run token:local -- --sub kaua@gmail.com --roles ADMIN`
Expected: imprime um JWT de três partes separadas por ponto.

- [ ] **Step 8: Commit**

```bash
git add package.json package-lock.json src/senhas.js src/tokens.js scripts/emitir-token-local.js \
        test/unit/senhas.test.js test/unit/tokens.test.js
git commit -m "feat: add bcrypt password checks and rs256 token signing"
```

---

### Task 5: Repositório PostgreSQL

**Files:**
- Create: `src/repositorio.js`
- Create: `test/integracao/schema.sql`
- Test: `test/integracao/repositorio.test.js`

**Interfaces:**
- Produces: `criarPool({ host, database, user, password, ssl })` e `criarRepositorio(pool)` com os três métodos do contrato da Task 3.

- [ ] **Step 1: Subir um Postgres local para os testes**

```bash
docker run -d --name oficina-auth-pg -e POSTGRES_PASSWORD=postgres -p 5433:5432 postgres:16
```
Expected: container em execução (`docker ps`).

- [ ] **Step 2: Schema equivalente ao gerado pelo Hibernate**

`test/integracao/schema.sql`:
```sql
DROP TABLE IF EXISTS users_roles, users, roles, cliente;

CREATE TABLE roles (
    role_id BIGSERIAL PRIMARY KEY,
    name    VARCHAR(255)
);

CREATE TABLE users (
    id       UUID PRIMARY KEY,
    username VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL
);

CREATE TABLE users_roles (
    user_id UUID   NOT NULL REFERENCES users (id),
    role_id BIGINT NOT NULL REFERENCES roles (role_id),
    PRIMARY KEY (user_id, role_id)
);

CREATE TABLE cliente (
    id             BIGSERIAL PRIMARY KEY,
    nome           VARCHAR(100) NOT NULL,
    cpf_cnpj       VARCHAR(14)  NOT NULL UNIQUE,
    tipo_documento VARCHAR(255) NOT NULL,
    telefone       VARCHAR(20),
    email          VARCHAR(150),
    endereco       VARCHAR(255),
    status         VARCHAR(20)  NOT NULL DEFAULT 'ATIVO',
    senha_hash     VARCHAR(60),
    criado_em      TIMESTAMP    NOT NULL,
    atualizado_em  TIMESTAMP
);
```

- [ ] **Step 3: Escrever os testes**

`test/integracao/repositorio.test.js`:
```js
import { after, before, beforeEach, describe, test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import pg from 'pg';
import { criarRepositorio } from '../../src/repositorio.js';

const pool = new pg.Pool({
  connectionString: process.env.DATABASE_URL_TESTE ?? 'postgres://postgres:postgres@localhost:5433/postgres',
  max: 2,
});
const repositorio = criarRepositorio(pool);
const schema = readFileSync(new URL('./schema.sql', import.meta.url), 'utf8');

before(async () => {
  await pool.query('SELECT 1');
});

beforeEach(async () => {
  await pool.query(schema);
  await pool.query(`
    INSERT INTO roles (name) VALUES ('ADMIN'), ('OPERADOR'), ('RECEPCAO');
    INSERT INTO users (id, username, password) VALUES
      ('11111111-1111-1111-1111-111111111111', 'kaua@gmail.com', '$2a$10$hashAdmin'),
      ('22222222-2222-2222-2222-222222222222', 'semrole@oficina.com', '$2a$10$hashSemRole');
    INSERT INTO users_roles (user_id, role_id)
      SELECT '11111111-1111-1111-1111-111111111111', role_id FROM roles WHERE name IN ('ADMIN', 'RECEPCAO');
    INSERT INTO cliente (nome, cpf_cnpj, tipo_documento, email, status, criado_em) VALUES
      ('João da Silva', '52998224725', 'CPF', 'Joao@Email.com', 'ATIVO', now()),
      ('Maria Oliveira', '98765432100', 'CPF', 'maria@email.com', 'INATIVO', now());
  `);
});

after(async () => {
  await pool.end();
});

describe('buscarFuncionario', () => {
  test('devolve hash e todas as roles', async () => {
    const funcionario = await repositorio.buscarFuncionario('kaua@gmail.com');
    assert.equal(funcionario.username, 'kaua@gmail.com');
    assert.equal(funcionario.senhaHash, '$2a$10$hashAdmin');
    assert.deepEqual([...funcionario.roles].sort(), ['ADMIN', 'RECEPCAO']);
  });

  test('usuário sem role devolve lista vazia', async () => {
    const funcionario = await repositorio.buscarFuncionario('semrole@oficina.com');
    assert.deepEqual(funcionario.roles, []);
  });

  test('usuário inexistente devolve null', async () => {
    assert.equal(await repositorio.buscarFuncionario('nao@existe.com'), null);
  });
});

describe('buscarClientePorCpf', () => {
  test('devolve id numérico, status e hash nulo', async () => {
    const cliente = await repositorio.buscarClientePorCpf('52998224725');
    assert.equal(typeof cliente.id, 'number');
    assert.equal(cliente.status, 'ATIVO');
    assert.equal(cliente.senhaHash, null);
  });

  test('CPF inexistente devolve null', async () => {
    assert.equal(await repositorio.buscarClientePorCpf('71498053297'), null);
  });
});

describe('definirSenhaPrimeiroAcesso', () => {
  test('grava o hash comparando e-mail sem diferenciar maiúsculas', async () => {
    const id = await repositorio.definirSenhaPrimeiroAcesso('52998224725', 'joao@email.com', '$2a$10$novo');
    assert.equal(typeof id, 'number');
    assert.equal((await repositorio.buscarClientePorCpf('52998224725')).senhaHash, '$2a$10$novo');
  });

  test('segunda tentativa não sobrescreve a senha', async () => {
    await repositorio.definirSenhaPrimeiroAcesso('52998224725', 'joao@email.com', '$2a$10$primeira');
    assert.equal(await repositorio.definirSenhaPrimeiroAcesso('52998224725', 'joao@email.com', '$2a$10$segunda'), null);
    assert.equal((await repositorio.buscarClientePorCpf('52998224725')).senhaHash, '$2a$10$primeira');
  });

  test('e-mail divergente devolve null', async () => {
    assert.equal(await repositorio.definirSenhaPrimeiroAcesso('52998224725', 'outro@email.com', '$2a$10$x'), null);
  });

  test('cliente inativo devolve null', async () => {
    assert.equal(await repositorio.definirSenhaPrimeiroAcesso('98765432100', 'maria@email.com', '$2a$10$x'), null);
  });

  test('duas tentativas simultâneas: só uma grava', async () => {
    const resultados = await Promise.all([
      repositorio.definirSenhaPrimeiroAcesso('52998224725', 'joao@email.com', '$2a$10$corridaA'),
      repositorio.definirSenhaPrimeiroAcesso('52998224725', 'joao@email.com', '$2a$10$corridaB'),
    ]);
    assert.equal(resultados.filter((id) => id !== null).length, 1);
  });
});
```

- [ ] **Step 4: Rodar e ver falhar**

Run: `npm run test:integracao`
Expected: FAIL — `Cannot find module '.../src/repositorio.js'`.

- [ ] **Step 5: Implementar**

`src/repositorio.js`:
```js
import pg from 'pg';

export function criarPool({ host, database, user, password, ssl }) {
  return new pg.Pool({
    host,
    database,
    user,
    password,
    port: 5432,
    ssl,
    max: 1,
    idleTimeoutMillis: 60_000,
    connectionTimeoutMillis: 5_000,
  });
}

export function criarRepositorio(pool) {
  return {
    async buscarFuncionario(username) {
      const { rows } = await pool.query(
        `SELECT u.username,
                u.password AS senha_hash,
                COALESCE(array_agg(r.name) FILTER (WHERE r.name IS NOT NULL), '{}') AS roles
           FROM users u
           LEFT JOIN users_roles ur ON ur.user_id = u.id
           LEFT JOIN roles r ON r.role_id = ur.role_id
          WHERE u.username = $1
          GROUP BY u.id, u.username, u.password`,
        [username],
      );
      if (rows.length === 0) return null;
      return { username: rows[0].username, senhaHash: rows[0].senha_hash, roles: rows[0].roles };
    },

    async buscarClientePorCpf(cpf) {
      const { rows } = await pool.query(
        'SELECT id, status, senha_hash FROM cliente WHERE cpf_cnpj = $1',
        [cpf],
      );
      if (rows.length === 0) return null;
      return { id: Number(rows[0].id), status: rows[0].status, senhaHash: rows[0].senha_hash };
    },

    async definirSenhaPrimeiroAcesso(cpf, email, senhaHash) {
      const { rows } = await pool.query(
        `UPDATE cliente
            SET senha_hash = $1
          WHERE cpf_cnpj = $2
            AND status = 'ATIVO'
            AND lower(email) = lower($3)
            AND senha_hash IS NULL
         RETURNING id`,
        [senhaHash, cpf, email],
      );
      return rows.length === 0 ? null : Number(rows[0].id);
    },
  };
}
```

- [ ] **Step 6: Rodar e ver passar**

Run: `npm run test:integracao`
Expected: PASS nos 10 testes.

- [ ] **Step 7: Commit**

```bash
git add src/repositorio.js test/integracao/schema.sql test/integracao/repositorio.test.js
git commit -m "feat: add postgres repository for staff and customer credentials"
```

---

### Task 6: Handler da Lambda e segredos

**Files:**
- Create: `src/segredos.js`
- Create: `src/handler.js`
- Test: `test/unit/handler.test.js`

**Interfaces:**
- Consumes: `criarAutenticacao` (Task 3), `ErroHttp` (Task 3), `senhas` e `criarTokens` (Task 4), `criarPool`/`criarRepositorio` (Task 5).
- Produces:
  - `lerSegredos(secretId, cliente?)` → `Promise<{ jwtPrivateKey, dbPassword }>` com cache por cold start.
  - `criarHandler(obterDependencias: () => Promise<{ autenticacao, tokens }>)` → `async (event) => resposta HTTP API 2.0`.
  - `handler` exportado para a Lambda: `src/handler.handler`. Variáveis de ambiente: `DB_HOST`, `DB_NAME`, `DB_USER`, `SECRET_ID`, `DB_SSL` (opcional, `false` desliga TLS).

- [ ] **Step 1: Escrever os testes**

`test/unit/handler.test.js`:
```js
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { criarHandler } from '../../src/handler.js';
import { ErroHttp } from '../../src/erros.js';

const DOMINIO = 'abc123.execute-api.us-east-1.amazonaws.com';

function evento(routeKey, corpo, extras = {}) {
  return {
    routeKey,
    headers: { 'x-correlation-id': 'rastreio-1' },
    requestContext: { domainName: DOMINIO },
    body: corpo === undefined ? undefined : JSON.stringify(corpo),
    isBase64Encoded: false,
    ...extras,
  };
}

function montar(autenticacao = {}) {
  const recebidos = [];
  const registrar = (nome, resposta) => async (corpo, issuer) => {
    recebidos.push({ nome, corpo, issuer });
    if (resposta instanceof Error) throw resposta;
    return resposta;
  };
  const dependencias = {
    autenticacao: {
      loginFuncionario: registrar('loginFuncionario', autenticacao.loginFuncionario ?? { token: 't', expiresIn: 28800 }),
      loginCliente: registrar('loginCliente', autenticacao.loginCliente ?? { token: 't', expiresIn: 3600 }),
      primeiroAcesso: registrar('primeiroAcesso', autenticacao.primeiroAcesso ?? { token: 't', expiresIn: 3600 }),
    },
    tokens: { jwks: () => ({ keys: [{ kid: 'k1' }] }) },
  };
  return { handler: criarHandler(async () => dependencias), recebidos };
}

test('POST /auth/funcionario responde 200 e repassa corpo e issuer', async () => {
  const { handler, recebidos } = montar();

  const resposta = await handler(evento('POST /auth/funcionario', { username: 'a', senha: 'b' }));

  assert.equal(resposta.statusCode, 200);
  assert.deepEqual(JSON.parse(resposta.body), { token: 't', expiresIn: 28800 });
  assert.deepEqual(recebidos[0], { nome: 'loginFuncionario', corpo: { username: 'a', senha: 'b' }, issuer: `https://${DOMINIO}` });
});

test('POST /auth/cliente responde 200', async () => {
  const { handler } = montar();
  assert.equal((await handler(evento('POST /auth/cliente', { cpf: '1', senha: '2' }))).statusCode, 200);
});

test('POST /auth/cliente/primeiro-acesso responde 201', async () => {
  const { handler } = montar();
  assert.equal((await handler(evento('POST /auth/cliente/primeiro-acesso', { cpf: '1' }))).statusCode, 201);
});

test('corpo em base64 é decodificado', async () => {
  const { handler, recebidos } = montar();
  const corpo = Buffer.from(JSON.stringify({ username: 'a', senha: 'b' })).toString('base64');

  await handler(evento('POST /auth/funcionario', undefined, { body: corpo, isBase64Encoded: true }));

  assert.deepEqual(recebidos[0].corpo, { username: 'a', senha: 'b' });
});

test('ErroHttp vira problem+json com o status do erro', async () => {
  const { handler } = montar({ loginCliente: new ErroHttp(401, 'Credenciais inválidas', 'CPF ou senha inválidos.') });

  const resposta = await handler(evento('POST /auth/cliente', { cpf: '1', senha: '2' }));

  assert.equal(resposta.statusCode, 401);
  assert.equal(resposta.headers['content-type'], 'application/problem+json');
  const problema = JSON.parse(resposta.body);
  assert.equal(problema.status, 401);
  assert.equal(problema.title, 'Credenciais inválidas');
  assert.equal(problema.detail, 'CPF ou senha inválidos.');
  assert.ok(problema.timestamp);
});

test('JSON malformado responde 400', async () => {
  const { handler } = montar();
  const resposta = await handler(evento('POST /auth/cliente', undefined, { body: '{nao-e-json' }));
  assert.equal(resposta.statusCode, 400);
});

test('erro inesperado responde 500 sem vazar a mensagem interna', async () => {
  const { handler } = montar({ loginCliente: new Error('connection refused 10.0.1.5') });

  const resposta = await handler(evento('POST /auth/cliente', { cpf: '1', senha: '2' }));

  assert.equal(resposta.statusCode, 500);
  assert.doesNotMatch(resposta.body, /10\.0\.1\.5/);
});

test('rota desconhecida responde 404', async () => {
  const { handler } = montar();
  assert.equal((await handler(evento('GET /auth/qualquer'))).statusCode, 404);
});

test('discovery usa o domínio da requisição como issuer', async () => {
  const { handler } = montar();

  const resposta = await handler(evento('GET /.well-known/{proxy+}', undefined, { rawPath: '/.well-known/openid-configuration' }));

  const documento = JSON.parse(resposta.body);
  assert.equal(resposta.statusCode, 200);
  assert.equal(documento.issuer, `https://${DOMINIO}`);
  assert.equal(documento.jwks_uri, `https://${DOMINIO}/.well-known/jwks.json`);
});

test('jwks.json devolve as chaves públicas', async () => {
  const { handler } = montar();

  const resposta = await handler(evento('GET /.well-known/{proxy+}', undefined, { rawPath: '/.well-known/jwks.json' }));

  assert.equal(resposta.statusCode, 200);
  assert.deepEqual(JSON.parse(resposta.body), { keys: [{ kid: 'k1' }] });
});
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `npm test`
Expected: FAIL — `Cannot find module '.../src/handler.js'`.

- [ ] **Step 3: Implementar `src/segredos.js`**

```js
import { GetSecretValueCommand, SecretsManagerClient } from '@aws-sdk/client-secrets-manager';

let cache;

export async function lerSegredos(secretId, cliente = new SecretsManagerClient({})) {
  if (!cache) {
    const resposta = await cliente.send(new GetSecretValueCommand({ SecretId: secretId }));
    cache = JSON.parse(resposta.SecretString);
  }
  return cache;
}
```

- [ ] **Step 4: Implementar `src/handler.js`**

```js
import { criarAutenticacao } from './autenticacao.js';
import { ErroHttp } from './erros.js';
import { criarPool, criarRepositorio } from './repositorio.js';
import { lerSegredos } from './segredos.js';
import { senhas } from './senhas.js';
import { criarTokens } from './tokens.js';

const ROTAS_AUTENTICACAO = {
  'POST /auth/funcionario': { metodo: 'loginFuncionario', status: 200 },
  'POST /auth/cliente': { metodo: 'loginCliente', status: 200 },
  'POST /auth/cliente/primeiro-acesso': { metodo: 'primeiroAcesso', status: 201 },
};

function json(status, corpo, tipo = 'application/json') {
  return { statusCode: status, headers: { 'content-type': tipo }, body: JSON.stringify(corpo) };
}

function problema(status, titulo, detalhe) {
  return json(status, {
    type: 'about:blank', title: titulo, status, detail: detalhe, timestamp: new Date().toISOString(),
  }, 'application/problem+json');
}

function lerCorpo(event) {
  if (!event.body) return null;
  const texto = event.isBase64Encoded ? Buffer.from(event.body, 'base64').toString('utf8') : event.body;
  try {
    return JSON.parse(texto);
  } catch {
    throw new ErroHttp(400, 'Erro de validação', 'O corpo da requisição não é um JSON válido.');
  }
}

function log(nivel, dados) {
  console.log(JSON.stringify({ nivel, timestamp: new Date().toISOString(), ...dados }));
}

async function responder(event, dependencias, issuer) {
  const rotaAutenticacao = ROTAS_AUTENTICACAO[event.routeKey];
  if (rotaAutenticacao) {
    const { autenticacao } = await dependencias();
    const resultado = await autenticacao[rotaAutenticacao.metodo](lerCorpo(event), issuer);
    return json(rotaAutenticacao.status, resultado);
  }
  if (event.routeKey === 'GET /.well-known/{proxy+}') {
    if (event.rawPath === '/.well-known/openid-configuration') {
      return json(200, {
        issuer,
        jwks_uri: `${issuer}/.well-known/jwks.json`,
        id_token_signing_alg_values_supported: ['RS256'],
        response_types_supported: ['token'],
        subject_types_supported: ['public'],
      });
    }
    if (event.rawPath === '/.well-known/jwks.json') {
      const { tokens } = await dependencias();
      return json(200, tokens.jwks());
    }
  }
  return problema(404, 'Recurso não encontrado', 'Rota não encontrada.');
}

export function criarHandler(dependencias) {
  return async (event) => {
    const inicio = Date.now();
    const issuer = `https://${event.requestContext.domainName}`;
    const correlationId = event.headers?.['x-correlation-id'];
    let resposta;
    try {
      resposta = await responder(event, dependencias, issuer);
    } catch (erro) {
      if (erro instanceof ErroHttp) {
        resposta = problema(erro.status, erro.titulo, erro.detalhe);
      } else {
        log('ERROR', { mensagem: 'falha inesperada', rota: event.routeKey, correlationId, erro: erro.message });
        resposta = problema(500, 'Erro interno', 'Não foi possível processar a requisição.');
      }
    }
    log('INFO', {
      mensagem: 'requisicao', rota: event.routeKey, status: resposta.statusCode, correlationId, duracaoMs: Date.now() - inicio,
    });
    return resposta;
  };
}

let dependenciasDeProducao;

async function carregarDependenciasDeProducao() {
  if (!dependenciasDeProducao) {
    dependenciasDeProducao = (async () => {
      const { jwtPrivateKey, dbPassword } = await lerSegredos(process.env.SECRET_ID);
      const pool = criarPool({
        host: process.env.DB_HOST,
        database: process.env.DB_NAME,
        user: process.env.DB_USER,
        password: dbPassword,
        ssl: process.env.DB_SSL === 'false' ? false : { rejectUnauthorized: false },
      });
      const tokens = await criarTokens(jwtPrivateKey);
      const autenticacao = criarAutenticacao({ repositorio: criarRepositorio(pool), senhas, tokens });
      return { autenticacao, tokens };
    })().catch((erro) => {
      dependenciasDeProducao = undefined;
      throw erro;
    });
  }
  return dependenciasDeProducao;
}

export const handler = criarHandler(carregarDependenciasDeProducao);
```

- [ ] **Step 5: Rodar e ver passar**

Run: `npm test`
Expected: PASS em todos os testes unitários.

- [ ] **Step 6: Script de empacotamento**

`scripts/empacotar.sh`:
```sh
#!/bin/sh
set -eu
rm -rf dist
mkdir -p dist/pacote
cp -R src package.json package-lock.json dist/pacote/
cd dist/pacote
npm ci --omit=dev --ignore-scripts
zip -qr ../lambda.zip src node_modules package.json
cd ..
rm -rf pacote
ls -lh lambda.zip
```

Run: `npm run empacotar && unzip -l dist/lambda.zip | grep -E "src/handler.js|package.json$|node_modules/jose/package.json"`
Expected: as três entradas listadas.

- [ ] **Step 7: Commit**

```bash
git add src/segredos.js src/handler.js test/unit/handler.test.js scripts/empacotar.sh
git commit -m "feat: add lambda http handler with discovery and jwks routes"
```

---

### Task 7: Terraform da Lambda e do API Gateway

**Files:**
- Create: `terraform/versions.tf`, `terraform/variables.tf`, `terraform/rede.tf`, `terraform/segredos.tf`, `terraform/lambda.tf`, `terraform/api_gateway.tf`, `terraform/outputs.tf`, `terraform/terraform.tfvars.example`

**Interfaces:**
- Consumes: `dist/lambda.zip` (Task 6), handler `src/handler.handler`, variáveis `DB_HOST`/`DB_NAME`/`DB_USER`/`SECRET_ID` (Task 6), NLB interno com tag `kubernetes.io/service-name = oficina/oficina-api` (plano irmão, Task 7).
- Produces: output `api_gateway_url` (sem barra final), consumido pelo secret `APP_PUBLIC_BASE_URL` da API.

- [ ] **Step 1: `terraform/versions.tf`**

```hcl
terraform {
  required_version = ">= 1.5"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.60"
    }

    # Usado so para dar margem de propagacao entre publicar a rota do discovery
    # e criar o authorizer (ver api_gateway.tf).
    time = {
      source  = "hashicorp/time"
      version = "~> 0.11"
    }
  }

  backend "s3" {
    bucket         = "fiap-15soat-oficina-tfstate"
    key            = "lambda-auth/terraform.tfstate"
    region         = "us-east-1"
    dynamodb_table = "fiap-15soat-oficina-tflock"
    encrypt        = true
  }
}

provider "aws" {
  region = var.aws_region
}
```

- [ ] **Step 2: `terraform/variables.tf`**

```hcl
variable "aws_region" {
  description = "Regiao AWS"
  type        = string
  default     = "us-east-1"
}

# ATENCAO: precisa ser identico em fiap-15soat-oficina-infra-k8s e fiap-15soat-oficina-infra-db.
variable "project_name" {
  description = "Prefixo de nome dos recursos. Contrato compartilhado entre os repositorios."
  type        = string
  default     = "oficina"
}

variable "db_name" {
  description = "Nome do banco no RDS"
  type        = string
  default     = "oficina"
}

variable "db_username" {
  description = "Usuario do banco usado pela Lambda"
  type        = string
  default     = "oficina"
}

variable "db_password" {
  description = "Senha do banco (vai para o Secrets Manager, nunca para variavel de ambiente)"
  type        = string
  sensitive   = true
}

variable "jwt_private_key" {
  description = "Chave privada RSA em PEM - o mesmo par usado pela API"
  type        = string
  sensitive   = true
}

variable "pacote_lambda" {
  description = "Caminho do zip gerado por npm run empacotar"
  type        = string
  default     = "../dist/lambda.zip"
}
```

- [ ] **Step 3: `terraform/rede.tf`**

```hcl
data "aws_caller_identity" "current" {}

# Descoberta por convencao de nomes e tags, sem terraform_remote_state - mesmo padrao do infra-db.
data "aws_vpc" "main" {
  filter {
    name   = "tag:Name"
    values = ["${var.project_name}-vpc"]
  }
}

data "aws_subnets" "private" {
  filter {
    name   = "vpc-id"
    values = [data.aws_vpc.main.id]
  }

  filter {
    name   = "tag:Name"
    values = ["${var.project_name}-vpc-private-*"]
  }
}

data "aws_db_instance" "oficina" {
  db_instance_identifier = "${var.project_name}-db"
}

# Criado pelo Service do Kubernetes (k8s/service.yaml na API), nao por Terraform:
# este repositorio so pode ser aplicado depois do primeiro deploy da API.
data "aws_lb" "api" {
  tags = {
    "kubernetes.io/service-name" = "oficina/oficina-api"
  }
}

data "aws_lb_listener" "api" {
  load_balancer_arn = data.aws_lb.api.arn
  port              = 80
}
```

- [ ] **Step 4: `terraform/segredos.tf`**

```hcl
resource "aws_secretsmanager_secret" "auth" {
  name = "${var.project_name}/auth"
  # Sem janela de recuperacao: no Academy o ambiente e destruido e recriado com frequencia,
  # e um segredo "agendado para exclusao" bloquearia a recriacao com o mesmo nome.
  recovery_window_in_days = 0
}

resource "aws_secretsmanager_secret_version" "auth" {
  secret_id = aws_secretsmanager_secret.auth.id
  secret_string = jsonencode({
    jwtPrivateKey = var.jwt_private_key
    dbPassword    = var.db_password
  })
}
```

- [ ] **Step 5: `terraform/lambda.tf`**

```hcl
locals {
  lab_role_arn = "arn:aws:iam::${data.aws_caller_identity.current.account_id}:role/LabRole"
}

resource "aws_security_group" "lambda" {
  name        = "${var.project_name}-auth-lambda-sg"
  description = "Lambda de autenticacao: so trafego de saida (RDS e Secrets Manager via NAT)"
  vpc_id      = data.aws_vpc.main.id

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

resource "aws_cloudwatch_log_group" "lambda" {
  name              = "/aws/lambda/${var.project_name}-auth"
  retention_in_days = 7
}

resource "aws_lambda_function" "auth" {
  function_name    = "${var.project_name}-auth"
  role             = local.lab_role_arn
  runtime          = "nodejs22.x"
  handler          = "src/handler.handler"
  filename         = var.pacote_lambda
  source_code_hash = filebase64sha256(var.pacote_lambda)
  memory_size      = 512
  timeout          = 10

  vpc_config {
    subnet_ids         = data.aws_subnets.private.ids
    security_group_ids = [aws_security_group.lambda.id]
  }

  environment {
    variables = {
      DB_HOST   = data.aws_db_instance.oficina.address
      DB_NAME   = var.db_name
      DB_USER   = var.db_username
      SECRET_ID = aws_secretsmanager_secret.auth.arn
    }
  }

  depends_on = [aws_cloudwatch_log_group.lambda, aws_secretsmanager_secret_version.auth]
}

resource "aws_lambda_permission" "api_gateway" {
  statement_id  = "AllowApiGatewayInvoke"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.auth.function_name
  principal     = "apigateway.amazonaws.com"
  source_arn    = "${aws_apigatewayv2_api.oficina.execution_arn}/*/*"
}
```

- [ ] **Step 6: `terraform/api_gateway.tf`**

```hcl
locals {
  rotas_autenticacao = [
    "POST /auth/funcionario",
    "POST /auth/cliente",
    "POST /auth/cliente/primeiro-acesso",
  ]

  rotas_publicas_api = [
    "GET /aprovacao-os",
    "GET /actuator/health",
    "GET /swagger-ui.html",
    "GET /swagger-ui/{proxy+}",
    "GET /v3/api-docs",
    "GET /v3/api-docs/{proxy+}",
  ]
}

resource "aws_apigatewayv2_api" "oficina" {
  name          = "${var.project_name}-api"
  protocol_type = "HTTP"
}

resource "aws_apigatewayv2_integration" "lambda" {
  api_id                 = aws_apigatewayv2_api.oficina.id
  integration_type       = "AWS_PROXY"
  integration_uri        = aws_lambda_function.auth.invoke_arn
  payload_format_version = "2.0"
}

resource "aws_security_group" "vpc_link" {
  name        = "${var.project_name}-api-vpc-link-sg"
  description = "ENIs do VPC Link do API Gateway ate o NLB interno da API"
  vpc_id      = data.aws_vpc.main.id

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = [data.aws_vpc.main.cidr_block]
  }
}

resource "aws_apigatewayv2_vpc_link" "api" {
  name               = "${var.project_name}-api"
  subnet_ids         = data.aws_subnets.private.ids
  security_group_ids = [aws_security_group.vpc_link.id]
}

resource "aws_apigatewayv2_integration" "api" {
  api_id             = aws_apigatewayv2_api.oficina.id
  integration_type   = "HTTP_PROXY"
  integration_method = "ANY"
  integration_uri    = data.aws_lb_listener.api.arn
  connection_type    = "VPC_LINK"
  connection_id      = aws_apigatewayv2_vpc_link.api.id
}

# O issuer e a propria URL do Gateway: a Lambda publica discovery e JWKS nela.
#
# A AWS busca o /.well-known/openid-configuration NA CRIACAO do authorizer, nao so a cada
# requisicao, e a rota leva alguns segundos para comecar a responder depois que o stage publica.
# No primeiro apply desta infraestrutura o authorizer foi criado 1 segundo apos o stage e falhou
# com "Invalid issuer ... must have a valid discovery endpoint". Por isso a espera abaixo, alem
# das dependencias de rota, stage e permissao de invocacao da Lambda.
resource "time_sleep" "discovery_publicado" {
  depends_on = [
    aws_apigatewayv2_route.well_known,
    aws_apigatewayv2_stage.default,
    aws_lambda_permission.api_gateway,
  ]

  create_duration = "60s"
}

resource "aws_apigatewayv2_authorizer" "jwt" {
  api_id           = aws_apigatewayv2_api.oficina.id
  name             = "${var.project_name}-jwt"
  authorizer_type  = "JWT"
  identity_sources = ["$request.header.Authorization"]

  jwt_configuration {
    issuer   = aws_apigatewayv2_api.oficina.api_endpoint
    audience = ["oficina-api"]
  }

  depends_on = [time_sleep.discovery_publicado]
}

resource "aws_apigatewayv2_route" "autenticacao" {
  for_each  = toset(local.rotas_autenticacao)
  api_id    = aws_apigatewayv2_api.oficina.id
  route_key = each.value
  target    = "integrations/${aws_apigatewayv2_integration.lambda.id}"
}

resource "aws_apigatewayv2_route" "well_known" {
  api_id    = aws_apigatewayv2_api.oficina.id
  route_key = "GET /.well-known/{proxy+}"
  target    = "integrations/${aws_apigatewayv2_integration.lambda.id}"
}

resource "aws_apigatewayv2_route" "publicas_api" {
  for_each  = toset(local.rotas_publicas_api)
  api_id    = aws_apigatewayv2_api.oficina.id
  route_key = each.value
  target    = "integrations/${aws_apigatewayv2_integration.api.id}"
}

# /actuator/prometheus propositalmente sem rota: o coletor do New Relic le de dentro do cluster.
resource "aws_apigatewayv2_route" "api_protegida" {
  api_id             = aws_apigatewayv2_api.oficina.id
  route_key          = "ANY /api/{proxy+}"
  target             = "integrations/${aws_apigatewayv2_integration.api.id}"
  authorization_type = "JWT"
  authorizer_id      = aws_apigatewayv2_authorizer.jwt.id
}

resource "aws_cloudwatch_log_group" "api" {
  name              = "/aws/apigateway/${var.project_name}-api"
  retention_in_days = 7
}

resource "aws_apigatewayv2_stage" "default" {
  api_id      = aws_apigatewayv2_api.oficina.id
  name        = "$default"
  auto_deploy = true

  default_route_settings {
    throttling_rate_limit  = 50
    throttling_burst_limit = 100
  }

  dynamic "route_settings" {
    for_each = toset(local.rotas_autenticacao)
    content {
      route_key              = route_settings.value
      throttling_rate_limit  = 5
      throttling_burst_limit = 10
    }
  }

  access_log_settings {
    destination_arn = aws_cloudwatch_log_group.api.arn
    format = jsonencode({
      requestId       = "$context.requestId"
      rota            = "$context.routeKey"
      status          = "$context.status"
      latenciaMs      = "$context.responseLatency"
      ip              = "$context.identity.sourceIp"
      erroAutorizador = "$context.authorizer.error"
    })
  }

  depends_on = [aws_apigatewayv2_route.autenticacao]
}
```

- [ ] **Step 7: `terraform/outputs.tf` e exemplo de variáveis**

`terraform/outputs.tf`:
```hcl
output "api_gateway_url" {
  description = "URL publica do Gateway. Usar no secret APP_PUBLIC_BASE_URL da API (emissor dos tokens e links de aprovacao)."
  value       = aws_apigatewayv2_api.oficina.api_endpoint
}

output "lambda_function_name" {
  value = aws_lambda_function.auth.function_name
}
```

`terraform/terraform.tfvars.example`:
```hcl
# Copie para terraform.tfvars (gitignorado). Em CI os valores vem de TF_VAR_* .
db_password     = "mesma-senha-do-infra-db"
jwt_private_key = <<-EOT
-----BEGIN PRIVATE KEY-----
...
-----END PRIVATE KEY-----
EOT
```

- [ ] **Step 8: Formatar e validar sem backend**

Run:
```bash
cd terraform
terraform fmt -recursive
terraform init -backend=false
terraform validate
cd ..
```
Expected: `Success! The configuration is valid.`

- [ ] **Step 9: Commit**

```bash
git add terraform/*.tf terraform/terraform.tfvars.example
git commit -m "feat(infra): provision auth lambda and http api gateway"
```

---

### Task 8: Pipeline de CI/CD e README

**Files:**
- Create: `.github/workflows/ci-cd.yml`
- Create: `README.md`

**Interfaces:**
- Consumes: `npm test`, `npm run test:integracao`, `npm run empacotar` (Tasks 2–6); Terraform em `terraform/` (Task 7).
- Produces: GitHub Secrets exigidos: `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`, `AWS_SESSION_TOKEN`, `DB_PASSWORD`, `JWT_PRIVATE_KEY`.

- [ ] **Step 1: `.github/workflows/ci-cd.yml`**

```yaml
name: CI/CD

on:
  push:
    branches: [main, master]
  pull_request:
    branches: [main, master]
  workflow_dispatch:

env:
  AWS_REGION: us-east-1
  TF_VERSION: "1.9.8"
  TF_IN_AUTOMATION: "true"
  TF_INPUT: "false"

# Duas execucoes simultaneas disputariam o lock do DynamoDB e uma falharia.
concurrency:
  group: lambda-auth-${{ github.ref }}
  cancel-in-progress: false

jobs:
  testes:
    name: Testes
    runs-on: ubuntu-latest
    services:
      postgres:
        image: postgres:16
        env:
          POSTGRES_PASSWORD: postgres
        ports:
          - 5433:5432
        options: >-
          --health-cmd "pg_isready -U postgres"
          --health-interval 5s
          --health-timeout 5s
          --health-retries 10
    steps:
      - uses: actions/checkout@v4

      - uses: actions/setup-node@v4
        with:
          node-version-file: .nvmrc
          cache: npm

      - run: npm ci

      - name: Testes unitarios
        run: npm test

      - name: Testes de integracao (Postgres real)
        run: npm run test:integracao

  validacao-terraform:
    name: Formato e validacao do Terraform
    runs-on: ubuntu-latest
    defaults:
      run:
        working-directory: terraform
    steps:
      - uses: actions/checkout@v4

      - uses: hashicorp/setup-terraform@v3
        with:
          terraform_version: ${{ env.TF_VERSION }}
          terraform_wrapper: false

      - run: terraform fmt -check -recursive -diff

      # Sem backend: roda mesmo com as credenciais do Academy expiradas.
      - run: |
          terraform init -backend=false
          terraform validate

  plano:
    name: Plano
    needs: [testes, validacao-terraform]
    if: github.event_name == 'pull_request'
    runs-on: ubuntu-latest
    permissions:
      contents: read
      pull-requests: write
    steps:
      - uses: actions/checkout@v4

      - uses: actions/setup-node@v4
        with:
          node-version-file: .nvmrc
          cache: npm

      - name: Empacotar a Lambda
        run: npm run empacotar

      - uses: hashicorp/setup-terraform@v3
        with:
          terraform_version: ${{ env.TF_VERSION }}
          terraform_wrapper: false

      - uses: aws-actions/configure-aws-credentials@v4
        with:
          aws-access-key-id: ${{ secrets.AWS_ACCESS_KEY_ID }}
          aws-secret-access-key: ${{ secrets.AWS_SECRET_ACCESS_KEY }}
          aws-session-token: ${{ secrets.AWS_SESSION_TOKEN }}
          aws-region: ${{ env.AWS_REGION }}

      - name: Plan
        id: plan
        working-directory: terraform
        env:
          TF_VAR_db_password: ${{ secrets.DB_PASSWORD }}
          TF_VAR_jwt_private_key: ${{ secrets.JWT_PRIVATE_KEY }}
        run: |
          terraform init -lock-timeout=5m
          terraform plan -no-color -lock-timeout=5m | tee plan.txt
          echo "resumo=$(grep -E '^(Plan:|No changes)' plan.txt | head -1)" >> "$GITHUB_OUTPUT"

      - name: Comentar o plano no PR
        uses: actions/github-script@v7
        with:
          script: |
            const fs = require('fs');
            let plano = fs.readFileSync('terraform/plan.txt', 'utf8');
            const limite = 60000;
            if (plano.length > limite) {
              plano = plano.slice(0, limite) + '\n\n... saida truncada, ver o log completo do job.';
            }
            await github.rest.issues.createComment({
              issue_number: context.issue.number,
              owner: context.repo.owner,
              repo: context.repo.repo,
              body: `### Terraform plan — \`lambda-auth\`\n\n**${{ steps.plan.outputs.resumo }}**\n\n<details><summary>Plano completo</summary>\n\n\`\`\`terraform\n${plano}\n\`\`\`\n\n</details>`
            });

  deploy:
    name: Deploy
    needs: [testes, validacao-terraform]
    if: github.event_name != 'pull_request'
    runs-on: ubuntu-latest
    environment: producao
    steps:
      - uses: actions/checkout@v4

      - uses: actions/setup-node@v4
        with:
          node-version-file: .nvmrc
          cache: npm

      - name: Empacotar a Lambda
        run: npm run empacotar

      - uses: hashicorp/setup-terraform@v3
        with:
          terraform_version: ${{ env.TF_VERSION }}
          terraform_wrapper: false

      - uses: aws-actions/configure-aws-credentials@v4
        with:
          aws-access-key-id: ${{ secrets.AWS_ACCESS_KEY_ID }}
          aws-secret-access-key: ${{ secrets.AWS_SECRET_ACCESS_KEY }}
          aws-session-token: ${{ secrets.AWS_SESSION_TOKEN }}
          aws-region: ${{ env.AWS_REGION }}

      - name: Apply
        working-directory: terraform
        env:
          TF_VAR_db_password: ${{ secrets.DB_PASSWORD }}
          TF_VAR_jwt_private_key: ${{ secrets.JWT_PRIVATE_KEY }}
        run: |
          terraform init -lock-timeout=5m
          terraform apply -auto-approve -lock-timeout=5m

      - name: Smoke test
        working-directory: terraform
        run: |
          URL=$(terraform output -raw api_gateway_url)
          curl -fsS "$URL/.well-known/jwks.json" | jq -e '.keys | length == 1'
          status=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$URL/auth/funcionario" \
            -H 'Content-Type: application/json' \
            -d '{"username":"smoke-test@oficina.invalid","senha":"senha-incorreta"}')
          echo "POST /auth/funcionario com credencial invalida -> $status"
          test "$status" = "401"

      - name: Publicar outputs no resumo do job
        working-directory: terraform
        run: |
          {
            echo "### Outputs"
            echo ""
            echo '| Output | Valor |'
            echo '|---|---|'
            terraform output -json | jq -r 'to_entries[] | "| \(.key) | `\(.value.value)` |"'
          } >> "$GITHUB_STEP_SUMMARY"
```

- [ ] **Step 2: Validar a sintaxe do workflow**

Run: `python3 -c "import yaml; yaml.safe_load(open('.github/workflows/ci-cd.yml')); print('ok')"`
Expected: `ok`.

- [ ] **Step 3: `README.md`**

````markdown
# fiap-15soat-oficina-lambda-auth

Autenticação da Oficina Mecânica (Tech Challenge Fase 3): uma Function Serverless que emite os
tokens de sessão e o API Gateway que é a porta de entrada única do sistema.

Um de quatro repositórios:
[`api`](https://github.com/KauaAlmeidaSilveira/fiap-15soat-oficina-api) ·
[`infra-k8s`](https://github.com/JulioNCavalcanti/fiap-15soat-oficina-infra-k8s) ·
[`infra-db`](https://github.com/JulioNCavalcanti/fiap-15soat-oficina-infra-db) ·
**`lambda-auth`**

## Arquitetura

```mermaid
flowchart LR
    U[Cliente / Funcionário] -->|HTTPS| GW[API Gateway HTTP API]
    GW -->|/auth/*, /.well-known/*| L[Lambda oficina-auth<br/>Node 22, subnets privadas]
    GW -->|/api/** com JWT authorizer| VL[VPC Link]
    VL --> NLB[NLB interno] --> API[Pods da API no EKS]
    L --> SM[Secrets Manager<br/>chave RSA + senha do banco]
    L --> RDS[(RDS PostgreSQL)]
    API --> RDS
```

## Autenticação

CPF é praticamente um dado público: identifica, mas não autentica. Por isso o cliente entra com
**CPF + senha**, e a senha é criada no primeiro acesso, conferindo o e-mail do cadastro.

| Rota | Corpo | Resposta |
|---|---|---|
| `POST /auth/funcionario` | `{"username", "senha"}` | `200 {token, expiresIn: 28800}` · 401 |
| `POST /auth/cliente` | `{"cpf", "senha"}` | `200 {token, expiresIn: 3600}` · 400 CPF inválido/CNPJ · 401 · 403 inativo |
| `POST /auth/cliente/primeiro-acesso` | `{"cpf", "email", "senha"}` | `201 {token, expiresIn: 3600}` · 400 · 422 |
| `GET /.well-known/openid-configuration` | — | discovery usado pelo JWT authorizer |
| `GET /.well-known/jwks.json` | — | chave pública |

Tokens: RS256, `aud = oficina-api`, `iss` = URL do Gateway. Funcionário recebe as roles do banco;
cliente recebe `roles = ["CLIENTE"]` e `sub` = id do cliente. As rotas `/auth/*` têm throttling de
5 req/s (burst 10).

Erros seguem RFC 7807 (`application/problem+json`). Cliente inexistente, sem senha ou com senha
errada recebem a mesma resposta 401; o status `INATIVO` só é revelado depois que a senha confere.

## Tecnologias

Node.js 22 · `pg` · `bcryptjs` · `jose` · AWS Lambda · API Gateway HTTP API · Secrets Manager ·
Terraform · GitHub Actions

## Desenvolvimento

```bash
nvm use                      # Node 22
npm ci
npm test                     # unitários

docker run -d --name oficina-auth-pg -e POSTGRES_PASSWORD=postgres -p 5433:5432 postgres:16
npm run test:integracao      # repositório contra Postgres real

# Token para chamar a API rodando em localhost:8080, sem Gateway
npm run token:local -- --sub kaua@gmail.com --roles ADMIN
```

## Deploy

Pipeline em `.github/workflows/ci-cd.yml`:

| Evento | O que roda |
|---|---|
| Pull request | testes unitários e de integração, `terraform fmt`/`validate`/`plan` com o plano comentado no PR |
| Push em `main` | testes, empacotamento, `terraform apply` no Environment `producao` e smoke test |

Secrets: `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`, `AWS_SESSION_TOKEN` (painel do Academy),
`DB_PASSWORD` (a mesma do `infra-db`) e `JWT_PRIVATE_KEY` (a mesma da API).

### Ordem de subida (primeira vez)

1. `infra-k8s` → `infra-db`
2. Deploy da API: cria o NLB interno e a coluna `cliente.senha_hash`
3. Este repositório: encontra o NLB por tag e publica o Gateway
4. Copiar o output `api_gateway_url` para o secret `APP_PUBLIC_BASE_URL` da API
5. Novo deploy da API (passa a aceitar tokens com esse emissor)

### Ordem de destruição

Este repositório **antes** de remover o `Service` da API (o VPC Link aponta para o listener do NLB),
e antes de `infra-db` e `infra-k8s`.

## Observações sobre o AWS Academy

A Lambda usa a `LabRole` pré-existente (`iam:CreateRole` é bloqueado). O segredo é criado sem
janela de recuperação para permitir destruir e recriar o ambiente com o mesmo nome. A Lambda alcança
o Secrets Manager pelo NAT gateway da VPC, sem VPC endpoint.
````

- [ ] **Step 4: Verificação local completa**

Run: `npm ci && npm test && npm run test:integracao && npm run empacotar && (cd terraform && terraform fmt -check -recursive && terraform validate)`
Expected: tudo verde.

- [ ] **Step 5: Commit**

```bash
git add .github/workflows/ci-cd.yml README.md
git commit -m "ci: add test, plan and deploy pipeline"
```

- [ ] **Step 6: Não fazer push sem o usuário**

O primeiro push para `master` dispara `terraform apply` e exige a API já migrada (Task 9). Confirme com o usuário antes de `git push`.

---

### Task 9: Primeira subida ponta a ponta (manual, com o usuário)

Exige credenciais do Academy, os dois repositórios de infraestrutura no ar e a branch `feat/api-gateway-auth` da API mesclada. **Cada passo que cria recurso ou altera secret deve ser confirmado com o usuário.**

- [ ] **Step 1: Deploy da API migrada**

Mesclar a branch da API e aguardar o workflow. Depois:

```bash
kubectl get svc oficina-api -n oficina -o jsonpath='{.metadata.annotations}'; echo
aws elbv2 describe-load-balancers --query "LoadBalancers[?Scheme=='internal'].[LoadBalancerName,Type]" --output table
```
Expected: anotação `aws-load-balancer-type: nlb` e um NLB `internal` listado.

- [ ] **Step 2: Conferir a tag que o Terraform procura**

```bash
ARN=$(aws elbv2 describe-load-balancers --query "LoadBalancers[?Scheme=='internal'&&Type=='network'].LoadBalancerArn | [0]" --output text)
aws elbv2 describe-tags --resource-arns "$ARN" --query "TagDescriptions[0].Tags[?Key=='kubernetes.io/service-name']"
```
Expected: `Value: oficina/oficina-api`. Se a tag tiver outro formato, ajuste `data "aws_lb" "api"` em `terraform/rede.tf` antes de seguir.

- [ ] **Step 3: Conferir a coluna criada pelo Hibernate**

```bash
kubectl run psql-check -n oficina --rm -it --restart=Never --image=postgres:16 \
  --env PGPASSWORD='<DB_PASSWORD>' -- psql -h <RDS_ENDPOINT> -U oficina -d oficina -c '\d cliente'
```
Expected: coluna `senha_hash character varying(60)`. Compare também os tipos com `test/integracao/schema.sql`.

- [ ] **Step 4: Secrets e push do `lambda-auth`**

Cadastrar no repositório `fiap-15soat-oficina-lambda-auth` os cinco secrets e o Environment `producao`. Com aprovação do usuário:

```bash
git push
```
Expected: workflow verde, com o smoke test passando e o `api_gateway_url` no resumo do job.

- [ ] **Step 5: Religar a API ao emissor do Gateway**

Atualizar o secret `APP_PUBLIC_BASE_URL` do repositório da API com o `api_gateway_url` e disparar o workflow da API (`workflow_dispatch`).

- [ ] **Step 6: Roteiro de validação (também serve de ensaio do vídeo)**

```bash
GW=<api_gateway_url>

# 1. Funcionário
TOKEN_ADMIN=$(curl -s -X POST $GW/auth/funcionario -H 'Content-Type: application/json' \
  -d '{"username":"kaua@gmail.com","senha":"Admin@123"}' | jq -r .token)
curl -s -o /dev/null -w '%{http_code}\n' $GW/api/clientes -H "Authorization: Bearer $TOKEN_ADMIN"   # 200

# 2. Cliente: primeiro acesso e login
curl -s -X POST $GW/auth/cliente/primeiro-acesso -H 'Content-Type: application/json' \
  -d '{"cpf":"52998224725","email":"joao@email.com","senha":"Cliente@123"}' -w '\n%{http_code}\n'  # 201
TOKEN_CLIENTE=$(curl -s -X POST $GW/auth/cliente -H 'Content-Type: application/json' \
  -d '{"cpf":"529.982.247-25","senha":"Cliente@123"}' | jq -r .token)

# 3. Autorização
curl -s -o /dev/null -w '%{http_code}\n' $GW/api/minhas-ordens-servico -H "Authorization: Bearer $TOKEN_CLIENTE"  # 200
curl -s -o /dev/null -w '%{http_code}\n' $GW/api/clientes -H "Authorization: Bearer $TOKEN_CLIENTE"               # 403 (API)
curl -s -o /dev/null -w '%{http_code}\n' $GW/api/clientes                                                          # 401 (Gateway)
curl -s -o /dev/null -w '%{http_code}\n' $GW/actuator/prometheus                                                   # 404 (sem rota)

# 4. Erros de autenticação
curl -s -o /dev/null -w '%{http_code}\n' -X POST $GW/auth/cliente -H 'Content-Type: application/json' \
  -d '{"cpf":"12345678901","senha":"x"}'   # 400
curl -s -o /dev/null -w '%{http_code}\n' -X POST $GW/auth/cliente -H 'Content-Type: application/json' \
  -d '{"cpf":"71498053297","senha":"x"}'   # 401
```
Expected: os códigos comentados em cada linha. O DataLoader só roda com banco vazio: se o banco de produção já tinha dados antes da troca do CPF do João, crie um cliente de teste pela API com o token de ADMIN e use o CPF dele.

- [ ] **Step 7: Registrar no checklist**

Atualizar os itens de "Autenticação e API Gateway" em `CHECKLIST.md` do repositório da API, com os hashes dos commits dos dois repositórios.

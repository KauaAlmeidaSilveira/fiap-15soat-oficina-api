# Manifestos Kubernetes — Oficina Mecânica API

Manifestos para deploy da aplicação em um cluster Kubernetes (EKS na AWS). O banco de dados
**não** roda no cluster — é uma instância RDS PostgreSQL externa, provisionada pelo repositório `fiap-15soat-oficina-infra-db`.

## Pré-requisitos

1. Cluster Kubernetes acessível via `kubectl` (`kubectl config current-context` apontando pro cluster certo).
2. **metrics-server** instalado no cluster — obrigatório para o HPA ler CPU/memória dos pods.
   No EKS isso é um addon gerenciado (`aws eks create-addon --addon-name metrics-server ...`),
   provisionado na fase de Terraform.
3. O node group do EKS precisa de permissão de leitura no ECR (policy
   `AmazonEC2ContainerRegistryReadOnly` na IAM role do node) — assim os pods puxam a imagem sem
   precisar de `imagePullSecrets`. Também provisionado na fase de Terraform.
4. Imagem já publicada no ECR (via pipeline de CI/CD) no path referenciado em `deployment.yaml`.
5. RDS já provisionado (via Terraform), com o endpoint disponível para o `configmap.yaml`.

## Gerando o Secret (não versionado)

O `secret.yaml.example` é só um template de referência — não contém segredo real e **não** deve
ser renomeado para `secret.yaml` e commitado com dados verdadeiros (`k8s/secret.yaml` está no
`.gitignore`).

Gere as chaves JWT (se ainda não tiver um par — reaproveite o mesmo par entre todas as réplicas,
nunca gere um por pod):

```bash
openssl genrsa -out /tmp/jwt.private.key 2048
openssl rsa -in /tmp/jwt.private.key -pubout -out /tmp/jwt.public.key
```

Crie o Secret diretamente no cluster (sem passar pelo arquivo YAML):

```bash
kubectl create secret generic oficina-api-secrets \
  --namespace oficina \
  --from-literal=DB_PASSWORD='<senha-real-do-rds>' \
  --from-literal=GMAIL_SMTP_USERNAME='<endereco-gmail-remetente>' \
  --from-literal=GMAIL_SMTP_PASSWORD='<app-password-do-gmail>' \
  --from-file=jwt.private.key=/tmp/jwt.private.key \
  --from-file=jwt.public.key=/tmp/jwt.public.key
```

> `GMAIL_SMTP_USERNAME`/`GMAIL_SMTP_PASSWORD` alimentam o envio real do e-mail de aprovação de
> orçamento (`NotificacaoAprovacaoSmtpService`, ativo só no profile `default`). A senha é uma
> App Password do Gmail (Conta Google → Segurança → Verificação em duas etapas → Senhas de app),
> nunca a senha normal da conta.

> Isso precisa ser feito **antes** de aplicar o `deployment.yaml`, já que ele referencia o Secret.

## Aplicando os manifestos

Antes de aplicar, edite `configmap.yaml` (`DB_HOST`) e `deployment.yaml` (`image:`) substituindo os
placeholders `<RDS_ENDPOINT>` e `<ECR_URI>` pelos valores reais (saída do Terraform / CI).

```bash
kubectl apply -f k8s/namespace.yaml
# criar o secret (ver seção acima) antes de seguir
kubectl apply -f k8s/configmap.yaml
kubectl apply -f k8s/deployment.yaml
kubectl apply -f k8s/service.yaml
kubectl apply -f k8s/hpa.yaml
```

Ou tudo de uma vez, contanto que o Secret já exista no namespace:

```bash
kubectl apply -f k8s/
```

## Verificando

```bash
kubectl get pods -n oficina
kubectl get svc oficina-api -n oficina        # EXTERNAL-IP do LoadBalancer
kubectl get hpa oficina-api-hpa -n oficina -w  # acompanhar réplicas/CPU em tempo real
```

## Por que as chaves JWT vão no Secret e não são geradas no container

O `docker-entrypoint.sh` da imagem gera um par de chaves RSA automaticamente **apenas se
`/app/keys/jwt.private.key` não existir** — isso é um fallback para quem roda a imagem localmente
sem Kubernetes. No cluster, o volume montado a partir do Secret `oficina-api-secrets` já entrega
esse arquivo em todos os pods, com o **mesmo** par de chaves. Isso é obrigatório: se cada réplica
gerasse sua própria chave, um JWT emitido por um pod seria rejeitado pelos demais atrás do Service,
causando 401 aleatórios assim que o HPA escalar para mais de uma réplica.

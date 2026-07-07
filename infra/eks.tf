module "eks" {
  source  = "terraform-aws-modules/eks/aws"
  version = "~> 20.31"

  cluster_name    = "${var.project_name}-eks"
  cluster_version = var.eks_cluster_version

  cluster_endpoint_public_access = true

  vpc_id     = module.vpc.vpc_id
  subnet_ids = module.vpc.private_subnets

  # Addons gerenciados pela AWS (vpc-cni, coredns, kube-proxy). O metrics-server
  # (necessário para o HPA em k8s/hpa.yaml) NÃO é um addon nativo do EKS — é
  # instalado à parte via kubectl/Helm, documentado no README desta pasta.
  cluster_addons = {
    coredns = {
      most_recent = true
    }
    kube-proxy = {
      most_recent = true
    }
    vpc-cni = {
      most_recent = true
    }
  }

  eks_managed_node_groups = {
    default = {
      instance_types = [var.node_instance_type]
      desired_size   = var.node_desired_size
      min_size       = var.node_min_size
      max_size       = var.node_max_size

      # Permite que o kubelet dos nodes autentique no ECR sem imagePullSecrets
      # (ver k8s/README.md) — a policy abaixo é anexada à role do node group.
      iam_role_additional_policies = {
        AmazonEC2ContainerRegistryReadOnly = "arn:aws:iam::aws:policy/AmazonEC2ContainerRegistryReadOnly"
      }
    }
  }

  # Dá ao usuário/role que rodou o `terraform apply` acesso de admin ao cluster
  # via aws-auth automaticamente (necessário para `aws eks update-kubeconfig` funcionar).
  enable_cluster_creator_admin_permissions = true
}

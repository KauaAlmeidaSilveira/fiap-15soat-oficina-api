locals {
  lab_role_arn = "arn:aws:iam::111119316346:role/LabRole"
}

module "eks" {
  source  = "terraform-aws-modules/eks/aws"
  version = "~> 20.31"

  cluster_name    = "${var.project_name}-eks"
  cluster_version = var.eks_cluster_version

  cluster_endpoint_public_access = true

  vpc_id     = module.vpc.vpc_id
  subnet_ids = module.vpc.private_subnets

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

  # AWS Academy não permite iam:CreateRole — reutiliza a LabRole pré-existente.
  create_iam_role = false
  iam_role_arn    = local.lab_role_arn

  eks_managed_node_groups = {
    default = {
      instance_types = [var.node_instance_type]
      desired_size   = var.node_desired_size
      min_size       = var.node_min_size
      max_size       = var.node_max_size

      # AWS Academy: reutiliza LabRole no node group também.
      create_iam_role = false
      iam_role_arn    = local.lab_role_arn
    }
  }

  # AWS Academy bloqueia iam:GetRole, iam:CreateRole e iam:CreateOpenIDConnectProvider.
  enable_cluster_creator_admin_permissions = false
  create_kms_key                           = false
  cluster_encryption_config               = {}
  enable_irsa                              = false
}

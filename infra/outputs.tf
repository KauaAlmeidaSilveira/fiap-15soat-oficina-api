output "eks_cluster_name" {
  description = "Nome do cluster EKS — usar em `aws eks update-kubeconfig --name <valor>`"
  value       = module.eks.cluster_name
}

output "eks_cluster_endpoint" {
  value = module.eks.cluster_endpoint
}

output "ecr_repository_url" {
  description = "URI a usar no campo `image:` de k8s/deployment.yaml e no push do CI/CD"
  value       = aws_ecr_repository.oficina_api.repository_url
}

output "rds_endpoint" {
  description = "Endpoint a usar no `DB_HOST` de k8s/configmap.yaml (sem a porta)"
  value       = aws_db_instance.oficina.address
}

output "rds_port" {
  value = aws_db_instance.oficina.port
}

output "configure_kubectl_command" {
  description = "Comando para configurar o kubectl local apontando para o cluster criado"
  value       = "aws eks update-kubeconfig --region ${var.aws_region} --name ${module.eks.cluster_name}"
}

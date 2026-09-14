terraform {
  required_version = ">= 1.5"

  required_providers {
    newrelic = {
      source  = "newrelic/newrelic"
      version = "~> 3.0"
    }
  }

  # State proprio, separado da infraestrutura AWS: dashboards e alertas nao tem
  # nenhuma dependencia de VPC, EKS ou RDS, e um plan aqui nao deve tocar neles.
  backend "s3" {
    bucket         = "fiap-15soat-oficina-tfstate"
    key            = "observability/terraform.tfstate"
    region         = "us-east-1"
    dynamodb_table = "fiap-15soat-oficina-tflock"
    encrypt        = true
  }
}

provider "newrelic" {
  account_id = var.newrelic_account_id
  api_key    = var.newrelic_api_key
  region     = var.newrelic_region
}

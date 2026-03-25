# SwiftCart — AWS Infrastructure (Terraform)

Provisions the full production AWS stack for SwiftCart using Infrastructure as Code.

## Architecture

```
Internet
   │
   ▼
[ALB] ──────────────── public subnets (2 AZs)
   │
   ▼
[ECS Fargate] ──────── private subnets (2 AZs)
   │         │
   ▼         ▼
[RDS      [ElastiCache
Postgres]  Redis]
```

## Resources Created

| Resource | Type | Purpose |
|---|---|---|
| VPC | `aws_vpc` | Isolated network |
| Public subnets (×2) | `aws_subnet` | ALB, NAT Gateways |
| Private subnets (×2) | `aws_subnet` | ECS, RDS, Redis |
| Internet Gateway | `aws_internet_gateway` | Public internet access |
| NAT Gateways (×2) | `aws_nat_gateway` | Outbound from private subnets |
| ALB | `aws_lb` | Load balancer with health checks |
| ECR Repository | `aws_ecr_repository` | Docker image storage |
| ECS Cluster + Service | `aws_ecs_*` | Fargate container runtime |
| RDS PostgreSQL 16 | `aws_db_instance` | Primary database |
| ElastiCache Redis 7 | `aws_elasticache_cluster` | Caching layer |
| Security Groups (×4) | `aws_security_group` | Least-privilege network rules |
| IAM Roles | `aws_iam_role` | ECS task execution permissions |
| CloudWatch Logs | `aws_cloudwatch_log_group` | Container log aggregation |

## Prerequisites

- [Terraform >= 1.6](https://developer.hashicorp.com/terraform/install)
- [AWS CLI](https://docs.aws.amazon.com/cli/latest/userguide/install-cliv2.html) configured with appropriate credentials
- A built Docker image pushed to ECR (GitHub Actions handles this automatically)

## Deploying

```bash
cd terraform

# 1. Initialise providers
terraform init

# 2. Preview what will be created
terraform plan \
  -var="db_password=YourStrongPassword" \
  -var="jwt_secret=YourJwtSecret"

# 3. Apply (creates all AWS resources)
terraform apply \
  -var="db_password=YourStrongPassword" \
  -var="jwt_secret=YourJwtSecret"
```

Or export sensitive values as environment variables (recommended):

```bash
export TF_VAR_db_password="YourStrongPassword"
export TF_VAR_jwt_secret="YourJwtSecret"
terraform apply
```

## After Apply

The ALB DNS name is printed as an output:

```bash
terraform output alb_dns_name
# → swiftcart-alb-xxxxxxxx.af-south-1.elb.amazonaws.com
```

The API is then available at:
```
http://<alb_dns_name>:8080/api/v1/products
```

## GitHub Actions — Automated Deploy

Add these secrets to your GitHub repository (`Settings → Secrets → Actions`):

| Secret | Value |
|---|---|
| `AWS_ACCESS_KEY_ID` | IAM user access key with ECS + ECR permissions |
| `AWS_SECRET_ACCESS_KEY` | Corresponding secret key |

Every push to `main` will build the Docker image, push it to ECR, and force a new ECS deployment.

## Tear Down

```bash
terraform destroy \
  -var="db_password=YourStrongPassword" \
  -var="jwt_secret=YourJwtSecret"
```

> **Note:** RDS has `deletion_protection = true`. Disable it first:
> `terraform apply -var="..." -target=aws_db_instance.main` after setting `deletion_protection = false`.

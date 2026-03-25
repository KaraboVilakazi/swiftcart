output "alb_dns_name" {
  description = "Public DNS of the Application Load Balancer"
  value       = aws_lb.main.dns_name
}

output "ecr_repository_url" {
  description = "ECR repository URL — use this in GitHub Actions to push images"
  value       = aws_ecr_repository.app.repository_url
}

output "rds_endpoint" {
  description = "RDS PostgreSQL endpoint (private — accessible from ECS only)"
  value       = aws_db_instance.main.endpoint
  sensitive   = true
}

output "redis_endpoint" {
  description = "ElastiCache Redis endpoint (private — accessible from ECS only)"
  value       = aws_elasticache_cluster.redis.cache_nodes[0].address
  sensitive   = true
}

output "ecs_cluster_name" {
  description = "ECS cluster name — used in GitHub Actions deploy workflow"
  value       = aws_ecs_cluster.main.name
}

output "ecs_service_name" {
  description = "ECS service name — used in GitHub Actions deploy workflow"
  value       = aws_ecs_service.app.name
}

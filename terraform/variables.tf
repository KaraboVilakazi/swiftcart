variable "aws_region" {
  description = "AWS region to deploy into"
  type        = string
  default     = "af-south-1" # Cape Town
}

variable "app_name" {
  description = "Application name used to prefix all resources"
  type        = string
  default     = "swiftcart"
}

variable "environment" {
  description = "Deployment environment"
  type        = string
  default     = "production"
}

# ── Networking ────────────────────────────────────────────────────────────────

variable "vpc_cidr" {
  description = "CIDR block for the VPC"
  type        = string
  default     = "10.0.0.0/16"
}

variable "public_subnet_cidrs" {
  description = "CIDR blocks for public subnets (one per AZ)"
  type        = list(string)
  default     = ["10.0.1.0/24", "10.0.2.0/24"]
}

variable "private_subnet_cidrs" {
  description = "CIDR blocks for private subnets (one per AZ)"
  type        = list(string)
  default     = ["10.0.11.0/24", "10.0.12.0/24"]
}

variable "availability_zones" {
  description = "Availability zones to spread resources across"
  type        = list(string)
  default     = ["af-south-1a", "af-south-1b"]
}

# ── Application ───────────────────────────────────────────────────────────────

variable "app_port" {
  description = "Port the Spring Boot app listens on inside the container"
  type        = number
  default     = 8080
}

variable "app_cpu" {
  description = "ECS task CPU units (1024 = 1 vCPU)"
  type        = number
  default     = 512
}

variable "app_memory" {
  description = "ECS task memory in MB"
  type        = number
  default     = 1024
}

variable "app_desired_count" {
  description = "Number of ECS task instances to run"
  type        = number
  default     = 1
}

variable "jwt_secret" {
  description = "JWT signing secret — provide via TF_VAR_jwt_secret env var, never hardcode"
  type        = string
  sensitive   = true
}

# ── Database ──────────────────────────────────────────────────────────────────

variable "db_instance_class" {
  description = "RDS instance type"
  type        = string
  default     = "db.t3.micro"
}

variable "db_name" {
  description = "PostgreSQL database name"
  type        = string
  default     = "swiftcart"
}

variable "db_username" {
  description = "PostgreSQL master username"
  type        = string
  default     = "swiftcart"
}

variable "db_password" {
  description = "PostgreSQL master password — provide via TF_VAR_db_password env var"
  type        = string
  sensitive   = true
}

variable "db_allocated_storage" {
  description = "RDS storage in GB"
  type        = number
  default     = 20
}

# ── Cache ─────────────────────────────────────────────────────────────────────

variable "redis_node_type" {
  description = "ElastiCache node type"
  type        = string
  default     = "cache.t3.micro"
}

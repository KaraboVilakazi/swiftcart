# ── RDS Subnet Group ──────────────────────────────────────────────────────────
# RDS must span at least 2 AZs

resource "aws_db_subnet_group" "main" {
  name       = "${var.app_name}-db-subnet-group"
  subnet_ids = aws_subnet.private[*].id

  tags = { Name = "${var.app_name}-db-subnet-group" }
}

# ── RDS Parameter Group ───────────────────────────────────────────────────────

resource "aws_db_parameter_group" "main" {
  name   = "${var.app_name}-pg16"
  family = "postgres16"

  parameter {
    name  = "log_connections"
    value = "1"
  }

  tags = { Name = "${var.app_name}-pg16" }
}

# ── RDS Instance ──────────────────────────────────────────────────────────────

resource "aws_db_instance" "main" {
  identifier = "${var.app_name}-postgres"

  engine         = "postgres"
  engine_version = "16"
  instance_class = var.db_instance_class

  allocated_storage     = var.db_allocated_storage
  max_allocated_storage = 100 # autoscaling up to 100 GB
  storage_type          = "gp3"
  storage_encrypted     = true

  db_name  = var.db_name
  username = var.db_username
  password = var.db_password

  db_subnet_group_name   = aws_db_subnet_group.main.name
  vpc_security_group_ids = [aws_security_group.rds.id]
  parameter_group_name   = aws_db_parameter_group.main.name

  # Backups
  backup_retention_period = 7
  backup_window           = "02:00-03:00"
  maintenance_window      = "sun:04:00-sun:05:00"

  # Production hardening
  deletion_protection      = true
  skip_final_snapshot      = false
  final_snapshot_identifier = "${var.app_name}-final-snapshot"
  publicly_accessible      = false
  multi_az                 = false # set to true for production HA

  tags = { Name = "${var.app_name}-postgres" }
}

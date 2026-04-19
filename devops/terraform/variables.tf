variable "yc_token" {
  description = "Yandex Cloud IAM/OAuth token"
  type        = string
  sensitive   = true
}

variable "yc_cloud_id" {
  description = "Yandex Cloud ID"
  type        = string
}

variable "yc_folder_id" {
  description = "Yandex Cloud folder ID"
  type        = string
}

variable "yc_zone" {
  description = "Availability zone"
  type        = string
  default     = "ru-central1-a"
}

variable "vm_name" {
  description = "Name of the VM"
  type        = string
  default     = "dealer-k3s"
}

variable "vm_cores" {
  description = "Number of vCPUs"
  type        = number
  default     = 4
}

variable "vm_memory_gb" {
  description = "RAM in GB"
  type        = number
  default     = 8
}

variable "vm_core_fraction" {
  description = "Core fraction (5/20/50/100)"
  type        = number
  default     = 50
}

variable "vm_disk_gb" {
  description = "Boot disk size GB"
  type        = number
  default     = 30
}

variable "vm_image_family" {
  description = "Base image family"
  type        = string
  default     = "ubuntu-2204-lts"
}

variable "ssh_public_key_path" {
  description = "Path to SSH public key"
  type        = string
  default     = "~/.ssh/yc_lab.pub"
}

variable "ssh_user" {
  description = "Default SSH user"
  type        = string
  default     = "ubuntu"
}

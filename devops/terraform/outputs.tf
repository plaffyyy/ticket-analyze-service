output "static_ip" {
  description = "Static public IP of the VM"
  value       = yandex_vpc_address.static.external_ipv4_address[0].address
}

output "ssh_command" {
  description = "SSH command to connect to the VM"
  value       = "ssh ${var.ssh_user}@${yandex_vpc_address.static.external_ipv4_address[0].address}"
}

output "app_url" {
  description = "URL to access the app"
  value       = "http://${yandex_vpc_address.static.external_ipv4_address[0].address}"
}

data "yandex_compute_image" "ubuntu" {
  family = var.vm_image_family
}

locals {
  ssh_public_key = file(pathexpand(var.ssh_public_key_path))

  cloud_init = templatefile("${path.module}/cloud-init.yaml.tftpl", {
    ssh_user       = var.ssh_user
    ssh_public_key = local.ssh_public_key
  })
}

resource "yandex_compute_instance" "this" {
  name        = var.vm_name
  hostname    = var.vm_name
  platform_id = "standard-v3"
  zone        = var.yc_zone

  resources {
    cores         = var.vm_cores
    memory        = var.vm_memory_gb
    core_fraction = var.vm_core_fraction
  }

  boot_disk {
    initialize_params {
      image_id = data.yandex_compute_image.ubuntu.id
      size     = var.vm_disk_gb
      type     = "network-ssd"
    }
  }

  network_interface {
    subnet_id          = yandex_vpc_subnet.this.id
    nat                = true
    nat_ip_address     = yandex_vpc_address.static.external_ipv4_address[0].address
    security_group_ids = [yandex_vpc_security_group.this.id]
  }

  metadata = {
    user-data = local.cloud_init
    ssh-keys  = "${var.ssh_user}:${local.ssh_public_key}"
  }

  scheduling_policy {
    preemptible = false
  }
}

resource "yandex_vpc_network" "this" {
  name = "${var.vm_name}-net"
}

resource "yandex_vpc_subnet" "this" {
  name           = "${var.vm_name}-subnet"
  zone           = var.yc_zone
  network_id     = yandex_vpc_network.this.id
  v4_cidr_blocks = ["10.10.0.0/24"]
}

resource "yandex_vpc_address" "static" {
  name = "${var.vm_name}-static-ip"

  external_ipv4_address {
    zone_id = var.yc_zone
  }
}

resource "yandex_vpc_security_group" "this" {
  name       = "${var.vm_name}-sg"
  network_id = yandex_vpc_network.this.id

  ingress {
    description    = "SSH from anywhere"
    protocol       = "TCP"
    v4_cidr_blocks = ["0.0.0.0/0"]
    port           = 22
  }

  ingress {
    description    = "HTTP app (NodePort / ingress)"
    protocol       = "TCP"
    v4_cidr_blocks = ["0.0.0.0/0"]
    port           = 80
  }

  ingress {
    description    = "App direct NodePort"
    protocol       = "TCP"
    v4_cidr_blocks = ["0.0.0.0/0"]
    port           = 8080
  }

  ingress {
    description    = "Grafana NodePort"
    protocol       = "TCP"
    v4_cidr_blocks = ["0.0.0.0/0"]
    port           = 30030
  }

  ingress {
    description    = "NodePort range"
    protocol       = "TCP"
    v4_cidr_blocks = ["0.0.0.0/0"]
    from_port      = 30000
    to_port        = 32767
  }

  ingress {
    description    = "HTTPS / ingress"
    protocol       = "TCP"
    v4_cidr_blocks = ["0.0.0.0/0"]
    port           = 443
  }

  ingress {
    description    = "Kubernetes API"
    protocol       = "TCP"
    v4_cidr_blocks = ["0.0.0.0/0"]
    port           = 6443
  }

  ingress {
    description    = "ICMP"
    protocol       = "ICMP"
    v4_cidr_blocks = ["0.0.0.0/0"]
  }

  egress {
    description    = "Any"
    protocol       = "ANY"
    v4_cidr_blocks = ["0.0.0.0/0"]
  }
}

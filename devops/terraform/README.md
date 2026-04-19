# Terraform: ВМ в Yandex Cloud для k3s

Создаётся сеть, статический публичный IP, security group и одна ВМ (Ubuntu 22.04) с SSH по ключу.

## Что нужно заранее

- Установлены [Terraform](https://developer.hashicorp.com/terraform/install) `>= 1.5` и [OpenSSH](https://www.openssh.com/).
- Аккаунт [Yandex Cloud](https://cloud.yandex.ru/), каталог (folder) и облако (cloud).
- **OAuth- или IAM-токен** с правами на создание ресурсов в каталоге (см. [документацию](https://yandex.cloud/ru/docs/iam/concepts/authorization/oauth-token)).
- SSH **публичный** ключ (по умолчанию ожидается `~/.ssh/yc_lab.pub`, путь можно переопределить переменной `TF_VAR_ssh_public_key_path`).

## Шаги после клонирования репозитория

Перейти в каталог с конфигурацией:

```bash
cd terraform
```

Создать локальный файл переменных из примера:

```bash
cp terraform.tfvars.example terraform.tfvars
```

Отредактировать `terraform.tfvars`: подставить `yc_cloud_id`, `yc_folder_id`, при необходимости `yc_zone`, `vm_cores`, `vm_memory_gb`.

**Токен не записывать в файл**: перед каждым `plan` / `apply` экспортировать:

```bash
export TF_VAR_yc_token="y0_ВАШ_ТОКЕН"
```

Если ключ лежит не в `~/.ssh/yc_lab.pub`:

```bash
export TF_VAR_ssh_public_key_path="$HOME/.ssh/id_ed25519.pub"
```

Инициализация провайдера и модулей (один раз или после смены `versions.tf`):

```bash
terraform init -input=false
```

Просмотр плана без изменений:

```bash
terraform plan -out=tfplan
```

Применение (создание/обновление инфраструктуры):

```bash
terraform apply tfplan
```

Или одной командой (как в `apply.sh`):

```bash
./apply.sh
```

После успешного apply — вывести IP и команду SSH:

```bash
terraform output
```

Пример:

- `static_ip` — публичный адрес ВМ
- `ssh_command` — готовая строка для `ssh`
- `app_url` — заглушка под HTTP (приложение поднимается отдельно по инструкции)

## Уничтожение инфраструктуры

```bash
cd terraform
export TF_VAR_yc_token="y0_ВАШ_ТОКЕН"
terraform destroy
```

Подтвердите запрос Terraform или используйте `-auto-approve` только если осознанно.

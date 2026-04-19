# Сценарий: ВМ уже есть → k3s на сервере → всё остальное с ноутбука

Ниже предполагается, что **виртуальная машина в Yandex Cloud уже создана** (отдельная инструкция в **`devops/terraform/README.md`**: переменные, `terraform apply`, получение `static_ip`).

На **сервер не копируются** каталоги `k8s/` и не нужен клон всего репозитория на ВМ. На ВМ попадают только **bash-скрипты** из `scripts/`. Манифесты и Helm values читаются **локально** из вашего клона репозитория; **Helm** и **`kubectl`** работают с ноутбука после настройки `kubeconfig`.

---

## Обозначения

На ноутбуке один раз задайте (подставьте свои значения):

```bash
export SSH_KEY=~/.ssh/yc_lab
export VM_IP=203.0.113.10
export REPO_ROOT=~/git/ticket-analyze-service/devops
```

- **`SSH_KEY`** — приватный ключ SSH для пользователя `ubuntu` на ВМ.  
- **`VM_IP`** — публичный IP из `terraform output static_ip`.  
- **`REPO_ROOT`** — devops директория **локального** клона репозитория (там лежат `k8s/` и `scripts/`).

Дальше на ноутбуке понадобятся **`kubectl`** и **`helm`** (для мониторинга и Loki). Установка Helm: https://helm.sh/docs/intro/install/

---

## Шаг 1. Скопировать на ВМ только скрипты

С ноутбука:

```bash
ssh -i "${SSH_KEY}" -o StrictHostKeyChecking=accept-new "ubuntu@${VM_IP}" 'mkdir -p ~/scripts'
scp -i "${SSH_KEY}" \
  "${REPO_ROOT}/scripts/install-k8s.sh" \
  "${REPO_ROOT}/scripts/setup-edge-proxy.sh" \
  "ubuntu@${VM_IP}:~/scripts/"
```

**Зачем:** на сервере выполняется только установка **k3s** и при необходимости **nginx/certbot**; YAML и values остаются у вас в git на ноутбуке.

---

## Шаг 2. Установить k3s на ВМ (`install-k8s.sh`)

Подключение к ВМ:

```bash
ssh -i "${SSH_KEY}" "ubuntu@${VM_IP}"
```

Запуск скрипта **от root** (k3s ставится системно):

```bash
sudo bash ~/scripts/install-k8s.sh
```

**Что делает скрипт сейчас:**

- ставит пакеты (`curl`, …), нужные установщику k3s;
- ставит **k3s** (Traefik отключён), при возможности добавляет в сертификат API **SAN на публичный IP**;
- кладёт kubeconfig в `~/.kube/config` для пользователя `ubuntu` и подменяет в нём `https://127.0.0.1:6443` → `https://<VM_IP>:6443`, чтобы с ноутбука не было ошибки TLS.

Если SSH обрывается на долгих операциях, на ВМ:

```bash
nohup sudo bash ~/scripts/install-k8s.sh > /tmp/install-k8s.log 2>&1 &
tail -f /tmp/install-k8s.log
```

---

## Шаг 3. kubeconfig на ноутбук и проверка API

На **ноутбуке**:

```bash
mkdir -p ~/.kube
scp -i "${SSH_KEY}" "ubuntu@${VM_IP}:~/.kube/config" ~/.kube/k3s-dealer.yaml
export KUBECONFIG=~/.kube/k3s-dealer.yaml
kubectl get nodes
```

**Зачем:** `kubectl` и `helm` на ноутбуке ходят в кластер по `https://<VM_IP>:6443`.

Если в `~/.kube/k3s-dealer.yaml` в `clusters[].cluster.server` всё ещё `127.0.0.1`, замените на IP ВМ.

Linux:

```bash
sed -i.bak "s#https://127.0.0.1:6443#https://${VM_IP}:6443#g" ~/.kube/k3s-dealer.yaml
```

macOS:

```bash
sed -i '' "s#https://127.0.0.1:6443#https://${VM_IP}:6443#g" ~/.kube/k3s-dealer.yaml
```

Повторная проверка:

```bash
kubectl get nodes
```

Ожидается одна нода в статусе `Ready`.

---

## Шаг 4. Мониторинг с ноутбука (Helm + локальный `values`)

Рабочий каталог — **корень репозитория на ноутбуке**, чтобы путь `-f` к файлу был коротким и понятным:

```bash
cd "${REPO_ROOT}"
export KUBECONFIG=~/.kube/k3s-dealer.yaml
```

Добавить репозиторий чартов и обновить индекс:

```bash
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
helm repo update
```

**Зачем:** чарт `kube-prometheus-stack` скачивается из репозитория `prometheus-community`.

Установить или обновить релиз **`monitoring`** в namespace `monitoring`, подставив **локальный** файл values из репозитория:

```bash
helm upgrade --install monitoring prometheus-community/kube-prometheus-stack \
  --namespace monitoring \
  --create-namespace \
  -f k8s/monitoring/values-kube-prometheus-stack.yaml \
  --wait --timeout 20m
```

**Зачем:** Prometheus, Grafana, Alertmanager и сопутствующие поды поднимаются в кластере; конфигурация берётся из вашего git (в т.ч. пароль Grafana из `grafana.adminPassword` в этом YAML).

Проверка:

```bash
kubectl -n monitoring get pods
```

Дождаться готовности Grafana:

```bash
kubectl -n monitoring rollout status deploy/monitoring-grafana --timeout=10m
```

---

## Шаг 5. Приложение, БД и Redis

Все команды ниже — **с ноутбука**, каталог `k8s/app/` относительно `REPO_ROOT`.

```bash
cd "${REPO_ROOT}"
export KUBECONFIG=~/.kube/k3s-dealer.yaml
```

### 5.1 Секреты: настроить **до** первого `kubectl apply -f k8s/app/`

В кластере приложение и Postgres читают данные из Kubernetes **Secret**. В репозитории они заданы в YAML:

| Файл                         | Secret                           | Ключи `stringData`                                  | Зачем                                   |
|------------------------------|----------------------------------|-----------------------------------------------------|-----------------------------------------|
| `k8s/app/10-postgres.yaml`   | `postgres-credentials`           | `POSTGRES_USER`, `POSTGRES_PASSWORD`, `POSTGRES_DB` | Учётная запись Postgres в контейнере БД |
| `k8s/app/30-app-config.yaml` | `ticket-analyze-service-secrets` | `DATABASE_USER`, `DATABASE_PASSWORD`, `JWT_SECRET`  | Spring: JDBC и JWT                      |

**Важно:** пароль приложения к БД и пароль самой БД должны **совпадать**:

- `postgres-credentials` → `POSTGRES_PASSWORD`
- `ticket-analyze-service-secrets` → `DATABASE_PASSWORD`  
  (и `DATABASE_USER` = `POSTGRES_USER`, обычно `dealer`)

`JWT_SECRET` — длинная случайная строка (для JWT в проде нужна достаточная длина; в манифесте есть подсказка в placeholder).

Отредактируйте **локально**, без коммита в git:

- `k8s/app/10-postgres.yaml` — блок `stringData` у `postgres-credentials`
- `k8s/app/30-app-config.yaml` — блок `stringData` у `ticket-analyze-service-secrets`

Сохраните файлы, затем переходите к п. **5.2**.

### 5.2 Установка манифестов

```bash
kubectl apply -f k8s/app/
```

**Зачем:** namespace `dealer`, StatefulSet’ы Postgres и Redis, Deployment’ы приложения (blue/green), Service’ы, PDB, ServiceMonitor.

### 5.3 Ожидание готовности

```bash
kubectl -n dealer rollout status sts/postgres --timeout=10m
kubectl -n dealer rollout status sts/redis --timeout=5m
kubectl -n dealer rollout status deploy/ticket-analyze-service-blue --timeout=15m
```

### 5.4 Проверка

```bash
curl -sS "http://${VM_IP}:30080/actuator/health"
```

(NodePort **30080** — как в `k8s/app/50-app-service.yaml`.)

### 5.5 Смена секретов **после** деплоя

1. Обновите Secret (тем же способом, что в п. 5.1 правкой YAML + `kubectl apply -f k8s/app/30-app-config.yaml` только для нужного ресурса).

2. **Перезапустите поды**, чтобы процесс перечитал переменные окружения из Secret:

Приложение (активный цвет — чаще всего `blue`; если поднят и `green` — повторите для него):

```bash
kubectl -n dealer rollout restart deploy/ticket-analyze-service-blue
kubectl -n dealer rollout status deploy/ticket-analyze-service-blue --timeout=10m
```

**Зачем:** `rollout restart` создаёт новые pod’ы с новым поколением ReplicaSet; старые завершаются после drain по правилам Deployment.

Postgres (если меняли **только** `postgres-credentials` и пароль **уже совпадает** с тем, что записан в data directory — редкий случай при первом старте; при **смене** пароля на существующем томе обычно нужна отдельная процедура внутри Postgres или пересоздание PVC):

```bash
kubectl -n dealer rollout restart sts/postgres
kubectl -n dealer rollout status sts/postgres --timeout=10m
```

Redis при смене пароля (если добавите секрет позже):

```bash
kubectl -n dealer rollout restart sts/redis
kubectl -n dealer rollout status sts/redis --timeout=5m
```

**Замечание:** ConfigMap (`ticket-analyze-service-config`) меняется так же: `kubectl apply -f k8s/app/30-app-config.yaml` (или выборочно) + `rollout restart` для Deployment’ов приложения — поды не подхватывают ConfigMap автоматически.

---

## Шаг 6. HTTPS на ВМ (`setup-edge-proxy.sh`)

Снова на **ВМ** (скрипт уже скопирован на шаге 1):

```bash
ssh -i "${SSH_KEY}" "ubuntu@${VM_IP}"
```

Запуск **от root**:

```bash
sudo bash ~/scripts/setup-edge-proxy.sh
```

**Зачем:** nginx слушает 80/443, Let’s Encrypt выписывает сертификат на имя `<IP>.sslip.io`, HTTPS проксируется на `127.0.0.1:30080` (NodePort приложения).

Проверка **с ноутбука**:

```bash
curl -sSLf "http://${VM_IP}/actuator/health"
curl -sSf  "https://${VM_IP}.sslip.io/actuator/health"
```

---

## Шаг 7. Логи (Loki) с ноутбука

```bash
cd "${REPO_ROOT}"
export KUBECONFIG=~/.kube/k3s-dealer.yaml
helm repo add grafana https://grafana.github.io/helm-charts
helm repo update
helm upgrade --install loki-stack grafana/loki-stack \
  --namespace monitoring \
  --set grafana.enabled=false \
  --set prometheus.enabled=false \
  --set promtail.enabled=true \
  --set fluent-bit.enabled=false \
  --wait --timeout 20m
```

**Зачем:** Loki + Promtail; Grafana остаётся из kube-prometheus-stack.

Если Grafana ушла в `CrashLoop` из‑за двух datasource с `isDefault: true`:

```bash
kubectl -n monitoring patch configmap loki-stack --type merge -p '{"data":{"loki-stack-datasource.yaml":"apiVersion: 1\ndatasources:\n- name: Loki\n  type: loki\n  access: proxy\n  url: \"http://loki-stack:3100\"\n  version: 1\n  isDefault: false\n  jsonData: {}\n"}}'
kubectl -n monitoring rollout restart deploy/monitoring-grafana
kubectl -n monitoring rollout status deploy/monitoring-grafana --timeout=10m
```

Проброс Grafana на ноутбук:

```bash
export POD_NAME=$(kubectl -n monitoring get pod -l "app.kubernetes.io/name=grafana,app.kubernetes.io/instance=monitoring" -o name)
kubectl -n monitoring port-forward "${POD_NAME}" 3000:3000
```

В браузере: `http://127.0.0.1:3000` — логин/пароль из `k8s/monitoring/values-kube-prometheus-stack.yaml`. В Explore → **Loki**, пример: `{namespace="dealer"}`.

---

## Шаг 8. Релиз образа (blue-green)

```bash
cd "${REPO_ROOT}"
export KUBECONFIG=~/.kube/k3s-dealer.yaml
export IMAGE_REPO=ghcr.io/your-org/ticket-analyze-service
./scripts/deploy-release.sh v1.2.3
```

**Зачем:** смена тега на неактивном цвете, rollout, smoke `actuator/health`, переключение Service, остановка старого цвета.

---

Создание ВМ описано в **`devops/terraform/README.md`**.

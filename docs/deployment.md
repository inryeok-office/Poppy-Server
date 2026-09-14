# 운영 배포

Poppy-Server의 현재 production source of truth는 Docker Compose다. `docker-compose.yml`이 app과 PostgreSQL을 함께 정의하고, app은 PostgreSQL healthcheck가 통과한 뒤 시작한다. Ubuntu 행사장 환경에서는 `deploy/systemd/poppy-server.service`가 Compose foreground 프로세스를 관리한다.

이 문서는 repository 수준의 설치·운영 절차만 제공한다. 실제 서버에 설치하거나 reboot를 수행하지 않는다.

## 전제

- Ubuntu의 systemd
- Docker Engine과 Docker Compose plugin
- `/opt/poppy-server`에 checkout된 repository
- Docker daemon이 부팅 시 시작되도록 설치된 상태
- `poppy` system user가 Docker socket을 사용할 수 있는 상태

서비스는 `/opt/poppy-server`에서 실행되며 `/etc/poppy-server/poppy-server.env`를 읽는다. env 파일에는 DB password와 Agent token을 포함할 수 있으므로 repository의 예시 파일을 그대로 운영에 사용하지 않는다.

## 설치

아래 명령은 operator가 실제 Ubuntu 서버에서 검토한 뒤 직접 실행한다.

1. repository를 `/opt/poppy-server`에 배치하고 service user가 읽을 수 있게 한다.

```bash
getent passwd poppy >/dev/null || sudo useradd --system --create-home --home-dir /opt/poppy-server --shell /usr/sbin/nologin poppy
sudo usermod --append --groups docker poppy
sudo install -d -o poppy -g poppy /opt/poppy-server
sudo chown -R poppy:poppy /opt/poppy-server
```

repository를 `/opt/poppy-server`에 배치한 뒤 위의 `chown`을 다시 실행한다. `poppy` 사용자가 이미 있으면 useradd 명령은 아무 작업도 하지 않는다.

2. 운영용 env 파일을 만들고 secret을 서버에서만 입력한다.

```bash
sudo install -d -m 0750 /etc/poppy-server
sudo install -o root -g poppy -m 0640 deploy/systemd/poppy-server.env.example /etc/poppy-server/poppy-server.env
sudoedit /etc/poppy-server/poppy-server.env
```

3. Compose 설정을 확인하고 app 이미지를 빌드한다.

```bash
sudo -u poppy docker compose --env-file /etc/poppy-server/poppy-server.env config --quiet
sudo -u poppy docker compose --env-file /etc/poppy-server/poppy-server.env build app
```

4. unit을 설치하고 부팅 자동 시작을 활성화한다.

```bash
sudo install -m 0644 deploy/systemd/poppy-server.service /etc/systemd/system/poppy-server.service
sudo systemctl daemon-reload
sudo systemctl enable --now poppy-server.service
```

이 unit은 `network-online.target`과 Docker daemon 이후 시작한다. Compose의 `depends_on`과 PostgreSQL `healthcheck`가 PostgreSQL이 healthy가 된 뒤 app을 시작하게 한다.

## 운영 명령

```bash
sudo systemctl start poppy-server.service
sudo systemctl stop poppy-server.service
sudo systemctl restart poppy-server.service
sudo systemctl status poppy-server.service
```

app 또는 PostgreSQL 컨테이너가 실패하면 Compose가 종료 코드와 함께 종료되고, systemd가 5초 후 서비스를 재시작한다. `systemctl stop`은 자동 재시작하지 않는다. 중지 시 `docker compose stop`만 실행하며 DB volume을 삭제하지 않는다.

## 로그

Compose foreground 출력은 systemd journal에서 확인할 수 있다.

```bash
sudo journalctl -u poppy-server.service -f
sudo journalctl -u poppy-server.service -n 100 --no-pager
sudo -u poppy docker compose --env-file /etc/poppy-server/poppy-server.env logs --tail=100 app
```

## Health 확인

Actuator의 기존 health endpoint를 사용한다.

```bash
curl --fail --silent --show-error http://127.0.0.1:8080/actuator/health
```

응답의 `status`가 `UP`인지 확인한다. custom health API를 추가하지 않는다.

## 업데이트

새 commit을 `/opt/poppy-server`에 반영한 뒤 image를 다시 만들고 서비스를 재시작한다.

```bash
sudo -u poppy docker compose --env-file /etc/poppy-server/poppy-server.env build app
sudo systemctl restart poppy-server.service
sudo systemctl status poppy-server.service
```

운영 env 파일은 repository 업데이트로 덮어쓰지 않는다. `docker compose down -v`, DB volume 삭제, 임의의 credential 생성을 이 절차에 포함하지 않는다.

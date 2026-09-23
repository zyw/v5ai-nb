#!/usr/bin/env bash

# v5ai-nb 服务管理脚本。
# 支持源码目录中的 v5ai-starter/target jar，也支持发布目录中的同名 jar。

set -u

APP_NAME="v5ai-nb"
JAR_NAME="v5ai-starter-0.1.0-SNAPSHOT.jar"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
APP_HOME="${APP_HOME:-$(cd "${SCRIPT_DIR}/../.." && pwd)}"
CONF_DIR="${CONF_DIR:-${APP_HOME}/config}"
PROFILE="${PROFILE:-dev}"
JAVA_OPTS="${JAVA_OPTS:--Xms512m -Xmx1024m}"
PORT="${PORT:-8080}"
PID_FILE="${PID_FILE:-${APP_HOME}/${APP_NAME}.pid}"
LOG_DIR="${LOG_DIR:-${APP_HOME}/logs}"
LOG_FILE="${LOG_FILE:-${LOG_DIR}/${APP_NAME}.log}"
SERVICE_USER="${USER_NAME:-$(id -un)}"
SYSTEMD_FILE="/etc/systemd/system/${APP_NAME}.service"

find_jar() {
  if [[ -n "${JAR_PATH:-}" && -f "${JAR_PATH}" ]]; then
    printf '%s\n' "${JAR_PATH}"
    return 0
  fi

  local candidates=(
    "${APP_HOME}/${JAR_NAME}"
    "${APP_HOME}/v5ai-starter/target/${JAR_NAME}"
  )
  local candidate
  for candidate in "${candidates[@]}"; do
    if [[ -f "${candidate}" ]]; then
      printf '%s\n' "${candidate}"
      return 0
    fi
  done

  if [[ -d "${APP_HOME}/v5ai-starter/target" ]]; then
    find "${APP_HOME}/v5ai-starter/target" -maxdepth 1 -type f \
      -name 'v5ai-starter-*.jar' ! -name '*-plain.jar' -print -quit
  fi
}

get_pid() {
  [[ -f "${PID_FILE}" ]] || return 1
  local pid
  pid="$(cat "${PID_FILE}")"
  [[ "${pid}" =~ ^[0-9]+$ ]] || return 1
  printf '%s\n' "${pid}"
}

is_running() {
  local pid
  pid="$(get_pid 2>/dev/null || true)"
  [[ -n "${pid}" ]] && kill -0 "${pid}" 2>/dev/null
}

build_spring_args() {
  printf '%s\n' "--server.port=${PORT}" "--spring.profiles.active=${PROFILE}"
  if [[ -d "${CONF_DIR}" ]]; then
    printf '%s\n' "--spring.config.additional-location=optional:file:${CONF_DIR}/"
  fi
}

start() {
  if is_running; then
    echo "${APP_NAME} already running, pid=$(get_pid)"
    return 0
  fi

  local jar
  jar="$(find_jar)"
  if [[ -z "${jar}" || ! -f "${jar}" ]]; then
    echo "Cannot find ${JAR_NAME}; build the project or set JAR_PATH." >&2
    return 1
  fi

  mkdir -p "${LOG_DIR}"
  rm -f "${PID_FILE}"
  mapfile -t spring_args < <(build_spring_args)

  echo "Starting ${APP_NAME} with ${jar}"
  nohup java ${JAVA_OPTS} -jar "${jar}" "${spring_args[@]}" >>"${LOG_FILE}" 2>&1 &
  echo $! >"${PID_FILE}"

  sleep 2
  if is_running; then
    echo "${APP_NAME} started, pid=$(get_pid), log=${LOG_FILE}"
    return 0
  fi

  echo "${APP_NAME} failed to start; inspect ${LOG_FILE}." >&2
  rm -f "${PID_FILE}"
  return 1
}

stop() {
  local pid
  pid="$(get_pid 2>/dev/null || true)"
  if [[ -z "${pid}" ]]; then
    echo "${APP_NAME} is not running"
    rm -f "${PID_FILE}"
    return 0
  fi

  echo "Stopping ${APP_NAME}, pid=${pid}"
  kill "${pid}" 2>/dev/null || true
  for _ in {1..30}; do
    if ! kill -0 "${pid}" 2>/dev/null; then
      rm -f "${PID_FILE}"
      echo "${APP_NAME} stopped"
      return 0
    fi
    sleep 1
  done

  echo "${APP_NAME} did not stop gracefully; killing pid=${pid}" >&2
  kill -9 "${pid}" 2>/dev/null || true
  rm -f "${PID_FILE}"
}

restart() {
  stop
  start
}

status() {
  if is_running; then
    echo "${APP_NAME} is running, pid=$(get_pid), port=${PORT}"
    return 0
  fi
  echo "${APP_NAME} is stopped"
  return 3
}

install_service() {
  if [[ "$(id -u)" -ne 0 ]]; then
    echo "install requires root; use sudo." >&2
    return 1
  fi

  local java_bin
  java_bin="$(command -v java)"
  if [[ -z "${java_bin}" ]]; then
    echo "java was not found in PATH." >&2
    return 1
  fi

  cat >"${SYSTEMD_FILE}" <<EOF
[Unit]
Description=v5ai-nb AI Agent Platform
After=network.target

[Service]
Type=simple
User=${SERVICE_USER}
WorkingDirectory=${APP_HOME}
ExecStart=${java_bin} ${JAVA_OPTS} -jar ${APP_HOME}/v5ai-starter/target/${JAR_NAME} --server.port=${PORT} --spring.profiles.active=${PROFILE} --spring.config.additional-location=optional:file:${CONF_DIR}/
Restart=on-failure
RestartSec=5
SuccessExitStatus=143
StandardOutput=append:${LOG_FILE}
StandardError=append:${LOG_FILE}

[Install]
WantedBy=multi-user.target
EOF

  mkdir -p "${LOG_DIR}"
  chown -R "${SERVICE_USER}" "${LOG_DIR}" "${APP_HOME}" 2>/dev/null || true
  systemctl daemon-reload
  systemctl enable "${APP_NAME}"
  echo "Installed ${SYSTEMD_FILE}"
  echo "Start with: systemctl start ${APP_NAME}"
}

uninstall_service() {
  if [[ "$(id -u)" -ne 0 ]]; then
    echo "uninstall requires root; use sudo." >&2
    return 1
  fi
  systemctl disable --now "${APP_NAME}" 2>/dev/null || true
  rm -f "${SYSTEMD_FILE}"
  systemctl daemon-reload
  echo "Uninstalled ${APP_NAME} systemd service"
}

usage() {
  cat <<EOF
Usage: $0 {start|stop|restart|status|install|uninstall}

Environment overrides:
  APP_HOME    application directory (default: repository root)
  JAR_PATH    explicit executable jar path
  CONF_DIR    external Spring config directory (default: APP_HOME/config)
  PROFILE     Spring profile (default: dev)
  JAVA_OPTS   JVM options (default: -Xms512m -Xmx1024m)
  PORT        HTTP port (default: 8080)
EOF
}

case "${1:-}" in
  start) start ;;
  stop) stop ;;
  restart) restart ;;
  status) status ;;
  install) install_service ;;
  uninstall) uninstall_service ;;
  *) usage; exit 2 ;;
esac

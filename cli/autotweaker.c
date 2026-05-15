/*
 * AutoTweaker
 * Copyright (C) 2026  WhiteElephant-abc
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

#define _GNU_SOURCE
#include <cjson/cJSON.h>

#include <errno.h>
#include <fcntl.h>
#include <poll.h>
#include <signal.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/socket.h>
#include <sys/stat.h>
#include <sys/un.h>
#include <sys/wait.h>
#include <termios.h>
#include <time.h>
#include <unistd.h>

#define MAX_LINE_LEN 65536
#define POLL_INTERVAL_MS 100
#define WARN_ITERATIONS 600

static const char *const PROXY_VARS[] = {
    "https_proxy", "HTTPS_PROXY", "http_proxy", "HTTP_PROXY",
    "all_proxy",  "ALL_PROXY",   "no_proxy",   "NO_PROXY",
};

static char conf_dir[512];
static char sock_path[512];
static char lock_path[512];
static char env_path[512];

/* ---- read_line: buffered line reader on a socket fd ---- */
struct line_reader {
    int  fd;
    char buf[MAX_LINE_LEN];
    int  pos;
    int  len;
};

static int lr_fill(struct line_reader *lr) {
    if (lr->pos > 0 && lr->pos < lr->len) {
        memmove(lr->buf, lr->buf + lr->pos, lr->len - lr->pos);
        lr->len -= lr->pos;
        lr->pos = 0;
    } else {
        lr->len = 0;
        lr->pos = 0;
    }
    int     eintr_retries = 0;
    ssize_t n;
    while (eintr_retries < 100) {
        n = recv(lr->fd, lr->buf + lr->len,
                 sizeof(lr->buf) - 1 - lr->len, 0);
        if (n < 0) {
            if (errno == EINTR) {
                eintr_retries++;
                struct timespec ts = {0, POLL_INTERVAL_MS * 1000000L};
                nanosleep(&ts, NULL);
                continue;
            }
            return -1;
        }
        break;
    }
    if (eintr_retries >= 100) return -1;
    if (n == 0) return -1; /* EOF */
    lr->len += (int)n;
    lr->buf[lr->len] = '\0';
    return 0;
}

/* Returns a malloc'd line (without \n), or NULL on EOF/error.
 * Caller must free. */
static char *lr_readline(struct line_reader *lr) {
    for (;;) {
        char *nl = memchr(lr->buf + lr->pos, '\n', lr->len - lr->pos);
        if (nl) {
            int    line_len = (int)(nl - (lr->buf + lr->pos));
            char  *line     = strndup(lr->buf + lr->pos, line_len);
            lr->pos = (int)(nl + 1 - lr->buf);
            return line;
        }
        if (lr->len - lr->pos >= MAX_LINE_LEN - 1) {
            /* line too long, return what we have */
            char *line = strndup(lr->buf + lr->pos, MAX_LINE_LEN - 1);
            lr->pos = lr->len;
            return line;
        }
        if (lr_fill(lr) < 0) {
            /* no more data, return what's left (if any) */
            if (lr->pos < lr->len) {
                char *line = strndup(lr->buf + lr->pos, lr->len - lr->pos);
                lr->pos = lr->len;
                return line;
            }
            return NULL;
        }
    }
}

/* ---- send_line ---- */
static int send_line(int fd, const char *line) {
    size_t len  = strlen(line);
    size_t sent = 0;
    while (sent < len) {
        ssize_t n = send(fd, line + sent, len - sent, MSG_NOSIGNAL);
        if (n < 0) {
            if (errno == EINTR) continue;
            return -1;
        }
        sent += (size_t)n;
    }
    /* send the newline */
    const char nl = '\n';
    while (1) {
        ssize_t n = send(fd, &nl, 1, MSG_NOSIGNAL);
        if (n < 0) {
            if (errno == EINTR) continue;
            return -1;
        }
        break;
    }
    return 0;
}

/* ---- config_init ---- */
static void config_init(void) {
    const char *xdg = getenv("XDG_CONFIG_HOME");
    const char *home = getenv("HOME");
    const char *base = NULL;

    if (xdg && xdg[0]) base = xdg;
    else if (home && home[0]) base = home;
    else {
        fputs("Error: neither XDG_CONFIG_HOME nor HOME is set\n", stderr);
        exit(1);
    }
    int n = snprintf(conf_dir, sizeof(conf_dir), "%s/.config/autotweaker", base);
    if (n < 0 || (size_t)n >= sizeof(conf_dir)) {
        fputs("Error: config path too long\n", stderr);
        exit(1);
    }

    n = snprintf(sock_path, sizeof(sock_path), "%s/cli.sock", conf_dir);
    if (n < 0 || (size_t)n >= sizeof(sock_path)) {
        fputs("Error: socket path too long\n", stderr);
        exit(1);
    }
    n = snprintf(lock_path, sizeof(lock_path), "%s/autotweaker.lock", conf_dir);
    if (n < 0 || (size_t)n >= sizeof(lock_path)) {
        fputs("Error: lock path too long\n", stderr);
        exit(1);
    }
    n = snprintf(env_path, sizeof(env_path), "%s/env", conf_dir);
    if (n < 0 || (size_t)n >= sizeof(env_path)) {
        fputs("Error: env path too long\n", stderr);
        exit(1);
    }

    if (mkdir(conf_dir, 0755) < 0 && errno != EEXIST) {
        fprintf(stderr, "Warning: mkdir %s: %s\n", conf_dir, strerror(errno));
    }
}

/* ---- write_proxy_env ---- */
static void write_proxy_env(void) {
    FILE *f = fopen(env_path, "w");
    if (!f) {
        fprintf(stderr, "Warning: cannot write %s: %s\n",
                env_path, strerror(errno));
        return;
    }
    for (size_t i = 0; i < sizeof(PROXY_VARS) / sizeof(PROXY_VARS[0]); i++) {
        const char *val = getenv(PROXY_VARS[i]);
        if (val && val[0]) fprintf(f, "%s=%s\n", PROXY_VARS[i], val);
    }
    fclose(f);
}

/* ---- daemon helpers ---- */

/* Return the output of a shell command as a malloc'd string (NULL on failure).
 * Strips trailing whitespace. */
static char *shell_read(const char *cmd) {
    FILE *p = popen(cmd, "r");
    if (!p) return NULL;
    char  buf[4096];
    char *result = NULL;
    if (fgets(buf, sizeof(buf), p)) {
        /* strip trailing whitespace */
        size_t len = strlen(buf);
        while (len > 0 && (buf[len - 1] == '\n' || buf[len - 1] == ' ' ||
                           buf[len - 1] == '\t' || buf[len - 1] == '\r'))
            buf[--len] = '\0';
        result = strdup(buf);
    }
    pclose(p);
    return result;
}

/* Run a command, ignore its exit code (like ... || true in bash) */
static void shell_ignore(const char *cmd) {
    int rc = system(cmd);
    (void)rc;
}

/* Return 1 if active, 0 otherwise */
static int daemon_is_active(void) {
    char *state = shell_read(
        "systemctl --user show autotweaker -p ActiveState --value 2>/dev/null");
    if (!state) return 0;
    int active = (strcmp(state, "active") == 0);
    free(state);
    return active;
}

static char *daemon_substate(void) {
    return shell_read(
        "systemctl --user show autotweaker -p SubState --value 2>/dev/null");
}

/* ---- ensure_daemon ---- */
static void ensure_daemon(void) {
    shell_ignore("systemctl --user daemon-reload 2>/dev/null");

    int active = daemon_is_active();
    if (!active) {
        fputs("Starting daemon...\n", stderr);
        shell_ignore(
            "systemctl --user reset-failed autotweaker 2>/dev/null");
        shell_ignore("systemctl --user start autotweaker");

        /* Give systemd a moment to spawn the process, then fail fast
         * if the unit is already dead instead of waiting in poll loop */
        struct timespec ts = {0, 500 * 1000000L};
        nanosleep(&ts, NULL);

        if (!daemon_is_active()) {
            char *sub = daemon_substate();
            int   dead = sub && (strcmp(sub, "failed") == 0 ||
                                 strcmp(sub, "dead") == 0);
            free(sub);
            if (dead) {
                fputs("Daemon failed to start, showing recent logs:\n",
                      stderr);
                fflush(stderr);
                int _rc = system(
                    "journalctl --user -u autotweaker --no-pager -n 30 >&2");
                (void)_rc;
                exit(1);
            }
        }
    }
}

/* ---- wait_for_ready ---- */
static void wait_for_ready(void) {
    struct stat st;
    int        waited = 0;

    while (1) {
        if (stat(sock_path, &st) == 0 && S_ISSOCK(st.st_mode) &&
            stat(lock_path, &st) == 0 && S_ISREG(st.st_mode))
            return;

        char *state_str    = shell_read(
            "systemctl --user show autotweaker -p ActiveState --value 2>/dev/null");
        char *substate_str = daemon_substate();

        const char *state    = state_str    ? state_str    : "";
        const char *substate = substate_str ? substate_str : "";

        /* Case: failed:*, inactive:*, *:auto-restart */
        if (strcmp(state, "failed") == 0 ||
            strcmp(state, "inactive") == 0 ||
            strcmp(substate, "auto-restart") == 0) {
            fputs("Daemon failed to start, showing recent logs:\n", stderr);
            fflush(stderr);
            int _rc = system("journalctl --user -u autotweaker --no-pager -n 30 >&2");
            (void)_rc;
            exit(1);
        }

        free(state_str);
        free(substate_str);

        waited++;
        if (waited >= WARN_ITERATIONS) {
            fputs("Daemon is taking too long, showing recent logs:\n", stderr);
            fflush(stderr);
            int _rc2 = system("journalctl --user -u autotweaker --no-pager -n 15 >&2");
            (void)_rc2;
            waited = 0;
        }

        struct timespec ts = {0, POLL_INTERVAL_MS * 1000000L};
        nanosleep(&ts, NULL);
    }
}

/* ---- build_request ---- */
static char *build_request(int argc, char **argv) {
    cJSON *root = cJSON_CreateObject();
    cJSON_AddStringToObject(root, "type", "cmd");

    cJSON *args = cJSON_AddArrayToObject(root, "args");
    for (int i = 1; i < argc; i++)
        cJSON_AddItemToArray(args, cJSON_CreateString(argv[i]));

    const char *prog = argv[0];
    const char *slash = strrchr(prog, '/');
    if (slash) prog = slash + 1;
    cJSON_AddStringToObject(root, "prog", prog);

    char *json = cJSON_PrintUnformatted(root);
    cJSON_Delete(root);
    return json;
}

/* ---- connect_socket ---- */
static int connect_socket(void) {
    int fd = socket(AF_UNIX, SOCK_STREAM, 0);
    if (fd < 0) {
        perror("socket");
        return -1;
    }

    struct sockaddr_un addr;
    memset(&addr, 0, sizeof(addr));
    addr.sun_family = AF_UNIX;
    size_t plen = strlen(sock_path);
    if (plen >= sizeof(addr.sun_path)) {
        fputs("Error: socket path too long for Unix socket\n", stderr);
        close(fd);
        return -1;
    }
    memcpy(addr.sun_path, sock_path, plen + 1);

    if (connect(fd, (struct sockaddr *)&addr, sizeof(addr)) < 0) {
        fprintf(stderr, "Failed to connect to %s: %s\n",
                sock_path, strerror(errno));
        close(fd);
        return -1;
    }
    return fd;
}

/* ---- handle_prompt: returns a malloc'd reply JSON string ---- */
static char *handle_prompt(const char *prompt_text, int echo) {
    fputs(prompt_text, stderr);
    fflush(stderr);

    char answer[4096];
    answer[0] = '\0';

    if (isatty(STDIN_FILENO)) {
        /* Read from /dev/tty, optionally with echo disabled */
        int tty = open("/dev/tty", O_RDONLY);
        if (tty < 0) goto fallback;

        struct termios old_tio, new_tio;
        if (tcgetattr(tty, &old_tio) < 0) {
            close(tty);
            goto fallback;
        }
        new_tio = old_tio;
        if (!echo) new_tio.c_lflag &= ~(ECHO | ECHONL);
        if (tcsetattr(tty, TCSANOW, &new_tio) < 0) {
            close(tty);
            goto fallback;
        }

        ssize_t n = read(tty, answer, sizeof(answer) - 1);
        if (n > 0) {
            answer[n] = '\0';
            /* strip trailing newline(s) */
            while (n > 0 &&
                   (answer[n - 1] == '\n' || answer[n - 1] == '\r'))
                answer[--n] = '\0';
        }

        tcsetattr(tty, TCSANOW, &old_tio);
        close(tty);
    } else {
    fallback:
        if (!fgets(answer, sizeof(answer), stdin)) {
            answer[0] = '\0';
        } else {
            size_t len = strlen(answer);
            while (len > 0 &&
                   (answer[len - 1] == '\n' || answer[len - 1] == '\r'))
                answer[--len] = '\0';
        }
    }

    cJSON *reply = cJSON_CreateObject();
    cJSON_AddStringToObject(reply, "type", "reply");
    cJSON_AddStringToObject(reply, "text", answer);
    char *json = cJSON_PrintUnformatted(reply);
    cJSON_Delete(reply);
    return json;
}

/* ---- run_protocol ---- */
static int run_protocol(int fd) {
    int                exit_code  = 1;
    struct line_reader lr         = {.fd = fd};

    while (1) {
        char *line = lr_readline(&lr);
        if (!line) break;

        cJSON *root = cJSON_Parse(line);
        free(line);
        if (!root) break;

        cJSON *type = cJSON_GetObjectItemCaseSensitive(root, "type");
        if (!type || !cJSON_IsString(type)) {
            cJSON_Delete(root);
            break;
        }

        const char *t = type->valuestring;

        if (strcmp(t, "data") == 0) {
            cJSON *text    = cJSON_GetObjectItemCaseSensitive(root, "text");
            cJSON *channel = cJSON_GetObjectItemCaseSensitive(root, "channel");
            cJSON *newline = cJSON_GetObjectItemCaseSensitive(root, "newline");

            const char *txt = cJSON_IsString(text) ? text->valuestring : "";
            const char *ch  =
                cJSON_IsString(channel) ? channel->valuestring : "stdout";
            int nl = !cJSON_IsFalse(newline);

            if (strcmp(ch, "stderr") == 0) {
                fputs(txt, stderr);
                if (nl) fputc('\n', stderr);
            } else {
                fputs(txt, stdout);
                if (nl) fputc('\n', stdout);
            }
            fflush(stdout);
            fflush(stderr);
        } else if (strcmp(t, "done") == 0) {
            cJSON *ec = cJSON_GetObjectItemCaseSensitive(root, "exitCode");
            exit_code = cJSON_IsNumber(ec) ? ec->valueint : 0;
            cJSON_Delete(root);
            break;
        } else if (strcmp(t, "prompt") == 0) {
            cJSON *ptext = cJSON_GetObjectItemCaseSensitive(root, "text");
            const char *pt =
                cJSON_IsString(ptext) ? ptext->valuestring : "";
            cJSON *pecho = cJSON_GetObjectItemCaseSensitive(root, "echo");
            int echo = !cJSON_IsFalse(pecho);

            char *reply = handle_prompt(pt, echo);
            if (send_line(fd, reply) < 0) {
                free(reply);
                cJSON_Delete(root);
                break;
            }
            free(reply);
        } else {
            cJSON_Delete(root);
            break;
        }
        cJSON_Delete(root);
    }
    return exit_code;
}

/* ---- handle_daemon_cmd: intercept -d <action>, return exit code ---- */
static int handle_daemon_cmd(const char *action) {
    static const char *const ACTIONS[] = {
        "start",   "stop",     "restart",   "reload",   "try-restart",
        "enable",  "disable",  "mask",      "unmask",   "reenable",
        "status",  "is-active","is-enabled","is-failed",
        NULL,
    };

    for (int i = 0; ACTIONS[i]; i++) {
        if (strcmp(action, ACTIONS[i]) == 0) {
            pid_t pid = fork();
            if (pid == -1) return 1;
            if (pid == 0) {
                execlp("systemctl", "systemctl", "--user", action,
                       "autotweaker", (char *)NULL);
                _exit(127);
            }
            int wstatus;
            if (waitpid(pid, &wstatus, 0) == -1) return 1;
            return WIFEXITED(wstatus) ? WEXITSTATUS(wstatus) : 1;
        }
    }

    /* unknown action, not handled — caller should pass through */
    return -1;
}

/* ---- main ---- */
int main(int argc, char **argv) {
    signal(SIGPIPE, SIG_IGN);

    /* intercept autotweaker -d <action> — manage daemon directly */
    if (argc == 3 && strcmp(argv[1], "-d") == 0) {
        int rc = handle_daemon_cmd(argv[2]);
        if (rc >= 0) return rc;
        /* unknown action falls through to socket passthrough */
    }

    config_init();
    write_proxy_env();
    ensure_daemon();
    wait_for_ready();

    char *request = build_request(argc, argv);
    int   fd      = connect_socket();
    if (fd < 0) {
        free(request);
        return 1;
    }

    if (send_line(fd, request) < 0) {
        free(request);
        close(fd);
        return 1;
    }
    free(request);

    int exit_code = run_protocol(fd);
    close(fd);
    return exit_code;
}

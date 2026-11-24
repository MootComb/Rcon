# RCON Plugin

<h1 align="center">RCON</h1>

<p align="center">
  <img src="https://img.shields.io/badge/Minecraft-1.13%2B-brightgreen" alt="Minecraft Version">
  <img src="https://img.shields.io/badge/Java-8%2B-orange" alt="Java Version">
  <img src="https://img.shields.io/badge/License-GPL%203.0-blue" alt="License">
</p>

A lightweight Minecraft plugin that lets you send RCON commands to remote servers directly from in-game or the console. Configure multiple servers, control access via permissions, and get responses back in chat, all without leaving the game.

---

## Features


- **Native RCON protocol implementation** — Native implementation packet handling
- **Lightweight** — No dependencies, minimal performance impact
- **Secure** — Password never exposed in chat, only used for authentication
- **Error handling** — Distinguishes auth failures from general connection errors with configurable messages
- **Multiple server support** — Configure any number of remote servers with individual addresses and passwords
- **In-game command execution** — Send RCON commands to any configured server
- **Async execution** — Commands run asynchronously so the main thread never blocks (with automatic fallback on older servers)
- **Configurable messages** — All responses, errors, and usage messages are customizable
- **Permission control** — Separate permissions for using, sending, and reloading

---

## How to Compile

### Prerequisites
- **Java 8** or higher
- **Maven 3.6+** or higher

### Build Instructions

```bash
# Clone the repository
git clone https://github.com/MootComb/Rcon.git

# Navigate to the project directory
cd Rcon

# Build with Maven
mvn clean package
```

### Output
The compiled JAR file will be located in the `target/` directory as `Rcon-<version>.jar`.

### Installation
1. Copy the JAR file to your server's `plugins/` folder
2. Restart your server or use a plugin manager
3. Configure the `config.yml` file with your servers
4. Reload the config with `/rcon reload`

---

## Configuration

### Server List

```yaml
servers:
  lobby:
    address: "127.0.0.1:25575"
    password: "your_rcon_password"
  survival:
    address: "192.168.1.10:25575"
    password: "another_password"
```

> Each server must have `enable-rcon=true` and a matching `rcon.password` in its `server.properties`.

### Messages

```yaml
messages:
  prefix: "&8[&cRCON&8]&r "
  help: "%prefix%&fUsage: &c/rcon <send|reload|help>"
  commandUsage: "%prefix%&cUsage: /rcon send <server> <command>"
  commandSendUsage: "%prefix%&cUsage: /rcon send <server> <command>"
  permission-message: "%prefix%&cYou don't have permission."
  send:
    permission-message: "%prefix%&cYou don't have permission to send RCON commands."
  reload:
    permission-message: "%prefix%&cYou don't have permission to reload."
  responseFromServer: "%prefix%&a[%s] &f%r"
  serverNotFound: "%prefix%&cServer not found: %s"
  invalidPort: "%prefix%&cInvalid port for server: %s"
  rconAuthError: "%prefix%&cAuthentication failed on %s"
  rconError: "%prefix%&cError contacting %s"
  settingsReloaded: "%prefix%&aConfiguration reloaded."
  plugin:
    enabled: "RCON plugin enabled."
    disabled: "RCON plugin disabled."
  errors:
    socketOpen: "Failed to connect to %s:%s"
    socketClose: "Failed to close socket"
    write: "Failed to write %s bytes"
    read: "Failed to read %s bytes"
    readMismatch: "Read mismatch: expected %s, got %s"
    nullBytes: "Invalid null bytes: %s, %s"
    authFailure: "Authentication failed"
    idMismatch: "Request ID mismatch: %s != %s"
```

---

## Commands

| Command | Description | Permission |
|---------|-------------|------------|
| `/rcon help` | Show help message | `rcon.use` |
| `/rcon send <server> <command>` | Send an RCON command to the given server | `rcon.send` |
| `/rcon reload` | Reload configuration and server list | `rcon.reload` |

---

## Permissions

| Permission | Description | Default |
|------------|-------------|---------|
| `rcon.use` | Base permission to use `/rcon` | op |
| `rcon.send` | Allows `/rcon send` | op |
| `rcon.reload` | Allows `/rcon reload` | op |

---

## Quick Start

1. **Install the plugin** — Place the JAR in your `plugins/` folder
2. **Configure** — Add your servers to `config.yml`
3. **Enable RCON** — Make sure each remote server has `enable-rcon=true` in `server.properties`
4. **Reload** — Use `/rcon reload` to apply changes
5. **Send** — Run `/rcon send <server> <command>` and get the response in chat

---

## How It Works

### Protocol
The plugin implements the RCON protocol natively:
- **Packet format:** `<size:4><id:4><type:4><body><null:1><null:1>` (little-endian)
- **Auth:** `type=3`, server replies with `id=-1` on failure or `id=<request>` on success
- **Command:** `type=2`, response echoes the request ID

### Execution Flow
1. Parse the server address (`host:port`)
2. Open a `SocketChannel` to the remote server
3. Authenticate with the configured password
4. Send the command payload
5. Read the response and print it back to the sender
6. Close the channel

---

## Support

For issues, suggestions, or questions:
- **GitHub Issues:** [https://github.com/MootComb/Rcon/issues](https://github.com/MootComb/Rcon/issues)

---

## License

This project is licensed under the **GNU Affero General Public License v3.0** – see the [LICENSE](LICENSE) file for details.

---

## 🗺️ Roadmap

Planned and proposed features, roughly ordered by priority.

### 🎨 Cosmetic / UX
- [x] **Tab completion** — server names and subcommands auto-complete
- [ ] **Pretty-printed responses** — wrap long responses across multiple lines
- [ ] **History** — `/rcon history` to recall recently sent commands

### 🛡️ Access & Security
- [x] **Granular permissions** — separate `use`, `send`, `reload`
- [ ] **Per-server permissions** — `rcon.send.<server>` for fine-grained control
- [ ] **Rate limiting** — prevent command spam
- [ ] **Audit log** — log all sent commands with sender and timestamp

### ⚙️ Gameplay / Utility
- [ ] **Console support** — send commands from server console without a player sender
- [ ] **Scheduled commands** — planned RCON execution
- [ ] **Preset command aliases** — `/rcon run <alias>` for frequent commands

### 🔌 Integrations
- [ ] **PlaceholderAPI** — expose last response, server status, etc.
- [ ] **Connection pooling** — keep connections alive for faster repeated sends

### 📊 Logging & Admin
- [x] **Configurable messages** — all messages in `config.yml`
- [ ] **Debug mode** — verbose packet-level logging
- [ ] **Metrics** — command count, success/failure ratio per server

### 🔧 Utilities
- [ ] **Import/export** — backup/restore server list
- [ ] **Server status check** — `/rcon ping <server>` to check reachability
- [ ] **Response file export** — save long responses to a file

### 🌍 Localization
- [ ] **Multi-language support** — `lang/en.yml`, `lang/ru.yml`, etc.
- [ ] **Per-player language selection** — `/rcon lang <code>`

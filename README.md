## 🚀 Getting Started

To use Figura Unchained, you need to connect to a compatible custom backend. You can use the official public server provided for the community:

**Public Server IP:** `figura-unchained.alwaysdata.net`

**Installation & Cloud Connect**
1. Download this addon([Modrinth](https://modrinth.com/mod/figura-unchained))
2. Install a compatible version of the original [Figura mod](https://modrinth.com/mod/figura) (or compile from the [Official GitHub](https://github.com/FiguraMC/Figura)).
3. Launch Minecraft and join any world or server.
4. Open the Figura mod settings.
5. In the mod settings, set the Figura Cloud IP address to: `figura-unchained.alwaysdata.net` (or set up your own backend).
6. Use the in-game commands to authenticate if needed.

---

## 📦 Requirements & Dependencies

This mod is an addon and requires the following to work:
* **Minecraft:** 1.21.10
* **Fabric Loader:** >=0.19.3
* **Java:** 21
* **Compatible Figura:** [GitHub](https://github.com/FiguraMC/Figura)/[Modrinth](https://modrinth.com/mod/figura)

---

## ⚙️ Features & Usage

**Custom Authentication**
This mod features a custom, independent authentication system designed specifically for custom backend servers, completely separate from the official Figura ecosystem. 

**In-Game Commands**
Once connected to a backend, use these commands in the Minecraft chat to manage your session:
* `/figura-unchained register <password>` — Register your account on the current backend.
* `/figura-unchained login <password>` — Log in to your existing account.

---

## 🛡️ Security & Privacy

> **Security Note:** This mod uses an independent authentication system for custom servers. It **DOES NOT** use your Minecraft/Microsoft password. Please use a unique password. Your key is securely hashed locally using PBKDF2 before being sent to the server.

**Privacy & Transparency**
* The addon **does not** collect any personal telemetry or data from your game.
* The only information transmitted is your connection IP address, which is standard for any web server. This IP is only visible to the owner of the backend you connect to.
* You choose which servers to trust. The creator of this addon assumes no liability for the actions, data retention, or security of third-party backends.

---

## ⚠️ Rules & Moderation (Public Server)

If you are using the public `figura-unchained.alwaysdata.net` server:
Uploading malicious, illegal, or NSFW content is **strictly prohibited**. The IP addresses of violators are securely logged by the backend owner and may be handed over to the respective Internet Service Provider (ISP) in case of severe abuse. 

---

## 🖥️ Host Your Own Server

Want total control? You don't have to use the public server. You can host your own Figura Unchained backend for free for your friends or a private SMP. 

**[Get the Backend source code on GitHub](https://github.com/bulbat0n/figura-unchained-backend)**

---

## ❗ Disclaimer

**This mod is NOT an official product of the Figura team and is NOT supported by them.** 
Figura Unchained is a third-party modification. Please do not bother the official Figura developers with issues, bugs, or questions related to this custom backend addon.

Official Figura resources:
[GitHub](https://github.com/FiguraMC/Figura)
[Modrinth](https://modrinth.com/mod/figura)

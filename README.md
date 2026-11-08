## This is the addon repository. The backend repository is [here](https://github.com/bulbat0n/figura-unchained-backend)

## 🚀 Getting Started

Figura Unchained connects the original Figura mod to a compatible custom backend. Out of the box it uses the official public server provided for the community.

**Installation**
1. Download this addon ([Modrinth](https://modrinth.com/mod/figura-unchained)).
2. Install a compatible version of the original [Figura mod](https://modrinth.com/mod/figura) (or compile from the [Official GitHub](https://github.com/FiguraMC/Figura)).
3. Launch Minecraft. On the title screen the addon asks two questions, and it makes no network connection until you answer the first one:
   1. **May it download a small signed file from GitHub?** The file tells the addon where the public server is.
   2. **May it connect to the address from that file?** The address is shown to you in the second window.
4. Answer **Yes** to both to use the public server. Answering **No** to either keeps the addon on `localhost:55000`, a server on your own computer.
5. Join any world or server and use the in-game commands to register or log in.

You can change your answers at any time with `/figura-unchained consent`.

**Using a different server**
Open the Figura settings and enter the server address in the *Figura Cloud IP* field. Any value other than `default` is used as it is: the addon does not contact GitHub, does not ask for the second approval and does not check the server's identity. Set the field back to `default` to return to the public server. On the very first launch the field is replaced with `default` once (the window tells you if that changes your value), so enter your own address after answering the first-launch window.

If you have any issues, you can contact me

[![Discord](https://img.shields.io/badge/Discord-bulbat0n-%235865F2?style=flat-square&logo=discord&logoColor=white)](https://discord.com/users/1496534564842246306)

---

## 📦 Requirements & Dependencies

This mod is an addon and requires the following to work:
* **Minecraft:** 1.21.1
* **Fabric Loader:** >=0.19.5 or **NeoForge:** >= 21.1.100
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
* `/figura-unchained consent` — Change your answers about GitHub and the public server address.
* `/figura-unchained report <player> <reason>` — Report a player's avatar to the backend owner. Tab completion lists the players whose avatars you have loaded. You must be logged in, and you can send up to 5 reports per day.

---

## 🛡️ Security & Privacy

> **Security Note:** This mod uses an independent authentication system for custom servers. It **DOES NOT** use your Minecraft/Microsoft password. Please use a unique password. Your key is securely hashed locally using PBKDF2 before being sent to the server.

**What is sent, and to whom**

| Who | When | What they see |
|---|---|---|
| GitHub | Only if you answered Yes to the first question, each time the game starts | Your IP address and that you use Figura Unchained (the request carries a `FiguraUnchained/<version>` user agent). The addon downloads one small file from the `bulbat0n/figura-unchained-endpoint` repository. |
| The public server and its hosting provider | Only after you approved its address | Your nickname, UUID, IP address, avatars, login token and password hash. |
| A custom server you entered yourself | Whenever you use it | The same data as the public server, seen by whoever runs that server. |

Your password itself never leaves your computer: the addon sends a PBKDF2 hash derived from it.

**How the connection is protected**
* The file on GitHub is signed. The addon contains the public keys and ignores anything that does not match them.
* If the public server address changes, the addon asks you again and makes no connection to the new address until you answer.
* The public server proves its identity with a key listed in the signed file. If the proof fails, the addon refuses to talk to it. This check is skipped for custom addresses.

**Files on your computer**
* `config/figura-unchained/consent.json` — your answers and the last approved address.
* `config/figura-unchained/<uuid>.txt` — your login token, stored as plain text. Keep your game folder private.

**Privacy & Transparency**
* The addon **does not** collect any telemetry or data from your game beyond what is listed above.
* You choose which servers to trust. The creator of this addon assumes no liability for the actions, data retention, or security of third-party backends.

---

## ⚠️ Rules & Moderation (Public Server)

If you are using the public `figura-unchained.alwaysdata.net` server:
Uploading malicious, illegal, or NSFW content is **strictly prohibited**. The IP addresses of violators are securely logged by the backend owner and may be handed over to the respective Internet Service Provider (ISP) in case of severe abuse.

Players can report an avatar with `/figura-unchained report <player> <reason>`. Reports are reviewed by the backend owner.

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

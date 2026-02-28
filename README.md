<div align="center">
  <h1>❄️ Whiteout Survival Bot (WOS Bot)</h1>
  <p><strong>Advanced Auto Bot, Macro, and Management Script for Whiteout Survival</strong></p>

  [![Discord](https://img.shields.io/badge/Discord-%235865F2.svg?style=for-the-badge&logo=discord&logoColor=white)](https://discord.gg/sUthSHRVvU)
  [![Buy Me A Coffee](https://img.shields.io/badge/Buy_Me_A_Coffee-FFDD00?style=for-the-badge&logo=buy-me-a-coffee&logoColor=black)](https://buymeacoffee.com/Shederator)
</div>

---

## 📖 About The Project

**Whiteout Survival Bot (WOS Bot)** is a completely **free and open-source** automated assistant, macro, and scripting tool designed to streamline your gameplay in Whiteout Survival. Whether you are managing multiple accounts or looking to optimize your daily tasks, this comprehensive auto bot reliably handles routine operations so you can focus on strategy and community without any paywalls or subscriptions.

> **⚠️ Acknowledgment:** This project is built upon the excellent foundational work of [wosbot by camoloqlo](https://github.com/camoloqlo/wosbot). It is continually developed and expanded in free time with new features and improvements.

---

## ✨ Key Features

This bot is packed with features to automate nearly every aspect of the game.

### ⚔️ Combat & Events
- **Arena Battles**: Automatically fight your Arena matches.
- **Polar Terror Hunting**: Join or initiate hunts seamlessly.
- **Bear Trap**: Participate and maximize your damage.
- **Alliance Rallies**: Auto-join alliance rallies and configurable manual rallies.
- **Tundra Truck Event**: Manage the "My Trucks" section efficiently.

### 🏰 City Management
- **Troop Training**: Automatically trains and promotes troops to higher tiers.
- **Intel & Experts**: Complete intel missions and manage expert interactions.
- **Resource Gathering**: Keeps your march queues busy with constant, optimal gathering.
- **Hero Recruitment**: Automatically claim daily hero recruitment options.
- **Fire Crystals & Shards**: Collect resources from the Crystal Laboratory and War Academy.

### 🐾 Pets & Exploration
- **Pet Adventure & Skills**: Manage food, treasure, and stamina automatically.
- **Tundra Trek & Journey of Light**: Automate these exploration events with optimized routing.
- **Exploration Chests**: Automatically open and claim rewards.

### 🤝 Alliance & Social
- **Alliance Tech & Chests**: Contribute to tech and claim alliance gifts.
- **Mail & Online Rewards**: Keep your inbox clean and claim time-based rewards.
- **Nomadic Merchant**: Process merchant trades efficiently.

### ⚙️ Advanced Bot Capabilities
- **Multi-Profile Support**: Run and manage multiple accounts simultaneously.
- **Preemption System**: Intelligent idle monitoring ensures time-sensitive events are never missed.
- **Injection System**: Dynamically inject quick operations (e.g., Alliance Help or Furnace Upgrades) into the active queue without disrupting current tasks.
- **Debugging Tab**: Advanced UI providing real-time logs and bot troubleshooting tools.

---

## 🎬 Showcase & Media

<details>
<summary><b>🎥 Click to view the Video Showcase</b></summary>
<br>

[![Video Showcase](./images/picture_yt.png)](https://www.youtube.com/watch?v=Nnjv68xiIV0)

</details>

<details>
<summary><b>📸 Click to view Screenshots</b></summary>
<br>

| | |
|:---:|:---:|
| ![Interface 1](./images/picture1.png) | ![Interface 2](./images/picture2.png) |
| ![Interface 3](./images/picture3.png) | ![Interface 4](./images/picture4.png) | 
| ![Interface 5](./images/picture5.png) | ![Interface 6](./images/picture6.png) |
| ![Interface 7](./images/picture7.png) | ![Interface 8](./images/picture8.png) |
| ![Interface 9](./images/picture9.png) | ![Interface 10](./images/picture10.png) |
| ![Interface 11](./images/picture11.png) | ![Interface 12](./images/picture12.png) |
| ![Interface 13](./images/picture13.png) | |

</details>

---

## 🛠️ Installation & Setup

Follow these steps to compile and run the bot from the source code.

### 1️⃣ Prerequisites

Ensure you have the following installed on your system:
- **Java (JDK 17 or newer)**: [Download Adoptium Temurin](https://adoptium.net/)
- **Apache Maven**: [Download Maven](https://maven.apache.org/install.html)

<details>
<summary><b>Windows Users: Adding Java & Maven to PATH</b></summary>

1. Press **Win + R**, type `sysdm.cpl`, and press **Enter**.
2. Go to **Advanced → Environment Variables**.
3. Under **System variables**, select `Path`, and click **Edit**.
4. Add the `bin` directories of your Java and Maven installations. For example:
   ```text
   C:\Program Files\Eclipse Adoptium\jdk-17\bin
   C:\apache-maven-3.9.9\bin
   ```
5. Click **OK** and restart your terminal. Verify configuration by running `java -version` and `mvn -version` in your command prompt.
</details>

### 2️⃣ Compilation

Open your terminal in the root folder of the project and run the following command to build the executable:

```sh
mvn clean install package
```

Once completed, the compiled `.jar` file will be located in the `wos-hmi/target` directory (e.g., `wos-bot-1.7.0.jar`).

### 3️⃣ Running the Bot

We highly recommend running the bot via the command line to access real-time logs for easier debugging.

```sh
# Navigate to the target directory
cd wos-hmi/target

# Run the compiled jar (replace X.X.X with your version)
java -jar wos-bot-X.X.X.jar
```
*(Alternatively, you can double-click the `.jar` file from your file explorer, but console logs will not be visible.)*

---

## 📱 Emulator Configuration

The bot interfaces with your Android emulator. It officially supports **MuMu Player**, **MEmu**, and **LDPlayer 9**.

### Emulator Executable Paths

When prompted by the bot launcher, select the **command-line controller** (not the graphical app) for your emulator. Default paths are typically:

- **MuMu Player**:
  - `C:\Program Files\Netease\MuMuPlayerGlobal-12.0\shell\MuMuManager.exe`
  - `C:\Program File\Netease\MuMuPlayer\nx_main\MuMuManager.exe`
- **MEmu**:
  - `C:\Program Files\Microvirt\MEmu\memuc.exe`
- **LDPlayer 9**:
  - `C:\LDPlayer\LDPlayer9\ldconsole.exe`

> **Note for LDPlayer Users:** You must manually enable ADB in the instance settings (`Settings → Other settings → ADB debugging = Enable local connection`), or the bot will fail to connect.

### Required Instance Settings

For maximum reliability and image recognition accuracy, configure your emulator instance exactly as follows:
- **Resolution**: `720x1280` (Portrait) at **320 DPI** *(Mandatory)*
- **Language**: English *(Mandatory)*
- **Resources**: 2 CPU Cores / 2 GB RAM (Recommended)

> **Pro Tip:** In the game's settings, disable the *Snowfall* and *Day/Night Cycle* options, and avoid using *Ultra* graphics. This considerably improves performance and visual reliability for the bot.

---

## 🚀 Roadmap

We are continuously working to expand the bot's capabilities. Planned features include:

- 🎣 **Fishing Event Automation**
- 🦁 **Advanced Beast Hunt**
- 🎁 **Automatic Giftcode Discovery & Redemption**
- � **Discord, Telegram & WhatsApp Integrations**
- �️ **Alliance Management Tools**
- 🧠 **AI Integration** (Handling unprogrammable logic and dynamic tasks)

---

<div align="center">
  <p>Developed with ❤️ for the Whiteout Survival community.</p>
  <p>Have suggestions or need help? <a href="https://discord.gg/sUthSHRVvU">Join our Discord!</a></p>
</div>

---
<!-- SEO Keywords: Whiteout Survival Bot, WOS Bot, Whiteout Survival Macro, Free WOS Bot, Whiteout Survival Auto Script, Open Source WOS Bot, Whiteout Survival Automation, Auto Join Rallies, Whiteout Survival PC Bot, WOS Auto Farm -->

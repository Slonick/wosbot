package cl.camodev.wosbot.tgwatcher;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Properties;

/**
 * Always-on standalone Telegram watcher.
 *
 * This process runs permanently (started at Windows login via the Startup
 * folder or HKCU\Run) and is completely independent of the main bot JAR.
 *
 * It polls the Telegram Bot API and reacts to launcher commands:
 *
 *   /launch   — start the main bot JAR if it is not already running
 *   /kill     — terminate the main bot JAR process
 *   /wstatus  — report whether the main bot JAR process is alive
 *   /whelp    — list these commands
 *
 * Commands /startbot, /stopbot, /status are intentionally IGNORED here —
 * they are handled by the TelegramBotService running inside the main app.
 *
 * Configuration is read from: %USERPROFILE%\.wosbot\telegram-watcher.properties
 * (written automatically by the Telegram config panel in the main bot UI)
 *
 *   token=<BotFather token>
 *   chatId=<your Telegram numeric chat-ID>
 *   botJarPath=<absolute path to wos-bot-x.x.x.jar>
 */
public class TelegramWatcher {

    private static final Logger logger = LoggerFactory.getLogger(TelegramWatcher.class);
    private static final String API_BASE = "https://api.telegram.org/bot";
    private static final int    LONG_POLL_TIMEOUT = 30;

    /** Config file location: %USERPROFILE%/.wosbot/telegram-watcher.properties */
    public static Path configFilePath() {
        return Paths.get(System.getProperty("user.home"), ".wosbot", "telegram-watcher.properties");
    }

    // ── entry point ───────────────────────────────────────────────────────────

    public static void main(String[] args) throws Exception {
        System.out.println("==============================================");
        System.out.println("  WOS Bot – Telegram Watcher");
        System.out.println("  Config: " + configFilePath());
        System.out.println("==============================================");

        Properties cfg = loadConfig();
        String token  = cfg.getProperty("token",  "").trim();
        String chatId = cfg.getProperty("chatId", "").trim();
        String jarPath = cfg.getProperty("botJarPath", "").trim();

        if (token.isBlank()) {
            System.err.println("[ERROR] 'token' is not set in " + configFilePath());
            System.err.println("        Open the bot, go to Telegram panel, enter token and save.");
            System.exit(1);
        }
        if (jarPath.isBlank()) {
            System.err.println("[ERROR] 'botJarPath' is not set in " + configFilePath());
            System.err.println("        Open the bot, go to Telegram panel, enter the JAR path and save.");
            System.exit(1);
        }

        long allowedChatId = 0;
        if (!chatId.isBlank()) {
            try {
                allowedChatId = Long.parseLong(chatId);
            } catch (NumberFormatException e) {
                System.err.println("[WARN] chatId '" + chatId + "' is not a valid number – all chats will be rejected");
            }
        }

        new TelegramWatcher(token, allowedChatId, jarPath).run();
    }

    // ── instance ──────────────────────────────────────────────────────────────

    private final String       token;
    private final long         allowedChatId;
    private final File         botJar;
    private final File         botJarDir;
    private final HttpClient   httpClient;
    private final ObjectMapper mapper = new ObjectMapper();

    private long    lastUpdateId = -1;
    private Process botProcess   = null;   // the process we launched (null if we didn't launch it)

    // Exponential back-off for network errors (e.g. DNS not ready after PC restart)
    private static final long BACKOFF_INITIAL_MS  = 5_000;   //  5 s
    private static final long BACKOFF_MAX_MS      = 60_000;  // 60 s
    private long currentBackoffMs = BACKOFF_INITIAL_MS;

    private TelegramWatcher(String token, long allowedChatId, String jarPath) {
        this.token         = token;
        this.allowedChatId = allowedChatId;
        this.botJar        = new File(jarPath);
        this.botJarDir     = botJar.getParentFile();
        this.httpClient    = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    // ── polling loop ──────────────────────────────────────────────────────────

    private void run() {
        logger.info("Watcher started. Polling Telegram for /launch, /kill, /wstatus commands.");
        System.out.println("[INFO] Watcher running. Waiting for Telegram commands...");
        System.out.println("[INFO] Send /whelp to your bot to list available launcher commands.");

        while (!Thread.currentThread().isInterrupted()) {
            try {
                String url = API_BASE + token
                        + "/getUpdates?timeout=" + LONG_POLL_TIMEOUT
                        + (lastUpdateId >= 0 ? "&offset=" + (lastUpdateId + 1) : "");

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(Duration.ofSeconds(LONG_POLL_TIMEOUT + 10))
                        .GET()
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    // Success — reset back-off
                    if (currentBackoffMs != BACKOFF_INITIAL_MS) {
                        logger.info("Network recovered. Resuming normal polling.");
                    }
                    currentBackoffMs = BACKOFF_INITIAL_MS;

                    JsonNode root = mapper.readTree(response.body());
                    if (root.path("ok").asBoolean()) {
                        for (JsonNode update : root.path("result")) {
                            processUpdate(update);
                            long uid = update.path("update_id").asLong(-1);
                            if (uid >= 0) lastUpdateId = uid;
                        }
                    }
                } else if (response.statusCode() == 409) {
                    // Another getUpdates consumer (TelegramBotService in the app)
                    // is already polling this token. Back off and let it handle things.
                    logger.info("HTTP 409 — bot app is handling Telegram commands. Backing off for {}s.", currentBackoffMs / 1000);
                    Thread.sleep(currentBackoffMs);
                    currentBackoffMs = Math.min(currentBackoffMs * 2, BACKOFF_MAX_MS);
                } else {
                    logger.warn("Unexpected HTTP {}", response.statusCode());
                    Thread.sleep(5_000);
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (java.net.ConnectException | java.nio.channels.UnresolvedAddressException e) {
                // Network not ready (common right after PC restart)
                logger.warn("Network not available — retrying in {}s …", currentBackoffMs / 1000);
                try { Thread.sleep(currentBackoffMs); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                currentBackoffMs = Math.min(currentBackoffMs * 2, BACKOFF_MAX_MS);
            } catch (Exception e) {
                logger.error("Poll error [{}]: {}", e.getClass().getSimpleName(), e.getMessage(), e);
                try { Thread.sleep(5_000); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
            }
        }
    }

    // ── command handling ──────────────────────────────────────────────────────

    private void processUpdate(JsonNode update) {
        JsonNode message = update.path("message");
        if (message.isMissingNode()) return;

        long chatId = message.path("chat").path("id").asLong(-1);
        if (chatId < 0) return;

        // Security gate
        if (allowedChatId != 0 && chatId != allowedChatId) {
            logger.debug("Rejected message from unauthorized chat-ID {}", chatId);
            sendMessage(chatId, "⛔ Unauthorized. Your chat ID is: " + chatId);
            return;
        }

        String text = message.path("text").asText("").trim().toLowerCase();

        // ── Watcher-owned commands ────────────────────────────────────────────
        if (text.startsWith("/launch")) {
            handleLaunch(chatId);
        } else if (text.startsWith("/kill")) {
            handleKill(chatId);
        } else if (text.startsWith("/wstatus")) {
            handleWStatus(chatId);
        } else if (text.startsWith("/whelp") || text.startsWith("/help")) {
            sendMessage(chatId, buildHelpMessage());
        }
        // All other commands (/startbot, /stopbot, /status, /help …) are
        // intentionally not handled here — TelegramBotService inside the running
        // app handles them. We simply advance the offset and move on.
    }

    // ── action handlers ───────────────────────────────────────────────────────

    private void handleLaunch(long chatId) {
        // Check if already running
        if (isBotProcessAlive()) {
            sendMessage(chatId, "ℹ️ Bot app is already running.");
            return;
        }

        if (!botJar.exists()) {
            sendMessage(chatId, "❌ JAR not found at:\n`" + botJar.getAbsolutePath() + "`\nCheck the path in the Telegram config panel.");
            return;
        }

        try {
            String javaExe = ProcessHandle.current().info().command()
                    .orElse("java"); // reuse same JVM if possible

            ProcessBuilder pb = new ProcessBuilder(javaExe, "-jar", botJar.getName());
            pb.directory(botJarDir);
            pb.redirectErrorStream(false);
            // Detach stdout/stderr so they don't block this process
            pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
            pb.redirectError(ProcessBuilder.Redirect.DISCARD);

            botProcess = pb.start();
            logger.info("Launched bot process PID {}", botProcess.pid());
            sendMessage(chatId, "▶️ Bot app launched! (PID " + botProcess.pid() + ")\n"
                    + "Use /startbot to start the automation once the app has loaded.");
        } catch (IOException e) {
            logger.error("Failed to launch bot: {}", e.getMessage());
            sendMessage(chatId, "❌ Failed to launch bot: " + e.getMessage());
        }
    }

    private void handleKill(long chatId) {
        if (!isBotProcessAlive()) {
            sendMessage(chatId, "ℹ️ Bot app is not running.");
            return;
        }

        // Try to kill the process we launched; fall back to killing by JAR name
        if (botProcess != null && botProcess.isAlive()) {
            botProcess.destroyForcibly();
            logger.info("Killed tracked bot process PID {}", botProcess.pid());
            sendMessage(chatId, "⏹️ Bot app has been terminated.");
        } else {
            // We didn't launch it (was already running before watcher started)
            // Try to find and kill it by JAR name
            boolean killed = ProcessHandle.allProcesses()
                    .filter(ph -> ph.info().command().map(c -> c.toLowerCase().contains("java")).orElse(false))
                    .filter(ph -> ph.info().arguments().map(
                            args -> {
                                for (String a : args) {
                                    if (a.contains(botJar.getName())) return true;
                                }
                                return false;
                            }).orElse(false))
                    .findFirst()
                    .map(ph -> { ph.destroyForcibly(); return true; })
                    .orElse(false);

            if (killed) {
                sendMessage(chatId, "⏹️ Bot app has been terminated.");
            } else {
                sendMessage(chatId, "⚠️ Could not find a running bot process to kill.");
            }
        }
    }

    private void handleWStatus(long chatId) {
        if (isBotProcessAlive()) {
            String pid = botProcess != null ? " (PID " + botProcess.pid() + ")" : "";
            sendMessage(chatId, "✅ Bot app is *running*" + pid + ".");
        } else {
            sendMessage(chatId, "🔴 Bot app is *not running*. Send /launch to start it.");
        }
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    /**
     * Returns true if the bot process we launched is still alive, OR if any
     * Java process running the bot JAR is found (covers the case where the app
     * was started manually before the watcher began).
     */
    private boolean isBotProcessAlive() {
        if (botProcess != null && botProcess.isAlive()) {
            return true;
        }
        // Also scan all processes in case the app was started some other way
        return ProcessHandle.allProcesses()
                .filter(ph -> ph.info().arguments().map(args -> {
                    for (String a : args) {
                        if (a.contains(botJar.getName())) return true;
                    }
                    return false;
                }).orElse(false))
                .findFirst()
                .isPresent();
    }

    private static String buildHelpMessage() {
        return
            "╔══════════════════════════╗\n"
          + "║   🤖  WOS Bot  •  Help   ║\n"
          + "╚══════════════════════════╝\n"
          + "\n"
          + "🚀 *Process Control* _(watcher – always on)_\n"
          + "`/launch`      — Start the bot app (open the JAR)\n"
          + "`/kill`        — Force-close the bot app\n"
          + "`/wstatus`     — Check if the bot app is running\n"
          + "\n"
          + "⚙️ *Automation Control* _(requires app to be running)_\n"
          + "`/startbot`    — Begin the automation routines\n"
          + "`/stopbot`     — Pause the automation routines\n"
          + "`/status`      — Show whether automation is running\n"
          + "`/screenshot`  — Capture & send emulator screen\n"
          + "\n"
          + "❓ *Other*\n"
          + "`/help`        — Show this message\n"
          + "\n"
          + "_Tip: use /launch first if the app is closed, then /startbot once it loads._";
    }

    private void sendMessage(long chatId, String text) {
        try {
            ObjectNode body = mapper.createObjectNode();
            body.put("chat_id", chatId);
            body.put("text", text);
            body.put("parse_mode", "Markdown");

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(API_BASE + token + "/sendMessage"))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                    .build();
            httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            logger.error("sendMessage failed: {}", e.getMessage());
        }
    }

    private static Properties loadConfig() throws IOException {
        Path cfg = configFilePath();
        if (!Files.exists(cfg)) {
            // Create a template file so the user knows what to fill in
            Files.createDirectories(cfg.getParent());
            String template =
                    "# WOS Bot – Telegram Watcher configuration\n" +
                    "# Generated automatically. You can also edit this by hand.\n" +
                    "token=\n" +
                    "chatId=\n" +
                    "botJarPath=\n";
            Files.writeString(cfg, template);
            System.out.println("[INFO] Created config template at: " + cfg);
            System.out.println("[INFO] Fill it in and restart, or save via the bot's Telegram config panel.");
        }
        Properties props = new Properties();
        try (FileInputStream fis = new FileInputStream(cfg.toFile())) {
            props.load(fis);
        }
        return props;
    }
}

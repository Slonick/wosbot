package cl.camodev.wosbot.serv.impl;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import javax.imageio.ImageIO;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import cl.camodev.wosbot.emulator.EmulatorManager;
import cl.camodev.wosbot.ot.DTOBotState;
import cl.camodev.wosbot.ot.DTOProfiles;
import cl.camodev.wosbot.ot.DTORawImage;
import cl.camodev.wosbot.serv.IBotStateListener;

/**
 * Telegram Bot integration service.
 *
 * Polls the Telegram Bot API using long-polling and reacts to commands from an
 * authorised chat ID. Runs entirely on a background daemon virtual thread —
 * no inbound port / firewall change required.
 *
 * Supported commands (case-insensitive):
 *   /start or /startbot  → launch the bot automation
 *   /stop  or /stopbot   → stop  the bot automation
 *   /status              → reply with current running state
 */
public class TelegramBotService implements IBotStateListener {

    private static final Logger logger = LoggerFactory.getLogger(TelegramBotService.class);
    private static final String API_BASE = "https://api.telegram.org/bot";
    /** Long-poll timeout in seconds (Telegram max is 50). */
    private static final int LONG_POLL_TIMEOUT = 30;

    // ── singleton ────────────────────────────────────────────────────────────
    private static TelegramBotService instance;

    public static synchronized TelegramBotService getInstance() {
        if (instance == null) {
            instance = new TelegramBotService();
        }
        return instance;
    }

    // ── state ─────────────────────────────────────────────────────────────────
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicLong    lastUpdateId = new AtomicLong(-1);
    private volatile boolean    botCurrentlyRunning = false;

    private String  token;
    private long    allowedChatId;
    private Thread  pollingThread;

    private final HttpClient   httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private TelegramBotService() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        ServScheduler.getServices().registryBotStateListener(this);
    }

    // ── lifecycle ─────────────────────────────────────────────────────────────

    /**
     * Start the Telegram polling service.
     *
     * @param token         Telegram Bot API token
     * @param allowedChatId The only Telegram chat-ID that is allowed to issue commands
     */
    public synchronized void start(String token, long allowedChatId) {
        if (running.get()) {
            logger.info("TelegramBotService already running – restarting with new credentials");
            stop();
        }
        if (token == null || token.isBlank()) {
            logger.warn("TelegramBotService: token is blank, not starting");
            return;
        }
        this.token         = token.trim();
        this.allowedChatId = allowedChatId;
        running.set(true);
        lastUpdateId.set(-1);

        pollingThread = Thread.ofVirtual().unstarted(this::pollLoop);
        pollingThread.setName("telegram-poll");
        pollingThread.setDaemon(true);
        pollingThread.start();
        logger.info("TelegramBotService started (allowed chat-ID: {})", allowedChatId);
    }

    /** Stop the service gracefully. */
    public synchronized void stop() {
        running.set(false);
        if (pollingThread != null) {
            pollingThread.interrupt();
            pollingThread = null;
        }
        logger.info("TelegramBotService stopped");
    }

    public boolean isRunning() {
        return running.get();
    }

    // ── polling loop ──────────────────────────────────────────────────────────

    private void pollLoop() {
        while (running.get() && !Thread.currentThread().isInterrupted()) {
            try {
                String url = buildGetUpdatesUrl();
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(Duration.ofSeconds(LONG_POLL_TIMEOUT + 10))
                        .GET()
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    JsonNode root = objectMapper.readTree(response.body());
                    if (root.path("ok").asBoolean()) {
                        JsonNode results = root.path("result");
                        for (JsonNode update : results) {
                            processUpdate(update);
                            long uid = update.path("update_id").asLong(-1);
                            if (uid >= 0) {
                                lastUpdateId.set(uid);
                            }
                        }
                    }
                } else {
                    logger.warn("TelegramBotService: unexpected HTTP status {}", response.statusCode());
                    Thread.sleep(5_000);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                if (running.get()) {
                    logger.error("TelegramBotService poll error: {}", e.getMessage());
                    try { Thread.sleep(5_000); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                }
            }
        }
    }

    private String buildGetUpdatesUrl() {
        long offset = lastUpdateId.get() >= 0 ? lastUpdateId.get() + 1 : 0;
        return API_BASE + token
                + "/getUpdates?timeout=" + LONG_POLL_TIMEOUT
                + (offset > 0 ? "&offset=" + offset : "");
    }

    // ── command handling ──────────────────────────────────────────────────────

    private void processUpdate(JsonNode update) {
        JsonNode message = update.path("message");
        if (message.isMissingNode()) {
            return; // ignore non-message updates (edited messages, etc.)
        }

        long chatId = message.path("chat").path("id").asLong(-1);
        if (chatId < 0) return;

        // Security: ignore any chat that is not the pre-configured one
        if (allowedChatId != 0 && chatId != allowedChatId) {
            logger.debug("TelegramBotService: ignored message from unauthorized chat-ID {}", chatId);
            sendMessage(chatId, "⛔ Unauthorized. Your chat ID: " + chatId);
            return;
        }

        String text = message.path("text").asText("").trim().toLowerCase();

        if (text.startsWith("/start") || text.startsWith("/startbot")) {
            if (botCurrentlyRunning) {
                sendMessage(chatId, "⚙️ Bot is already running.");
            } else {
                sendMessage(chatId, "▶️ Starting bot...");
                Thread.ofVirtual().start(() -> ServScheduler.getServices().startBot());
            }
        } else if (text.startsWith("/stop") || text.startsWith("/stopbot")) {
            if (!botCurrentlyRunning) {
                sendMessage(chatId, "⏹️ Bot is not currently running.");
            } else {
                sendMessage(chatId, "⏹️ Stopping bot...");
                Thread.ofVirtual().start(() -> ServScheduler.getServices().stopBot());
            }
        } else if (text.startsWith("/status")) {
            String status = botCurrentlyRunning ? "✅ Bot is *running*." : "🔴 Bot is *stopped*.";
            sendMessage(chatId, status);
        } else if (text.startsWith("/screenshot")) {
            sendMessage(chatId, "📸 Capturing screenshot...");
            Thread.ofVirtual().start(() -> sendScreenshot(chatId));
        } else if (text.startsWith("/help") || text.equals("/")) {
            sendMessage(chatId, buildHelpMessage());
        } else {
            sendMessage(chatId,
                    "❓ Unknown command. Send /help for the list of commands.");
        }
    }

    // ── Help message ──────────────────────────────────────────────────────────

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
          + "_Tip: send /whelp from the watcher if the app is closed._";
    }

    // ── Screenshot ────────────────────────────────────────────────────────────

    private void sendScreenshot(long chatId) {
        try {
            List<DTOProfiles> profiles = ServProfiles.getServices().getProfiles();
            if (profiles == null || profiles.isEmpty()) {
                sendMessage(chatId, "❌ No profiles configured.");
                return;
            }
            Optional<DTOProfiles> enabledProfile = profiles.stream()
                    .filter(DTOProfiles::getEnabled)
                    .findFirst();
            if (enabledProfile.isEmpty()) {
                sendMessage(chatId, "❌ No enabled profiles found.");
                return;
            }
            String emulatorNumber = enabledProfile.get().getEmulatorNumber();
            EmulatorManager emuManager = EmulatorManager.getInstance();
            DTORawImage raw = emuManager.captureScreenshotViaADB(emulatorNumber);
            if (raw == null || raw.getData() == null || raw.getData().length == 0) {
                sendMessage(chatId, "❌ Failed to capture screenshot. Is the emulator running?");
                return;
            }
            byte[] pngBytes = rawImageToPng(raw);
            if (pngBytes == null) {
                sendMessage(chatId, "❌ Failed to encode screenshot.");
                return;
            }
            sendPhoto(chatId, pngBytes);
        } catch (Exception e) {
            logger.error("TelegramBotService: screenshot failed: {}", e.getMessage());
            sendMessage(chatId, "❌ Screenshot error: " + e.getMessage());
        }
    }

    private byte[] rawImageToPng(DTORawImage raw) {
        try {
            int width  = raw.getWidth();
            int height = raw.getHeight();
            int bpp    = raw.getBpp();
            byte[] data = raw.getData();
            BufferedImage img;
            if (bpp == 4) {
                img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
                int[] pixels = new int[width * height];
                for (int i = 0; i < pixels.length; i++) {
                    int idx = i * 4;
                    int r = data[idx]     & 0xFF;
                    int g = data[idx + 1] & 0xFF;
                    int b = data[idx + 2] & 0xFF;
                    int a = data[idx + 3] & 0xFF;
                    pixels[i] = (a << 24) | (r << 16) | (g << 8) | b;
                }
                img.setRGB(0, 0, width, height, pixels, 0, width);
            } else {
                // bpp == 3 (RGB)
                img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
                int[] pixels = new int[width * height];
                for (int i = 0; i < pixels.length; i++) {
                    int idx = i * 3;
                    int r = data[idx]     & 0xFF;
                    int g = data[idx + 1] & 0xFF;
                    int b = data[idx + 2] & 0xFF;
                    pixels[i] = (r << 16) | (g << 8) | b;
                }
                img.setRGB(0, 0, width, height, pixels, 0, width);
            }
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(img, "png", baos);
            return baos.toByteArray();
        } catch (Exception e) {
            logger.error("TelegramBotService: PNG encoding failed: {}", e.getMessage());
            return null;
        }
    }

    private void sendPhoto(long chatId, byte[] pngBytes) {
        try {
            String boundary = "TelegramBotBoundary" + System.currentTimeMillis();
            byte[] header = ("--" + boundary + "\r\n"
                    + "Content-Disposition: form-data; name=\"chat_id\"\r\n\r\n"
                    + chatId + "\r\n"
                    + "--" + boundary + "\r\n"
                    + "Content-Disposition: form-data; name=\"photo\"; filename=\"screenshot.png\"\r\n"
                    + "Content-Type: image/png\r\n\r\n").getBytes(StandardCharsets.UTF_8);
            byte[] footer = ("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8);
            byte[] body = new byte[header.length + pngBytes.length + footer.length];
            System.arraycopy(header, 0, body, 0, header.length);
            System.arraycopy(pngBytes, 0, body, header.length, pngBytes.length);
            System.arraycopy(footer, 0, body, header.length + pngBytes.length, footer.length);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(API_BASE + token + "/sendPhoto"))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                logger.warn("TelegramBotService: sendPhoto HTTP {}: {}", response.statusCode(), response.body());
                sendMessage(chatId, "❌ Telegram rejected the photo (HTTP " + response.statusCode() + ")");
            }
        } catch (Exception e) {
            logger.error("TelegramBotService: failed to send photo: {}", e.getMessage());
        }
    }

    // ── Telegram send helper ──────────────────────────────────────────────────

    private void sendMessage(long chatId, String text) {
        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("chat_id", chatId);
            body.put("text", text);
            body.put("parse_mode", "Markdown");

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(API_BASE + token + "/sendMessage"))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                    .build();

            httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            logger.error("TelegramBotService: failed to send message: {}", e.getMessage());
        }
    }

    /**
     * Validate that a token is well-formed by calling getMe.
     *
     * @param token the token to test
     * @return the bot username if the token is valid, or an error string starting with "ERROR:"
     */
    public String testToken(String token) {
        if (token == null || token.isBlank()) {
            return "ERROR: token is empty";
        }
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(API_BASE + token.trim() + "/getMe"))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode root = objectMapper.readTree(response.body());
            if (root.path("ok").asBoolean()) {
                return "@" + root.path("result").path("username").asText("?");
            } else {
                return "ERROR: " + root.path("description").asText("invalid token");
            }
        } catch (Exception e) {
            return "ERROR: " + e.getMessage();
        }
    }

    // ── IBotStateListener ─────────────────────────────────────────────────────

    @Override
    public void onBotStateChange(DTOBotState botState) {
        if (botState != null) {
            botCurrentlyRunning = Boolean.TRUE.equals(botState.getRunning());
        }
    }
}

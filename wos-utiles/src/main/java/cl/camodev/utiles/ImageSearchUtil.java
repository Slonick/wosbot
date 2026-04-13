package cl.camodev.utiles;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import cl.camodev.wosbot.ot.DTOImageSearchResult;
import cl.camodev.wosbot.ot.DTOPoint;
import cl.camodev.wosbot.ot.DTORawImage;
import cl.camodev.wosbot.console.enumerable.EnumTemplates;
import org.slf4j.*;

public class ImageSearchUtil {
	private static final Logger logger = LoggerFactory.getLogger(ImageSearchUtil.class);

	// Cache thread-safe para templates precargados
	private static final ConcurrentHashMap<String, Mat> templateCache = new ConcurrentHashMap<>();

	// Cache for grayscale templates
	private static final ConcurrentHashMap<String, Mat> grayscaleTemplateCache = new ConcurrentHashMap<>();

	// Custom thread pool for OpenCV operations
	private static final ForkJoinPool openCVThreadPool = new ForkJoinPool(
			Math.min(Runtime.getRuntime().availableProcessors(), 4));

	// Cache for template byte arrays
	private static final ConcurrentHashMap<String, byte[]> templateBytesCache = new ConcurrentHashMap<>();

	// Cache initialization status
	private static volatile boolean cacheInitialized = false;

	// Thread-local storage for profile name context
	private static final ThreadLocal<String> currentProfileName = new ThreadLocal<>();

	/**
	 * Set the current profile name for logging context.
	 * This is used to prefix log messages with the profile name.
	 *
	 * @param profileName The profile name to use
	 */
	public static void setProfileName(String profileName) {
		currentProfileName.set(profileName);
	}

	/**
	 * Clear the current profile name
	 */
	public static void clearProfileName() {
		currentProfileName.remove();
	}

	/**
	 * Get formatted log message with profile name prefix if available
	 */
	private static String formatLogMessage(String message) {
		String profileName = currentProfileName.get();
		if (profileName != null && !profileName.isEmpty()) {
			return profileName + " - " + message;
		}
		return message;
	}

	static {
		// Automatic cache initialization in the background
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			openCVThreadPool.shutdown();
			// Clean cache and release OpenCV memory
			templateCache.values().forEach(Mat::release);
			templateCache.clear();
			grayscaleTemplateCache.values().forEach(Mat::release);
			grayscaleTemplateCache.clear();
			templateBytesCache.clear();
		}));

		// Preload all templates from the enum in the background
		initializeTemplateCache();
	}

	/**
	 * Initializes the template cache by loading all templates from the
	 * EnumTemplates enum.
	 */
	private static void initializeTemplateCache() {
		if (cacheInitialized)
			return;

		openCVThreadPool.submit(() -> {
			try {
				logger.info("Caching templates...");

				// Preload all templates from the enum (both color and grayscale)
				for (EnumTemplates enumTemplate : EnumTemplates.values()) {
					String templatePath = enumTemplate.getTemplate();
					try {
						// Load color template
						loadTemplateOptimized(templatePath);
						logger.debug(formatLogMessage("Template " + templatePath + " cached successfully"));

						// Also load grayscale version
						loadTemplateGrayscale(templatePath);
						logger.debug(formatLogMessage("Grayscale template " + templatePath + " cached successfully"));
					} catch (Exception e) {
						logger.warn(
								formatLogMessage("Error preloading template " + templatePath + ": " + e.getMessage()));
					}
				}

				cacheInitialized = true;
				logger.info(formatLogMessage(
						"Template cache initialized with " + templateCache.size() + " color templates and " +
								grayscaleTemplateCache.size() + " grayscale templates"));

			} catch (Exception e) {
				logger.error(formatLogMessage("Error initializing template cache: " + e.getMessage()));
			}
		});
	}

	/**
	 * Performs the search for a template within a raw image.
	 * Always receives raw image data and converts directly to OpenCV Mat.
	 */
	public static DTOImageSearchResult searchTemplate(DTORawImage rawImage, String templateResourcePath,
			DTOPoint topLeftCorner, DTOPoint bottomRightCorner, double thresholdPercentage) {
		DTOImageSearchResult result = searchTemplateOptimized(rawImage.getData(), rawImage.getWidth(),
				rawImage.getHeight(),
				rawImage.getBpp(), templateResourcePath, topLeftCorner, bottomRightCorner, thresholdPercentage);
		return result;
	}

	/**
	 * Performs the search for multiple matches of a template within a raw image.
	 */
	public static List<DTOImageSearchResult> searchTemplateMultiple(DTORawImage rawImage, String templateResourcePath,
			DTOPoint topLeftCorner, DTOPoint bottomRightCorner, double thresholdPercentage, int maxResults) {
		List<DTOImageSearchResult> results = searchTemplateMultipleOptimizedRaw(rawImage.getData(), rawImage.getWidth(),
				rawImage.getHeight(),
				rawImage.getBpp(), templateResourcePath, topLeftCorner, bottomRightCorner, thresholdPercentage,
				maxResults);
		return results;
	}

	/**
	 * Performs a grayscale search for a template within a raw image.
	 * Both the template and the image are converted to grayscale before matching.
	 */
	public static DTOImageSearchResult searchTemplateGrayscale(DTORawImage rawImage, String templateResourcePath,
			DTOPoint topLeftCorner, DTOPoint bottomRightCorner, double thresholdPercentage) {
		DTOImageSearchResult result = searchTemplateGrayscaleOptimizedRaw(rawImage.getData(), rawImage.getWidth(),
				rawImage.getHeight(),
				rawImage.getBpp(), templateResourcePath, topLeftCorner, bottomRightCorner, thresholdPercentage);
		return result;
	}

	/**
	 * Performs a grayscale search for multiple matches of a template within a raw
	 * image.
	 * Both the template and the image are converted to grayscale before matching.
	 */
	public static List<DTOImageSearchResult> searchTemplateGrayscaleMultiple(DTORawImage rawImage,
			String templateResourcePath, DTOPoint topLeftCorner, DTOPoint bottomRightCorner, double thresholdPercentage,
			int maxResults) {
		List<DTOImageSearchResult> results = searchTemplateGrayscaleMultipleOptimizedRaw(rawImage.getData(),
				rawImage.getWidth(), rawImage.getHeight(),
				rawImage.getBpp(), templateResourcePath, topLeftCorner, bottomRightCorner, thresholdPercentage,
				maxResults);
		return results;
	}

	/**
	 * Search for a template using byte[] (for backward compatibility).
	 */
	public static DTOImageSearchResult searchTemplate(byte[] image, String templateResourcePath, DTOPoint topLeftCorner,
			DTOPoint bottomRightCorner, double thresholdPercentage) {
		return searchTemplateOptimizedEncoded(image, templateResourcePath, topLeftCorner, bottomRightCorner,
				thresholdPercentage);
	}

	/**
	 * Search for multiple templates using byte[] (for backward compatibility).
	 */
	public static List<DTOImageSearchResult> searchTemplateMultiple(byte[] image, String templateResourcePath,
			DTOPoint topLeftCorner, DTOPoint bottomRightCorner, double thresholdPercentage, int maxResults) {
		return searchTemplateMultipleOptimizedEncoded(image, templateResourcePath, topLeftCorner, bottomRightCorner,
				thresholdPercentage, maxResults);
	}

	/**
	 * Search for a template using grayscale and byte[] (for backward
	 * compatibility).
	 */
	public static DTOImageSearchResult searchTemplateGrayscale(byte[] image, String templateResourcePath,
			DTOPoint topLeftCorner, DTOPoint bottomRightCorner, double thresholdPercentage) {
		return searchTemplateGrayscaleOptimizedEncoded(image, templateResourcePath, topLeftCorner, bottomRightCorner,
				thresholdPercentage);
	}

	/**
	 * Search for multiple templates using grayscale and byte[] (for backward
	 * compatibility).
	 */
	public static List<DTOImageSearchResult> searchTemplateGrayscaleMultiple(byte[] image, String templateResourcePath,
			DTOPoint topLeftCorner, DTOPoint bottomRightCorner, double thresholdPercentage, int maxResults) {
		return searchTemplateGrayscaleMultipleOptimizedEncoded(image, templateResourcePath, topLeftCorner,
				bottomRightCorner, thresholdPercentage, maxResults);
	}

	/**
	 * Performs the search for multiple matches using raw image data.
	 */
	public static List<DTOImageSearchResult> searchTemplateMultiple(byte[] rawImageData, int width, int height, int bpp,
			String templateResourcePath, DTOPoint topLeftCorner, DTOPoint bottomRightCorner, double thresholdPercentage,
			int maxResults) {
		return searchTemplateMultipleOptimizedRaw(rawImageData, width, height, bpp, templateResourcePath, topLeftCorner,
				bottomRightCorner, thresholdPercentage, maxResults);
	}

	// ========================================================================
	// CUSTOM FILE-BASED TEMPLATE SEARCH (bypasses classpath resource cache)
	// ========================================================================

	/**
	 * Searches for a template provided as raw bytes (e.g. loaded from an
	 * absolute filesystem path) against a captured emulator screen image.
	 *
	 * <p>This bypasses the classpath resource cache entirely; the template Mat is
	 * decoded on-the-fly from the supplied bytes. The result is NOT cached.</p>
	 *
	 * @param rawImage      Raw emulator screenshot
	 * @param templateBytes PNG/JPG bytes of the custom template image
	 * @param topLeft       Top-left corner of the search region
	 * @param bottomRight   Bottom-right corner of the search region
	 * @param threshold     Match threshold (0-100)
	 * @return Search result with found flag, coordinates and match percentage
	 */
	public static DTOImageSearchResult searchTemplateFromBytes(DTORawImage rawImage, byte[] templateBytes,
			DTOPoint topLeft, DTOPoint bottomRight, double threshold) {

		Mat imagenPrincipal = null;
		Mat template = null;
		Mat imagenROI = null;
		Mat resultado = null;

		try {
			// Decode template from supplied bytes
			MatOfByte mob = new MatOfByte(templateBytes);
			template = Imgcodecs.imdecode(mob, Imgcodecs.IMREAD_COLOR);
			if (template == null || template.empty()) {
				logger.error(formatLogMessage("searchTemplateFromBytes: could not decode template bytes"));
				return new DTOImageSearchResult(false, null, 0.0);
			}

			// Convert screen raw data to Mat
			imagenPrincipal = convertRawDataToMat(rawImage.getData(), rawImage.getWidth(), rawImage.getHeight(),
					rawImage.getBpp());
			if (imagenPrincipal.empty()) {
				return new DTOImageSearchResult(false, null, 0.0);
			}

			// Clamp ROI to image bounds
			int roiX = Math.max(0, topLeft.getX());
			int roiY = Math.max(0, topLeft.getY());
			int roiW = Math.min(bottomRight.getX(), imagenPrincipal.cols()) - roiX;
			int roiH = Math.min(bottomRight.getY(), imagenPrincipal.rows()) - roiY;

			if (roiW <= 0 || roiH <= 0) {
				return new DTOImageSearchResult(false, null, 0.0);
			}

			int resultCols = roiW - template.cols() + 1;
			int resultRows = roiH - template.rows() + 1;
			if (resultCols <= 0 || resultRows <= 0) {
				return new DTOImageSearchResult(false, null, 0.0);
			}

			Rect roi = new Rect(roiX, roiY, roiW, roiH);
			imagenROI = new Mat(imagenPrincipal, roi);
			resultado = new Mat(resultRows, resultCols, CvType.CV_32FC1);
			Imgproc.matchTemplate(imagenROI, template, resultado, Imgproc.TM_CCOEFF_NORMED);

			Core.MinMaxLocResult mmr = Core.minMaxLoc(resultado);
			double normalizedVal = Math.max(-1.0, Math.min(1.0, mmr.maxVal));
			if (Double.isNaN(normalizedVal) || Double.isInfinite(normalizedVal)) {
				return new DTOImageSearchResult(false, null, 0.0);
			}
			double matchPct = normalizedVal * 100.0;

			if (matchPct < threshold) {
				logger.info(formatLogMessage(
						"Custom template (bytes): NOT FOUND (match: " + String.format("%.2f", matchPct) + "%)"));
				return new DTOImageSearchResult(false, null, matchPct);
			}

			int centerX = (int) (mmr.maxLoc.x + roi.x + template.cols() / 2.0);
			int centerY = (int) (mmr.maxLoc.y + roi.y + template.rows() / 2.0);
			logger.info(formatLogMessage("Custom template (bytes): FOUND at (" + centerX + "," + centerY
					+ ") match: " + String.format("%.2f", matchPct) + "%"));
			return new DTOImageSearchResult(true, new DTOPoint(centerX, centerY), matchPct);

		} catch (Exception e) {
			logger.error(formatLogMessage("searchTemplateFromBytes: exception during search"), e);
			return new DTOImageSearchResult(false, null, 0.0);
		} finally {
			if (imagenPrincipal != null) imagenPrincipal.release();
			if (template != null) template.release();
			if (imagenROI != null) imagenROI.release();
			if (resultado != null) resultado.release();
		}
	}

	/**
	 * Grayscale variant of {@link #searchTemplateFromBytes}. Both the template
	 * and the screen ROI are converted to grayscale before matching.
	 *
	 * @param rawImage      Raw emulator screenshot
	 * @param templateBytes PNG/JPG bytes of the custom template image
	 * @param topLeft       Top-left corner of the search region
	 * @param bottomRight   Bottom-right corner of the search region
	 * @param threshold     Match threshold (0-100)
	 * @return Search result with found flag, coordinates and match percentage
	 */
	public static DTOImageSearchResult searchTemplateGrayscaleFromBytes(DTORawImage rawImage, byte[] templateBytes,
			DTOPoint topLeft, DTOPoint bottomRight, double threshold) {

		Mat imagenPrincipal = null;
		Mat template = null;
		Mat grayTemplate = null;
		Mat grayROI = null;
		Mat imagenROI = null;
		Mat resultado = null;

		try {
			MatOfByte mob = new MatOfByte(templateBytes);
			template = Imgcodecs.imdecode(mob, Imgcodecs.IMREAD_COLOR);
			if (template == null || template.empty()) {
				logger.error(formatLogMessage("searchTemplateGrayscaleFromBytes: could not decode template bytes"));
				return new DTOImageSearchResult(false, null, 0.0);
			}

			grayTemplate = new Mat();
			Imgproc.cvtColor(template, grayTemplate, Imgproc.COLOR_BGR2GRAY);

			imagenPrincipal = convertRawDataToMat(rawImage.getData(), rawImage.getWidth(), rawImage.getHeight(),
					rawImage.getBpp());
			if (imagenPrincipal.empty()) {
				return new DTOImageSearchResult(false, null, 0.0);
			}

			int roiX = Math.max(0, topLeft.getX());
			int roiY = Math.max(0, topLeft.getY());
			int roiW = Math.min(bottomRight.getX(), imagenPrincipal.cols()) - roiX;
			int roiH = Math.min(bottomRight.getY(), imagenPrincipal.rows()) - roiY;

			if (roiW <= 0 || roiH <= 0) {
				return new DTOImageSearchResult(false, null, 0.0);
			}

			int resultCols = roiW - grayTemplate.cols() + 1;
			int resultRows = roiH - grayTemplate.rows() + 1;
			if (resultCols <= 0 || resultRows <= 0) {
				return new DTOImageSearchResult(false, null, 0.0);
			}

			Rect roi = new Rect(roiX, roiY, roiW, roiH);
			imagenROI = new Mat(imagenPrincipal, roi);
			grayROI = new Mat();
			Imgproc.cvtColor(imagenROI, grayROI, Imgproc.COLOR_BGR2GRAY);

			resultado = new Mat(resultRows, resultCols, CvType.CV_32FC1);
			Imgproc.matchTemplate(grayROI, grayTemplate, resultado, Imgproc.TM_CCOEFF_NORMED);

			Core.MinMaxLocResult mmr = Core.minMaxLoc(resultado);
			double normalizedVal = Math.max(-1.0, Math.min(1.0, mmr.maxVal));
			if (Double.isNaN(normalizedVal) || Double.isInfinite(normalizedVal)) {
				return new DTOImageSearchResult(false, null, 0.0);
			}
			double matchPct = normalizedVal * 100.0;

			if (matchPct < threshold) {
				return new DTOImageSearchResult(false, null, matchPct);
			}

			int centerX = (int) (mmr.maxLoc.x + roi.x + grayTemplate.cols() / 2.0);
			int centerY = (int) (mmr.maxLoc.y + roi.y + grayTemplate.rows() / 2.0);
			return new DTOImageSearchResult(true, new DTOPoint(centerX, centerY), matchPct);

		} catch (Exception e) {
			logger.error(formatLogMessage("searchTemplateGrayscaleFromBytes: exception during search"), e);
			return new DTOImageSearchResult(false, null, 0.0);
		} finally {
			if (imagenPrincipal != null) imagenPrincipal.release();
			if (template != null) template.release();
			if (grayTemplate != null) grayTemplate.release();
			if (imagenROI != null) imagenROI.release();
			if (grayROI != null) grayROI.release();
			if (resultado != null) resultado.release();
		}
	}

	/**
	 * Optimized method for loading and caching templates.
	 */
	private static Mat loadTemplateOptimized(String templateResourcePath) {
		// Try to get from cache first
		Mat cachedTemplate = templateCache.get(templateResourcePath);
		if (cachedTemplate != null && !cachedTemplate.empty()) {
			return cachedTemplate.clone(); // Return a copy for thread safety
		}

		try {
			// Load bytes from cache or resource
			byte[] templateBytes = templateBytesCache.computeIfAbsent(templateResourcePath, path -> {
				try (InputStream is = ImageSearchUtil.class.getResourceAsStream(path)) {
					if (is == null) {
						logger.error(formatLogMessage("Template resource not found: " + path));
						return null;
					}
					return is.readAllBytes();
				} catch (IOException e) {
					logger.error(formatLogMessage("Error loading template bytes for: " + path), e);
					return null;
				}
			});

			if (templateBytes == null) {
				return new Mat(); // Empty Mat
			}

			// Decode template
			MatOfByte templateMatOfByte = new MatOfByte(templateBytes);
			Mat template = Imgcodecs.imdecode(templateMatOfByte, Imgcodecs.IMREAD_COLOR);

			if (!template.empty()) {
				// Save to cache (clone to avoid modifications)
				templateCache.put(templateResourcePath, template.clone());
			}

			return template;

		} catch (Exception e) {
			logger.error(formatLogMessage("Exception loading template: " + templateResourcePath), e);
			return new Mat();
		}
	}

	/**
	 * Optimized method for loading and caching grayscale templates.
	 */
	private static Mat loadTemplateGrayscale(String templateResourcePath) {
		// Try to get from grayscale cache first
		Mat cachedTemplate = grayscaleTemplateCache.get(templateResourcePath);
		if (cachedTemplate != null && !cachedTemplate.empty()) {
			return cachedTemplate.clone(); // Return a copy for thread safety
		}

		try {
			// Load the color template first
			Mat colorTemplate = loadTemplateOptimized(templateResourcePath);
			if (colorTemplate.empty()) {
				return new Mat();
			}

			// Convert to grayscale
			Mat grayTemplate = new Mat();
			Imgproc.cvtColor(colorTemplate, grayTemplate, Imgproc.COLOR_BGR2GRAY);

			// Save to grayscale cache
			if (!grayTemplate.empty()) {
				grayscaleTemplateCache.put(templateResourcePath, grayTemplate.clone());
			}

			// Release color template as we don't need it anymore
			colorTemplate.release();

			return grayTemplate;
		} catch (Exception e) {
			logger.error(formatLogMessage("Exception loading grayscale template: " + templateResourcePath), e);
			return new Mat();
		}
	}

	/**
	 * Loads a mask for the given template if it exists.
	 * Masks follow the naming convention: path/template_mask.png
	 * 
	 * @param templateResourcePath The template resource path
	 * @return Mat containing the mask, or null if no mask exists
	 */
	private static Mat loadTemplateMask(String templateResourcePath) {
		// Derive mask path from template path
		// Examples:
		// - /path/template.png -> /path/template_mask.png
		// - /path/template_CH.png -> /path/template_mask.png (same mask for both)

		String maskPath;
		if (templateResourcePath.contains("_CH.png")) {
			// For region-specific templates, use base mask
			maskPath = templateResourcePath.replace("_CH.png", "_mask.png");
		} else if (templateResourcePath.endsWith(".png")) {
			// For global templates
			maskPath = templateResourcePath.replace(".png", "_mask.png");
		} else {
			return null; // Unsupported format
		}

		// Try to get from cache first
		Mat cachedMask = templateCache.get(maskPath);
		if (cachedMask != null && !cachedMask.empty()) {
			return cachedMask.clone(); // Return a copy for thread safety
		}

		try {
			// Load bytes from cache or resource
			byte[] maskBytes = templateBytesCache.computeIfAbsent(maskPath, path -> {
				try (InputStream is = ImageSearchUtil.class.getResourceAsStream(path)) {
					if (is == null) {
						// No mask file found - this is normal, not all templates have masks
						logger.debug("No mask found for template: {}", templateResourcePath);
						return null;
					}
					logger.debug("Mask found and loaded: {}", maskPath);
					return is.readAllBytes();
				} catch (IOException e) {
					logger.debug("Error loading mask for: {}", path);
					return null;
				}
			});

			if (maskBytes == null) {
				return null; // No mask available
			}

			// Decode mask (load as grayscale)
			MatOfByte maskMatOfByte = new MatOfByte(maskBytes);
			Mat mask = Imgcodecs.imdecode(maskMatOfByte, Imgcodecs.IMREAD_GRAYSCALE);

			if (!mask.empty()) {
				// Save to cache (clone to avoid modifications)
				templateCache.put(maskPath, mask.clone());
				return mask;
			}

			return null;

		} catch (Exception e) {
			logger.debug("Exception loading mask for: {}", templateResourcePath);
			return null;
		}
	}

	/**
	 * Optimized version of the searchTemplate method with cache and better memory
	 * management.
	 */
	public static DTOImageSearchResult searchTemplateOptimized(byte[] rawImageData, int width, int height, int bpp,
			String templateResourcePath, DTOPoint topLeftCorner, DTOPoint bottomRightCorner,
			double thresholdPercentage) {

		long startTime = System.currentTimeMillis();
		logger.debug("=== Template Search Started ===");
		logger.debug("Template: {}, Threshold: {}%, ROI: ({},{}) to ({},{})",
				templateResourcePath, thresholdPercentage, topLeftCorner.getX(), topLeftCorner.getY(),
				bottomRightCorner.getX(), bottomRightCorner.getY());

		Mat imagenPrincipal = null;
		Mat template = null;
		Mat mask = null;
		Mat imagenROI = null;
		Mat resultado = null;

		String[] templatePaths = templateResourcePath.split("/");
		String templateName = templatePaths[templatePaths.length - 1];

		try {
			// Convert raw image data directly to OpenCV Mat
			long conversionStartTime = System.currentTimeMillis();
			imagenPrincipal = convertRawDataToMat(rawImageData, width, height, bpp);
			long conversionEndTime = System.currentTimeMillis();
			logger.debug("Raw data to Mat conversion: {} ms", (conversionEndTime - conversionStartTime));

			if (imagenPrincipal.empty()) {
				logger.error("Converted image is empty");
				return new DTOImageSearchResult(false, null, 0.0);
			}

			// Quick ROI validation
			int roiX = topLeftCorner.getX();
			int roiY = topLeftCorner.getY();
			int roiWidth = bottomRightCorner.getX() - topLeftCorner.getX();
			int roiHeight = bottomRightCorner.getY() - topLeftCorner.getY();

			if (roiWidth <= 0 || roiHeight <= 0) {
				logger.error(formatLogMessage("Invalid ROI dimensions"));
				return new DTOImageSearchResult(false, null, 0.0);
			}

			// Load optimized template with cache
			long templateLoadStartTime = System.currentTimeMillis();
			template = loadTemplateOptimized(templateResourcePath);
			long templateLoadEndTime = System.currentTimeMillis();
			logger.debug("Template loading: {} ms (from cache: {})",
					(templateLoadEndTime - templateLoadStartTime),
					templateCache.containsKey(templateResourcePath));

			if (template.empty()) {
				logger.error("Template is empty: {}", templateResourcePath);
				return new DTOImageSearchResult(false, null, 0.0);
			}

			// Load mask if available
			mask = loadTemplateMask(templateResourcePath);
			if (mask != null && !mask.empty()) {
				logger.debug("Using mask for template: {}", templateResourcePath);
			}

			logger.debug("Template size: {}x{}, Image size: {}x{}",
					template.cols(), template.rows(), imagenPrincipal.cols(), imagenPrincipal.rows());

			// ROI vs image validation
			if (roiX + roiWidth > imagenPrincipal.cols() || roiY + roiHeight > imagenPrincipal.rows()) {
				logger.error(formatLogMessage("ROI exceeds image dimensions"));
				return new DTOImageSearchResult(false, null, 0.0);
			}

			// Create ROI
			long roiStartTime = System.currentTimeMillis();
			Rect roi = new Rect(roiX, roiY, roiWidth, roiHeight);
			imagenROI = new Mat(imagenPrincipal, roi);
			long roiEndTime = System.currentTimeMillis();
			logger.debug("ROI creation: {} ms", (roiEndTime - roiStartTime));

			// Optimized size check
			int resultCols = imagenROI.cols() - template.cols() + 1;
			int resultRows = imagenROI.rows() - template.rows() + 1;
			if (resultCols <= 0 || resultRows <= 0) {
				logger.error("Template larger than ROI");
				return new DTOImageSearchResult(false, null, 0.0);
			}

			// Template matching
			long matchStartTime = System.currentTimeMillis();
			resultado = new Mat(resultRows, resultCols, CvType.CV_32FC1);

			int method = (mask != null && !mask.empty())
					? Imgproc.TM_CCORR_NORMED
					: Imgproc.TM_CCOEFF_NORMED;

			// Use mask if available, otherwise use standard matching
			if (mask != null && !mask.empty()) {
				Imgproc.matchTemplate(imagenROI, template, resultado, method, mask);
			} else {
				Imgproc.matchTemplate(imagenROI, template, resultado, method);
			}

			long matchEndTime = System.currentTimeMillis();
			logger.debug("Template matching execution: {} ms", (matchEndTime - matchStartTime));

			// Search for the best match
			Core.MinMaxLocResult mmr = Core.minMaxLoc(resultado);

			// Log the raw value for debugging
			if (mmr.maxVal > 1.0 || mmr.maxVal < -1.0 || Double.isNaN(mmr.maxVal) || Double.isInfinite(mmr.maxVal)) {
				logger.warn("Abnormal maxVal detected: {} for template: {}", mmr.maxVal, templateResourcePath);
			}

			// Clamp the value to valid range [-1.0, 1.0] before converting to percentage
			double normalizedVal = Math.max(-1.0, Math.min(1.0, mmr.maxVal));

			// Handle NaN or Infinite values
			if (Double.isNaN(normalizedVal) || Double.isInfinite(normalizedVal)) {
				logger.error("Invalid match value (NaN or Infinite) for template: {}", templateResourcePath);
				return new DTOImageSearchResult(false, null, 0.0);
			}

			double matchPercentage = normalizedVal * 100.0;

			long totalTime = System.currentTimeMillis() - startTime;

			if (matchPercentage < thresholdPercentage) {
				logger.info(
						"=== Template Search Completed === Template: {}, Total: {} ms, Match: {}% (BELOW threshold)",
						templateName, totalTime, String.format("%.2f", matchPercentage));
				return new DTOImageSearchResult(false, null, matchPercentage);
			}

			// Calculate center coordinates
			Point matchLoc = mmr.maxLoc;
			double centerX = matchLoc.x + roi.x + (template.cols() / 2.0);
			double centerY = matchLoc.y + roi.y + (template.rows() / 2.0);

			logger.info("=== Template Search Completed === Template: {}, Total: {} ms, Match: {}%, Position: ({},{})",
					templateName, totalTime, String.format("%.2f", matchPercentage), (int) centerX, (int) centerY);

			return new DTOImageSearchResult(true, new DTOPoint((int) centerX, (int) centerY), matchPercentage);

		} catch (Exception e) {
			logger.error(formatLogMessage("Exception during optimized template search"), e);
			return new DTOImageSearchResult(false, null, 0.0);
		} finally {
			// Explicit release of OpenCV memory
			if (imagenPrincipal != null)
				imagenPrincipal.release();
			if (template != null)
				template.release();
			if (mask != null)
				mask.release();
			if (imagenROI != null)
				imagenROI.release();
			if (resultado != null)
				resultado.release();
		}
	}

	private static Mat convertRawDataToMat(byte[] rawData, int width, int height, int bpp) {
		return convertRawToMatBulk(rawData, width, height, bpp);
	}

	// ========================================================================
	// FAST-PATH METHODS FOR TIGHT LOOPS
	// ========================================================================

	/**
	 * Fast bulk conversion of raw ADB screencap data to OpenCV BGR Mat.
	 * <p>
	 * Uses a single bulk {@code Mat.put()} call instead of per-pixel calls,
	 * reducing JNI round-trips from ~920,000 to 1-2 per frame.
	 * For 32 bpp (RGBA_8888): single put + native {@code cvtColor(RGBA→BGR)}.
	 * For 16 bpp (RGB565): tight-loop decode to BGR array + single put.
	 * <p>
	 * Typically <b>10-50× faster</b> than the original per-pixel version.
	 *
	 * @param rawData Raw pixel bytes (no header)
	 * @param width   Image width in pixels
	 * @param height  Image height in pixels
	 * @param bpp     Bits per pixel (16 or 32)
	 * @return BGR Mat (caller must release)
	 */
	public static Mat convertRawToMatBulk(byte[] rawData, int width, int height, int bpp) {
		if (rawData == null || rawData.length == 0 || width <= 0 || height <= 0) {
			logger.warn("convertRawToMatBulk: invalid input (data={}, {}x{}, {}bpp)",
					rawData == null ? "null" : rawData.length, width, height, bpp);
			return new Mat();
		}
		if (bpp == 32) {
			// RGBA_8888 → 4-channel bulk put, then native RGBA→BGR
			int expectedSize = width * height * 4;
			if (rawData.length < expectedSize / 2) {
				// Data is far too small — likely corrupted or wrong bpp
				logger.warn("convertRawToMatBulk: data too small for 32bpp (got {} bytes, expected {})",
						rawData.length, expectedSize);
				return new Mat();
			}
			Mat rgba = new Mat(height, width, CvType.CV_8UC4);
			byte[] data = (rawData.length == expectedSize)
					? rawData
					: java.util.Arrays.copyOf(rawData, expectedSize);
			rgba.put(0, 0, data);
			Mat bgr = new Mat();
			try {
				// Workaround for OpenCV 4.11.0 bug where dcn sometimes gets uninitialized
				// memory
				Imgproc.cvtColor(rgba, bgr, Imgproc.COLOR_RGBA2BGR, 3);
			} catch (Exception e) {
				logger.warn("convertRawToMatBulk: cvtColor failed ({}x{} {}bpp, data={}): {}",
						width, height, bpp, rawData.length, e.getMessage());
				rgba.release();
				bgr.release();
				return new Mat();
			}
			rgba.release();
			return bgr;
		} else {
			// RGB565 → decode to BGR byte array in tight loop, single put
			int pixels = width * height;
			byte[] bgrData = new byte[pixels * 3];
			for (int i = 0, s = 0, d = 0; i < pixels; i++, s += 2, d += 3) {
				int pixel = ((rawData[s + 1] & 0xFF) << 8) | (rawData[s] & 0xFF);
				bgrData[d] = (byte) ((pixel & 0x1F) << 3);
				bgrData[d + 1] = (byte) (((pixel >> 5) & 0x3F) << 2);
				bgrData[d + 2] = (byte) (((pixel >> 11) & 0x1F) << 3);
			}
			Mat mat = new Mat(height, width, CvType.CV_8UC3);
			mat.put(0, 0, bgrData);
			return mat;
		}
	}

	/**
	 * Returns a <b>direct reference</b> to the cached template Mat.
	 * <p>
	 * Unlike {@code loadTemplateOptimized()}, this does <b>not</b> clone the Mat.
	 * Ideal for tight loops that search the same template every frame.
	 * <p>
	 * <b>WARNING:</b> The returned Mat must NOT be modified or released by the
	 * caller. It is shared across all users of the cache.
	 *
	 * @param templateResourcePath Resource path (e.g. from
	 *                             {@code EnumTemplates.getTemplate()})
	 * @return Cached template Mat, or empty Mat if not found
	 */
	public static Mat getTemplateMat(String templateResourcePath) {
		Mat cached = templateCache.get(templateResourcePath);
		if (cached != null && !cached.empty()) {
			return cached;
		}
		// Trigger the normal load path (which clones into cache)
		Mat clone = loadTemplateOptimized(templateResourcePath);
		if (clone != null) {
			clone.release(); // discard the clone
		}
		// Return the original from cache
		cached = templateCache.get(templateResourcePath);
		return (cached != null) ? cached : new Mat();
	}

	/**
	 * Performs template matching directly on pre-converted OpenCV Mats.
	 * <p>
	 * Designed for tight loops where the caller converts the frame once and
	 * caches the template reference. No logging, no path resolution, no
	 * template loading/cloning per call — pure matching only.
	 *
	 * @param bgrImage            BGR Mat from {@link #convertRawToMatBulk}
	 * @param template            Template Mat from {@link #getTemplateMat}
	 *                            (read-only)
	 * @param topLeft             Top-left corner of search region
	 * @param bottomRight         Bottom-right corner of search region
	 * @param thresholdPercentage Match threshold (0-100)
	 * @return Search result with coordinates and confidence
	 */
	public static DTOImageSearchResult matchTemplateDirect(
			Mat bgrImage, Mat template,
			DTOPoint topLeft, DTOPoint bottomRight,
			double thresholdPercentage) {

		Mat imagenROI = null;
		Mat resultado = null;

		try {
			int roiX = topLeft.getX();
			int roiY = topLeft.getY();
			int roiW = Math.min(bottomRight.getX() - roiX, bgrImage.cols() - roiX);
			int roiH = Math.min(bottomRight.getY() - roiY, bgrImage.rows() - roiY);

			if (roiW <= template.cols() || roiH <= template.rows()) {
				return new DTOImageSearchResult(false, null, 0.0);
			}

			Rect roi = new Rect(roiX, roiY, roiW, roiH);
			imagenROI = new Mat(bgrImage, roi);

			int resultCols = roiW - template.cols() + 1;
			int resultRows = roiH - template.rows() + 1;
			resultado = new Mat(resultRows, resultCols, CvType.CV_32FC1);

			Imgproc.matchTemplate(imagenROI, template, resultado, Imgproc.TM_CCOEFF_NORMED);

			Core.MinMaxLocResult mmr = Core.minMaxLoc(resultado);
			double normalizedVal = Math.max(-1.0, Math.min(1.0, mmr.maxVal));
			if (Double.isNaN(normalizedVal) || Double.isInfinite(normalizedVal)) {
				return new DTOImageSearchResult(false, null, 0.0);
			}
			double matchPct = normalizedVal * 100.0;

			if (matchPct < thresholdPercentage) {
				return new DTOImageSearchResult(false, null, matchPct);
			}

			int centerX = (int) (mmr.maxLoc.x + roiX + template.cols() / 2.0);
			int centerY = (int) (mmr.maxLoc.y + roiY + template.rows() / 2.0);
			return new DTOImageSearchResult(true, new DTOPoint(centerX, centerY), matchPct);

		} finally {
			if (imagenROI != null)
				imagenROI.release();
			if (resultado != null)
				resultado.release();
		}
	}

	/**
	 * Optimized version for multiple search with parallelization.
	 */
	public static CompletableFuture<List<DTOImageSearchResult>> searchTemplateMultipleAsync(
			byte[] image, String templateResourcePath, DTOPoint topLeftCorner,
			DTOPoint bottomRightCorner, double thresholdPercentage, int maxResults) {

		return CompletableFuture.supplyAsync(() -> searchTemplateMultipleOptimized(image, templateResourcePath,
				topLeftCorner, bottomRightCorner, thresholdPercentage, maxResults), openCVThreadPool);
	}

	/**
	 * Optimized version of multiple search with better memory management.
	 */
	public static List<DTOImageSearchResult> searchTemplateMultipleOptimized(byte[] image,
			String templateResourcePath, DTOPoint topLeftCorner, DTOPoint bottomRightCorner,
			double thresholdPercentage, int maxResults) {

		List<DTOImageSearchResult> results = new ArrayList<>();
		Mat mainImage = null;
		Mat template = null;
		Mat imageROI = null;
		Mat matchResult = null;
		Mat resultCopy = null;

		try {
			// Quick ROI validation
			int roiX = topLeftCorner.getX();
			int roiY = topLeftCorner.getY();
			int roiWidth = bottomRightCorner.getX() - topLeftCorner.getX();
			int roiHeight = bottomRightCorner.getY() - topLeftCorner.getY();

			if (roiWidth <= 0 || roiHeight <= 0) {
				return results;
			}

			// Optimized decoding
			MatOfByte matOfByte = new MatOfByte(image);
			mainImage = Imgcodecs.imdecode(matOfByte, Imgcodecs.IMREAD_COLOR);

			if (mainImage.empty()) {
				return results;
			}

			// Load template with cache
			template = loadTemplateOptimized(templateResourcePath);
			if (template.empty()) {
				return results;
			}

			// Validations
			if (roiX + roiWidth > mainImage.cols() || roiY + roiHeight > mainImage.rows()) {
				return results;
			}

			// Create ROI
			Rect roi = new Rect(roiX, roiY, roiWidth, roiHeight);
			imageROI = new Mat(mainImage, roi);

			int resultCols = imageROI.cols() - template.cols() + 1;
			int resultRows = imageROI.rows() - template.rows() + 1;
			if (resultCols <= 0 || resultRows <= 0) {
				return results;
			}

			// Template matching
			matchResult = new Mat(resultRows, resultCols, CvType.CV_32FC1);
			Imgproc.matchTemplate(imageROI, template, matchResult, Imgproc.TM_CCOEFF_NORMED);

			// Optimized search for multiple matches
			double thresholdDecimal = thresholdPercentage / 100.0;
			resultCopy = matchResult.clone();
			int templateWidth = template.cols();
			int templateHeight = template.rows();

			// Pre-calculate for optimization
			int halfTemplateWidth = templateWidth / 2;
			int halfTemplateHeight = templateHeight / 2;

			while (results.size() < maxResults || maxResults <= 0) {
				Core.MinMaxLocResult mmr = Core.minMaxLoc(resultCopy);
				double matchValue = mmr.maxVal;

				if (matchValue < thresholdDecimal) {
					break;
				}

				Point matchLoc = mmr.maxLoc;
				double centerX = matchLoc.x + roi.x + halfTemplateWidth;
				double centerY = matchLoc.y + roi.y + halfTemplateHeight;

				results.add(new DTOImageSearchResult(true,
						new DTOPoint((int) centerX, (int) centerY), matchValue * 100.0));

				// Optimized suppression
				int suppressX = Math.max(0, (int) matchLoc.x - halfTemplateWidth);
				int suppressY = Math.max(0, (int) matchLoc.y - halfTemplateHeight);
				int suppressWidth = Math.min(templateWidth, resultCopy.cols() - suppressX);
				int suppressHeight = Math.min(templateHeight, resultCopy.rows() - suppressY);

				if (suppressWidth > 0 && suppressHeight > 0) {
					Rect suppressRect = new Rect(suppressX, suppressY, suppressWidth, suppressHeight);
					Mat suppressArea = new Mat(resultCopy, suppressRect);
					suppressArea.setTo(new org.opencv.core.Scalar(0));
					suppressArea.release();
				}
			}

		} catch (Exception e) {
			logger.error(formatLogMessage("Exception during optimized multiple template search"), e);
		} finally {
			// Explicit memory release
			if (mainImage != null)
				mainImage.release();
			if (template != null)
				template.release();
			if (imageROI != null)
				imageROI.release();
			if (matchResult != null)
				matchResult.release();
			if (resultCopy != null)
				resultCopy.release();
		}

		return results;
	}

	/**
	 * Optimized version of the searchTemplate method for encoded images (PNG/JPEG).
	 */
	private static DTOImageSearchResult searchTemplateOptimizedEncoded(byte[] image, String templateResourcePath,
			DTOPoint topLeftCorner, DTOPoint bottomRightCorner, double thresholdPercentage) {

		Mat imagenPrincipal = null;
		Mat template = null;
		Mat imagenROI = null;
		Mat resultado = null;

		try {
			// Quick ROI validation
			int roiX = topLeftCorner.getX();
			int roiY = topLeftCorner.getY();
			int roiWidth = bottomRightCorner.getX() - topLeftCorner.getX();
			int roiHeight = bottomRightCorner.getY() - topLeftCorner.getY();

			if (roiWidth <= 0 || roiHeight <= 0) {
				logger.error(formatLogMessage("Invalid ROI dimensions"));
				return new DTOImageSearchResult(false, null, 0.0);
			}

			// Decode image
			MatOfByte matOfByte = new MatOfByte(image);
			imagenPrincipal = Imgcodecs.imdecode(matOfByte, Imgcodecs.IMREAD_COLOR);

			if (imagenPrincipal.empty()) {
				return new DTOImageSearchResult(false, null, 0.0);
			}

			// Load optimized template with cache
			template = loadTemplateOptimized(templateResourcePath);
			if (template.empty()) {
				return new DTOImageSearchResult(false, null, 0.0);
			}

			// ROI vs image validation
			if (roiX + roiWidth > imagenPrincipal.cols() || roiY + roiHeight > imagenPrincipal.rows()) {
				logger.error(formatLogMessage("ROI exceeds image dimensions"));
				return new DTOImageSearchResult(false, null, 0.0);
			}

			// Create ROI
			Rect roi = new Rect(roiX, roiY, roiWidth, roiHeight);
			imagenROI = new Mat(imagenPrincipal, roi);

			// Optimized size check
			int resultCols = imagenROI.cols() - template.cols() + 1;
			int resultRows = imagenROI.rows() - template.rows() + 1;
			if (resultCols <= 0 || resultRows <= 0) {
				return new DTOImageSearchResult(false, null, 0.0);
			}

			// Template matching
			resultado = new Mat(resultRows, resultCols, CvType.CV_32FC1);
			Imgproc.matchTemplate(imagenROI, template, resultado, Imgproc.TM_CCOEFF_NORMED);

			// Search for the best match
			Core.MinMaxLocResult mmr = Core.minMaxLoc(resultado);
			double matchPercentage = mmr.maxVal * 100.0;

			if (matchPercentage < thresholdPercentage) {
				logger.warn(formatLogMessage("Template " + templateResourcePath + " match percentage " + matchPercentage
						+ " below threshold " + thresholdPercentage));
				return new DTOImageSearchResult(false, null, matchPercentage);
			}

			logger.info(formatLogMessage(
					"Template " + templateResourcePath + " found with match percentage: " + matchPercentage));

			// Calculate center coordinates
			Point matchLoc = mmr.maxLoc;
			double centerX = matchLoc.x + roi.x + (template.cols() / 2.0);
			double centerY = matchLoc.y + roi.y + (template.rows() / 2.0);

			return new DTOImageSearchResult(true, new DTOPoint((int) centerX, (int) centerY), matchPercentage);

		} catch (Exception e) {
			logger.error(formatLogMessage("Exception during optimized template search"), e);
			return new DTOImageSearchResult(false, null, 0.0);
		} finally {
			// Explicit release of OpenCV memory
			if (imagenPrincipal != null)
				imagenPrincipal.release();
			if (template != null)
				template.release();
			if (imagenROI != null)
				imagenROI.release();
			if (resultado != null)
				resultado.release();
		}
	}

	/**
	 * Optimized version of multiple search for encoded images (PNG/JPEG).
	 */
	private static List<DTOImageSearchResult> searchTemplateMultipleOptimizedEncoded(byte[] image,
			String templateResourcePath, DTOPoint topLeftCorner, DTOPoint bottomRightCorner,
			double thresholdPercentage, int maxResults) {

		List<DTOImageSearchResult> results = new ArrayList<>();
		Mat mainImage = null;
		Mat template = null;
		Mat imageROI = null;
		Mat matchResult = null;
		Mat resultCopy = null;

		try {
			// Quick ROI validation
			int roiX = topLeftCorner.getX();
			int roiY = topLeftCorner.getY();
			int roiWidth = bottomRightCorner.getX() - topLeftCorner.getX();
			int roiHeight = bottomRightCorner.getY() - topLeftCorner.getY();

			if (roiWidth <= 0 || roiHeight <= 0) {
				return results;
			}

			// Optimized decoding
			MatOfByte matOfByte = new MatOfByte(image);
			mainImage = Imgcodecs.imdecode(matOfByte, Imgcodecs.IMREAD_COLOR);

			if (mainImage.empty()) {
				return results;
			}

			// Load template with cache
			template = loadTemplateOptimized(templateResourcePath);
			if (template.empty()) {
				return results;
			}

			// Validations
			if (roiX + roiWidth > mainImage.cols() || roiY + roiHeight > mainImage.rows()) {
				return results;
			}

			// Create ROI
			Rect roi = new Rect(roiX, roiY, roiWidth, roiHeight);
			imageROI = new Mat(mainImage, roi);

			int resultCols = imageROI.cols() - template.cols() + 1;
			int resultRows = imageROI.rows() - template.rows() + 1;
			if (resultCols <= 0 || resultRows <= 0) {
				return results;
			}

			// Template matching
			matchResult = new Mat(resultRows, resultCols, CvType.CV_32FC1);
			Imgproc.matchTemplate(imageROI, template, matchResult, Imgproc.TM_CCOEFF_NORMED);

			// Optimized search for multiple matches
			double thresholdDecimal = thresholdPercentage / 100.0;
			resultCopy = matchResult.clone();
			int templateWidth = template.cols();
			int templateHeight = template.rows();

			// Pre-calculate for optimization
			int halfTemplateWidth = templateWidth / 2;
			int halfTemplateHeight = templateHeight / 2;

			while (results.size() < maxResults || maxResults <= 0) {
				Core.MinMaxLocResult mmr = Core.minMaxLoc(resultCopy);
				double matchValue = mmr.maxVal;

				if (matchValue < thresholdDecimal) {
					break;
				}

				Point matchLoc = mmr.maxLoc;
				double centerX = matchLoc.x + roi.x + halfTemplateWidth;
				double centerY = matchLoc.y + roi.y + halfTemplateHeight;

				results.add(new DTOImageSearchResult(true,
						new DTOPoint((int) centerX, (int) centerY), matchValue * 100.0));

				// Optimized suppression
				int suppressX = Math.max(0, (int) matchLoc.x - halfTemplateWidth);
				int suppressY = Math.max(0, (int) matchLoc.y - halfTemplateHeight);
				int suppressWidth = Math.min(templateWidth, resultCopy.cols() - suppressX);
				int suppressHeight = Math.min(templateHeight, resultCopy.rows() - suppressY);

				if (suppressWidth > 0 && suppressHeight > 0) {
					Rect suppressRect = new Rect(suppressX, suppressY, suppressWidth, suppressHeight);
					Mat suppressArea = new Mat(resultCopy, suppressRect);
					suppressArea.setTo(new org.opencv.core.Scalar(0));
					suppressArea.release();
				}
			}

		} catch (Exception e) {
			logger.error(formatLogMessage("Exception during optimized multiple template search"), e);
		} finally {
			// Explicit memory release
			if (mainImage != null)
				mainImage.release();
			if (template != null)
				template.release();
			if (imageROI != null)
				imageROI.release();
			if (matchResult != null)
				matchResult.release();
			if (resultCopy != null)
				resultCopy.release();
		}

		return results;
	}

	/**
	 * Grayscale search for encoded images (PNG/JPEG).
	 */
	private static DTOImageSearchResult searchTemplateGrayscaleOptimizedEncoded(byte[] image,
			String templateResourcePath,
			DTOPoint topLeftCorner, DTOPoint bottomRightCorner, double thresholdPercentage) {

		Mat imagenPrincipal = null;
		Mat imagenPrincipalGray = null;
		Mat template = null;
		Mat imagenROI = null;
		Mat resultado = null;

		try {
			// Quick ROI validation
			int roiX = topLeftCorner.getX();
			int roiY = topLeftCorner.getY();
			int roiWidth = bottomRightCorner.getX() - topLeftCorner.getX();
			int roiHeight = bottomRightCorner.getY() - topLeftCorner.getY();

			if (roiWidth <= 0 || roiHeight <= 0) {
				logger.error(formatLogMessage("Invalid ROI dimensions"));
				return new DTOImageSearchResult(false, null, 0.0);
			}

			// Decoding of main image
			MatOfByte matOfByte = new MatOfByte(image);
			imagenPrincipal = Imgcodecs.imdecode(matOfByte, Imgcodecs.IMREAD_COLOR);

			if (imagenPrincipal.empty()) {
				return new DTOImageSearchResult(false, null, 0.0);
			}

			// Convert main image to grayscale
			imagenPrincipalGray = new Mat();
			Imgproc.cvtColor(imagenPrincipal, imagenPrincipalGray, Imgproc.COLOR_BGR2GRAY);
			imagenPrincipal.release();
			imagenPrincipal = null;

			// Load optimized grayscale template with cache
			template = loadTemplateGrayscale(templateResourcePath);
			if (template.empty()) {
				return new DTOImageSearchResult(false, null, 0.0);
			}

			// ROI vs image validation
			if (roiX + roiWidth > imagenPrincipalGray.cols() || roiY + roiHeight > imagenPrincipalGray.rows()) {
				logger.error(formatLogMessage("ROI exceeds image dimensions"));
				return new DTOImageSearchResult(false, null, 0.0);
			}

			// Create ROI
			Rect roi = new Rect(roiX, roiY, roiWidth, roiHeight);
			imagenROI = new Mat(imagenPrincipalGray, roi);

			// Optimized size check
			int resultCols = imagenROI.cols() - template.cols() + 1;
			int resultRows = imagenROI.rows() - template.rows() + 1;
			if (resultCols <= 0 || resultRows <= 0) {
				return new DTOImageSearchResult(false, null, 0.0);
			}

			// Template matching
			resultado = new Mat(resultRows, resultCols, CvType.CV_32FC1);
			Imgproc.matchTemplate(imagenROI, template, resultado, Imgproc.TM_CCOEFF_NORMED);

			// Search for the best match
			Core.MinMaxLocResult mmr = Core.minMaxLoc(resultado);
			double matchPercentage = mmr.maxVal * 100.0;

			if (matchPercentage < thresholdPercentage) {
				logger.warn(formatLogMessage("Grayscale template " + templateResourcePath + " match percentage "
						+ matchPercentage + " below threshold " + thresholdPercentage));
				return new DTOImageSearchResult(false, null, matchPercentage);
			}

			logger.info(formatLogMessage(
					"Grayscale template " + templateResourcePath + " found with match percentage: " + matchPercentage));

			// Calculate center point of the match (taking ROI into account)
			int centerX = (int) (mmr.maxLoc.x + (double) template.cols() / 2 + roiX);
			int centerY = (int) (mmr.maxLoc.y + (double) template.rows() / 2 + roiY);

			return new DTOImageSearchResult(true, new DTOPoint(centerX, centerY), matchPercentage);

		} catch (Exception e) {
			logger.error(formatLogMessage("Exception during grayscale template search"), e);
			return new DTOImageSearchResult(false, null, 0.0);
		} finally {
			// Explicit memory release for all Mat objects
			if (imagenPrincipal != null)
				imagenPrincipal.release();
			if (imagenPrincipalGray != null)
				imagenPrincipalGray.release();
			if (template != null)
				template.release();
			if (imagenROI != null)
				imagenROI.release();
			if (resultado != null)
				resultado.release();
		}
	}

	/**
	 * Grayscale search for multiple matches in encoded images (PNG/JPEG).
	 */
	private static List<DTOImageSearchResult> searchTemplateGrayscaleMultipleOptimizedEncoded(byte[] image,
			String templateResourcePath, DTOPoint topLeftCorner, DTOPoint bottomRightCorner,
			double thresholdPercentage, int maxResults) {

		List<DTOImageSearchResult> results = new ArrayList<>();
		Mat mainImage = null;
		Mat mainImageGray = null;
		Mat template = null;
		Mat imageROI = null;
		Mat matchResult = null;
		Mat resultCopy = null;

		try {
			// Quick ROI validation
			int roiX = topLeftCorner.getX();
			int roiY = topLeftCorner.getY();
			int roiWidth = bottomRightCorner.getX() - topLeftCorner.getX();
			int roiHeight = bottomRightCorner.getY() - topLeftCorner.getY();

			if (roiWidth <= 0 || roiHeight <= 0) {
				return results;
			}

			// Optimized decoding
			MatOfByte matOfByte = new MatOfByte(image);
			mainImage = Imgcodecs.imdecode(matOfByte, Imgcodecs.IMREAD_COLOR);

			if (mainImage.empty()) {
				return results;
			}

			// Convert to grayscale
			mainImageGray = new Mat();
			Imgproc.cvtColor(mainImage, mainImageGray, Imgproc.COLOR_BGR2GRAY);
			mainImage.release();
			mainImage = null;

			// Load grayscale template with cache
			template = loadTemplateGrayscale(templateResourcePath);
			if (template.empty()) {
				return results;
			}

			// Validations
			if (roiX + roiWidth > mainImageGray.cols() || roiY + roiHeight > mainImageGray.rows()) {
				return results;
			}

			// Create ROI
			Rect roi = new Rect(roiX, roiY, roiWidth, roiHeight);
			imageROI = new Mat(mainImageGray, roi);

			int resultCols = imageROI.cols() - template.cols() + 1;
			int resultRows = imageROI.rows() - template.rows() + 1;
			if (resultCols <= 0 || resultRows <= 0) {
				return results;
			}

			// Template matching
			matchResult = new Mat(resultRows, resultCols, CvType.CV_32FC1);
			Imgproc.matchTemplate(imageROI, template, matchResult, Imgproc.TM_CCOEFF_NORMED);

			// Optimized search for multiple matches
			double thresholdDecimal = thresholdPercentage / 100.0;
			resultCopy = matchResult.clone();
			int templateWidth = template.cols();
			int templateHeight = template.rows();

			// Pre-calculate for optimization
			int halfTemplateWidth = templateWidth / 2;
			int halfTemplateHeight = templateHeight / 2;

			while (results.size() < maxResults || maxResults <= 0) {
				Core.MinMaxLocResult mmr = Core.minMaxLoc(resultCopy);
				double matchValue = mmr.maxVal;

				if (matchValue < thresholdDecimal) {
					break;
				}

				Point matchLoc = mmr.maxLoc;
				double centerX = matchLoc.x + roi.x + halfTemplateWidth;
				double centerY = matchLoc.y + roi.y + halfTemplateHeight;

				results.add(new DTOImageSearchResult(true,
						new DTOPoint((int) centerX, (int) centerY), matchValue * 100.0));

				// Optimized suppression
				int suppressX = Math.max(0, (int) matchLoc.x - halfTemplateWidth);
				int suppressY = Math.max(0, (int) matchLoc.y - halfTemplateHeight);
				int suppressWidth = Math.min(templateWidth, resultCopy.cols() - suppressX);
				int suppressHeight = Math.min(templateHeight, resultCopy.rows() - suppressY);

				if (suppressWidth > 0 && suppressHeight > 0) {
					Rect suppressRect = new Rect(suppressX, suppressY, suppressWidth, suppressHeight);
					Mat suppressArea = new Mat(resultCopy, suppressRect);
					suppressArea.setTo(new org.opencv.core.Scalar(0));
					suppressArea.release();
				}
			}

		} catch (Exception e) {
			logger.error(formatLogMessage("Exception during optimized multiple template search"), e);
		} finally {
			// Explicit memory release
			if (mainImage != null)
				mainImage.release();
			if (template != null)
				template.release();
			if (imageROI != null)
				imageROI.release();
			if (matchResult != null)
				matchResult.release();
			if (resultCopy != null)
				resultCopy.release();
		}

		return results;
	}

	/**
	 * Grayscale search for raw image data.
	 */
	private static DTOImageSearchResult searchTemplateGrayscaleOptimizedRaw(byte[] rawImageData, int width, int height,
			int bpp,
			String templateResourcePath, DTOPoint topLeftCorner, DTOPoint bottomRightCorner,
			double thresholdPercentage) {

		Mat imagenPrincipal = null;
		Mat imagenPrincipalGray = null;
		Mat template = null;
		Mat imagenROI = null;
		Mat resultado = null;

		try {
			// Convert raw image data directly to OpenCV Mat
			imagenPrincipal = convertRawDataToMat(rawImageData, width, height, bpp);

			if (imagenPrincipal.empty()) {
				return new DTOImageSearchResult(false, null, 0.0);
			}

			// Quick ROI validation
			int roiX = topLeftCorner.getX();
			int roiY = topLeftCorner.getY();
			int roiWidth = bottomRightCorner.getX() - topLeftCorner.getX();
			int roiHeight = bottomRightCorner.getY() - topLeftCorner.getY();

			if (roiWidth <= 0 || roiHeight <= 0) {
				logger.error(formatLogMessage("Invalid ROI dimensions"));
				return new DTOImageSearchResult(false, null, 0.0);
			}

			// Convert main image to grayscale
			imagenPrincipalGray = new Mat();
			Imgproc.cvtColor(imagenPrincipal, imagenPrincipalGray, Imgproc.COLOR_BGR2GRAY);
			imagenPrincipal.release();
			imagenPrincipal = null;

			// Load optimized grayscale template with cache
			template = loadTemplateGrayscale(templateResourcePath);
			if (template.empty()) {
				return new DTOImageSearchResult(false, null, 0.0);
			}

			// ROI vs image validation
			if (roiX + roiWidth > imagenPrincipalGray.cols() || roiY + roiHeight > imagenPrincipalGray.rows()) {
				logger.error(formatLogMessage("ROI exceeds image dimensions"));
				return new DTOImageSearchResult(false, null, 0.0);
			}

			// Create ROI
			Rect roi = new Rect(roiX, roiY, roiWidth, roiHeight);
			imagenROI = new Mat(imagenPrincipalGray, roi);

			// Optimized size check
			int resultCols = imagenROI.cols() - template.cols() + 1;
			int resultRows = imagenROI.rows() - template.rows() + 1;
			if (resultCols <= 0 || resultRows <= 0) {
				return new DTOImageSearchResult(false, null, 0.0);
			}

			// Template matching
			resultado = new Mat(resultRows, resultCols, CvType.CV_32FC1);
			Imgproc.matchTemplate(imagenROI, template, resultado, Imgproc.TM_CCOEFF_NORMED);

			// Search for the best match
			Core.MinMaxLocResult mmr = Core.minMaxLoc(resultado);
			double matchPercentage = mmr.maxVal * 100.0;

			if (matchPercentage < thresholdPercentage) {
				logger.warn(formatLogMessage("Grayscale template " + templateResourcePath + " match percentage "
						+ matchPercentage + " below threshold " + thresholdPercentage));
				return new DTOImageSearchResult(false, null, matchPercentage);
			}

			logger.info(formatLogMessage(
					"Grayscale template " + templateResourcePath + " found with match percentage: " + matchPercentage));

			// Calculate center point of the match (taking ROI into account)
			int centerX = (int) (mmr.maxLoc.x + (double) template.cols() / 2 + roiX);
			int centerY = (int) (mmr.maxLoc.y + (double) template.rows() / 2 + roiY);

			return new DTOImageSearchResult(true, new DTOPoint(centerX, centerY), matchPercentage);

		} catch (Exception e) {
			logger.error(formatLogMessage("Exception during grayscale template search with raw data"), e);
			return new DTOImageSearchResult(false, null, 0.0);
		} finally {
			// Explicit memory release for all Mat objects
			if (imagenPrincipal != null)
				imagenPrincipal.release();
			if (imagenPrincipalGray != null)
				imagenPrincipalGray.release();
			if (template != null)
				template.release();
			if (imagenROI != null)
				imagenROI.release();
			if (resultado != null)
				resultado.release();
		}
	}

	/**
	 * Grayscale search for multiple matches using raw image data.
	 */
	private static List<DTOImageSearchResult> searchTemplateGrayscaleMultipleOptimizedRaw(byte[] rawImageData,
			int width, int height, int bpp,
			String templateResourcePath, DTOPoint topLeftCorner, DTOPoint bottomRightCorner,
			double thresholdPercentage, int maxResults) {

		List<DTOImageSearchResult> results = new ArrayList<>();
		Mat mainImage = null;
		Mat mainImageGray = null;
		Mat template = null;
		Mat imageROI = null;
		Mat matchResult = null;
		Mat resultCopy = null;

		try {
			// Convert raw image data directly to OpenCV Mat
			mainImage = convertRawDataToMat(rawImageData, width, height, bpp);

			if (mainImage.empty()) {
				return results;
			}

			// Quick ROI validation
			int roiX = topLeftCorner.getX();
			int roiY = topLeftCorner.getY();
			int roiWidth = bottomRightCorner.getX() - topLeftCorner.getX();
			int roiHeight = bottomRightCorner.getY() - topLeftCorner.getY();

			if (roiWidth <= 0 || roiHeight <= 0) {
				return results;
			}

			// Convert to grayscale
			mainImageGray = new Mat();
			Imgproc.cvtColor(mainImage, mainImageGray, Imgproc.COLOR_BGR2GRAY);
			mainImage.release();
			mainImage = null;

			// Load grayscale template with cache
			template = loadTemplateGrayscale(templateResourcePath);
			if (template.empty()) {
				return results;
			}

			// Validations
			if (roiX + roiWidth > mainImageGray.cols() || roiY + roiHeight > mainImageGray.rows()) {
				return results;
			}

			// Create ROI
			Rect roi = new Rect(roiX, roiY, roiWidth, roiHeight);
			imageROI = new Mat(mainImageGray, roi);

			int resultCols = imageROI.cols() - template.cols() + 1;
			int resultRows = imageROI.rows() - template.rows() + 1;
			if (resultCols <= 0 || resultRows <= 0) {
				return results;
			}

			// Template matching
			matchResult = new Mat(resultRows, resultCols, CvType.CV_32FC1);
			Imgproc.matchTemplate(imageROI, template, matchResult, Imgproc.TM_CCOEFF_NORMED);

			// Optimized search for multiple matches
			double thresholdDecimal = thresholdPercentage / 100.0;
			resultCopy = matchResult.clone();
			int templateWidth = template.cols();
			int templateHeight = template.rows();

			// Pre-calculate for optimization
			int halfTemplateWidth = templateWidth / 2;
			int halfTemplateHeight = templateHeight / 2;

			while (results.size() < maxResults || maxResults <= 0) {
				Core.MinMaxLocResult mmr = Core.minMaxLoc(resultCopy);
				double matchValue = mmr.maxVal;

				if (matchValue < thresholdDecimal) {
					break;
				}

				Point matchLoc = mmr.maxLoc;
				double centerX = matchLoc.x + roi.x + halfTemplateWidth;
				double centerY = matchLoc.y + roi.y + halfTemplateHeight;

				results.add(new DTOImageSearchResult(true,
						new DTOPoint((int) centerX, (int) centerY), matchValue * 100.0));

				// Optimized suppression
				int suppressX = Math.max(0, (int) matchLoc.x - halfTemplateWidth);
				int suppressY = Math.max(0, (int) matchLoc.y - halfTemplateHeight);
				int suppressWidth = Math.min(templateWidth, resultCopy.cols() - suppressX);
				int suppressHeight = Math.min(templateHeight, resultCopy.rows() - suppressY);

				if (suppressWidth > 0 && suppressHeight > 0) {
					Rect suppressRect = new Rect(suppressX, suppressY, suppressWidth, suppressHeight);
					Mat suppressArea = new Mat(resultCopy, suppressRect);
					suppressArea.setTo(new org.opencv.core.Scalar(0));
					suppressArea.release();
				}
			}

		} catch (Exception e) {
			logger.error(formatLogMessage("Exception during optimized multiple grayscale template search"), e);
		} finally {
			// Explicit memory release
			if (mainImage != null)
				mainImage.release();
			if (template != null)
				template.release();
			if (imageROI != null)
				imageROI.release();
			if (matchResult != null)
				matchResult.release();
			if (resultCopy != null)
				resultCopy.release();
		}

		return results;
	}

	/**
	 * Multiple template search using raw image data.
	 */
	private static List<DTOImageSearchResult> searchTemplateMultipleOptimizedRaw(byte[] rawImageData, int width,
			int height, int bpp,
			String templateResourcePath, DTOPoint topLeftCorner, DTOPoint bottomRightCorner,
			double thresholdPercentage, int maxResults) {

		List<DTOImageSearchResult> results = new ArrayList<>();
		Mat mainImage = null;
		Mat template = null;
		Mat imageROI = null;
		Mat matchResult = null;
		Mat resultCopy = null;

		try {
			// Convert raw image data directly to OpenCV Mat
			mainImage = convertRawDataToMat(rawImageData, width, height, bpp);

			if (mainImage.empty()) {
				return results;
			}

			// Quick ROI validation
			int roiX = topLeftCorner.getX();
			int roiY = topLeftCorner.getY();
			int roiWidth = bottomRightCorner.getX() - topLeftCorner.getX();
			int roiHeight = bottomRightCorner.getY() - topLeftCorner.getY();

			if (roiWidth <= 0 || roiHeight <= 0) {
				return results;
			}

			// Load template with cache
			template = loadTemplateOptimized(templateResourcePath);
			if (template.empty()) {
				return results;
			}

			// Validations
			if (roiX + roiWidth > mainImage.cols() || roiY + roiHeight > mainImage.rows()) {
				return results;
			}

			// Create ROI
			Rect roi = new Rect(roiX, roiY, roiWidth, roiHeight);
			imageROI = new Mat(mainImage, roi);

			int resultCols = imageROI.cols() - template.cols() + 1;
			int resultRows = imageROI.rows() - template.rows() + 1;
			if (resultCols <= 0 || resultRows <= 0) {
				return results;
			}

			// Template matching
			matchResult = new Mat(resultRows, resultCols, CvType.CV_32FC1);
			Imgproc.matchTemplate(imageROI, template, matchResult, Imgproc.TM_CCOEFF_NORMED);

			// Optimized search for multiple matches
			double thresholdDecimal = thresholdPercentage / 100.0;
			resultCopy = matchResult.clone();
			int templateWidth = template.cols();
			int templateHeight = template.rows();

			// Pre-calculate for optimization
			int halfTemplateWidth = templateWidth / 2;
			int halfTemplateHeight = templateHeight / 2;

			while (results.size() < maxResults || maxResults <= 0) {
				Core.MinMaxLocResult mmr = Core.minMaxLoc(resultCopy);
				double matchValue = mmr.maxVal;

				if (matchValue < thresholdDecimal) {
					break;
				}

				Point matchLoc = mmr.maxLoc;
				double centerX = matchLoc.x + roi.x + halfTemplateWidth;
				double centerY = matchLoc.y + roi.y + halfTemplateHeight;

				results.add(new DTOImageSearchResult(true,
						new DTOPoint((int) centerX, (int) centerY), matchValue * 100.0));

				// Optimized suppression
				int suppressX = Math.max(0, (int) matchLoc.x - halfTemplateWidth);
				int suppressY = Math.max(0, (int) matchLoc.y - halfTemplateHeight);
				int suppressWidth = Math.min(templateWidth, resultCopy.cols() - suppressX);
				int suppressHeight = Math.min(templateHeight, resultCopy.rows() - suppressY);

				if (suppressWidth > 0 && suppressHeight > 0) {
					Rect suppressRect = new Rect(suppressX, suppressY, suppressWidth, suppressHeight);
					Mat suppressArea = new Mat(resultCopy, suppressRect);
					suppressArea.setTo(new org.opencv.core.Scalar(0));
					suppressArea.release();
				}
			}

		} catch (Exception e) {
			logger.error(formatLogMessage("Exception during optimized multiple template search with raw data"), e);
		} finally {
			// Explicit memory release
			if (mainImage != null)
				mainImage.release();
			if (template != null)
				template.release();
			if (imageROI != null)
				imageROI.release();
			if (matchResult != null)
				matchResult.release();
			if (resultCopy != null)
				resultCopy.release();
		}

		return results;
	}

	/**
	 * Method for preloading common templates.
	 */
	public static void preloadTemplate(String templateResourcePath) {
		openCVThreadPool.submit(() -> loadTemplateOptimized(templateResourcePath));
	}

	/**
	 * Method to clear cache manually.
	 */
	public static void clearCache() {
		templateCache.values().forEach(Mat::release);
		templateCache.clear();
		grayscaleTemplateCache.values().forEach(Mat::release);
		grayscaleTemplateCache.clear();
		templateBytesCache.clear();
		cacheInitialized = false;
	}

	/**
	 * Search for a template using the EnumTemplates enum directly.
	 */
	public static DTOImageSearchResult searchTemplate(byte[] image, EnumTemplates enumTemplate,
			DTOPoint topLeftCorner, DTOPoint bottomRightCorner, double thresholdPercentage) {
		return searchTemplate(image, enumTemplate.getTemplate(), topLeftCorner, bottomRightCorner, thresholdPercentage);
	}

	/**
	 * Search for multiple templates using the EnumTemplates enum directly.
	 */
	public static List<DTOImageSearchResult> searchTemplateMultiple(byte[] image, EnumTemplates enumTemplate,
			DTOPoint topLeftCorner, DTOPoint bottomRightCorner, double thresholdPercentage, int maxResults) {
		return searchTemplateMultiple(image, enumTemplate.getTemplate(), topLeftCorner, bottomRightCorner,
				thresholdPercentage, maxResults);
	}

	/**
	 * Search for a template using grayscale and the EnumTemplates enum directly.
	 */
	public static DTOImageSearchResult searchTemplateGrayscale(byte[] image, EnumTemplates enumTemplate,
			DTOPoint topLeftCorner, DTOPoint bottomRightCorner, double thresholdPercentage) {
		return searchTemplateGrayscale(image, enumTemplate.getTemplate(), topLeftCorner, bottomRightCorner,
				thresholdPercentage);
	}

	/**
	 * Search for multiple templates using grayscale and the EnumTemplates enum
	 * directly.
	 */
	public static List<DTOImageSearchResult> searchTemplateGrayscaleMultiple(byte[] image, EnumTemplates enumTemplate,
			DTOPoint topLeftCorner, DTOPoint bottomRightCorner, double thresholdPercentage, int maxResults) {
		return searchTemplateGrayscaleMultiple(image, enumTemplate.getTemplate(), topLeftCorner, bottomRightCorner,
				thresholdPercentage, maxResults);
	}

	/**
	 * Asynchronous version using enum.
	 */
	public static CompletableFuture<List<DTOImageSearchResult>> searchTemplateMultipleAsync(
			byte[] image, EnumTemplates enumTemplate, DTOPoint topLeftCorner,
			DTOPoint bottomRightCorner, double thresholdPercentage, int maxResults) {
		return searchTemplateMultipleAsync(image, enumTemplate.getTemplate(), topLeftCorner, bottomRightCorner,
				thresholdPercentage, maxResults);
	}

	/**
	 * Checks if the cache is fully initialized.
	 */
	public static boolean isCacheInitialized() {
		return cacheInitialized;
	}

	/**
	 * Gets cache statistics.
	 */
	public static String getCacheStats() {
		return String.format("Templates in cache: %d/%d, Bytes cache: %d",
				templateCache.size(), EnumTemplates.values().length, templateBytesCache.size());
	}

	public static void loadNativeLibrary(String resourcePath) throws IOException {
		// Get the file name from the resource path
		String[] parts = resourcePath.split("/");
		String libFileName = parts[parts.length - 1];

		// Create the lib/opencv directory if it doesn't exist
		File libDir = new File("lib/opencv");
		if (!libDir.exists()) {
			libDir.mkdirs();
		}

		// Create the destination file in lib/opencv
		File destLib = new File(libDir, libFileName);

		// Open the resource as a stream
		try (InputStream in = ImageSearchUtil.class.getResourceAsStream(resourcePath);
				OutputStream out = new FileOutputStream(destLib)) {
			if (in == null) {
				logger.error(formatLogMessage("Resource not found: " + resourcePath));
				throw new IOException("Resource not found: " + resourcePath);
			}
			byte[] buffer = new byte[4096];
			int bytesRead;
			while ((bytesRead = in.read(buffer)) != -1) {
				out.write(buffer, 0, bytesRead);
			}
		} catch (IOException e) {
			logger.error(formatLogMessage("Error extracting native library: " + e.getMessage()));
			throw e;
		}

		// Load the library using the absolute path of the destination file
		System.load(destLib.getAbsolutePath());
		logger.info(formatLogMessage("Native library loaded from: " + destLib.getPath())); // print a relative path for
																							// privacy when sharing logs
	}
}

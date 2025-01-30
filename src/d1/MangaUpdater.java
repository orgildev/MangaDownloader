package d1;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

public class MangaUpdater extends DownloadManga {
    private static final String DATA_FILE = "data.txt";
    private static final String MANGAS_DIR = "Mangas";
    private static final String CHAPTER_PREFIX = "Chapter ";
    private static final int MAX_CONSECUTIVE_FAILURES = 5;
    private static final int CHECK_INTERVAL_MS = 2000;
    private static final int UPDATE_TIMEOUT_HOURS = 5;
    private static final int DOWNLOAD_TIMEOUT_HOURS = 1;
    private static final String[] SUPPORTED_FILE_TYPES = {".jpg", ".webp", ".png"};
    
    private static final int UPDATE_THREADS = Math.min(Runtime.getRuntime().availableProcessors(), 5);
    private static final int DOWNLOAD_THREADS = 3;

    private static class MangaConfig {
        String name;
        int formatType;
        String formatPattern;
        
        MangaConfig(String name, int formatType) {
            this.name = name;
            this.formatType = formatType;
            this.formatPattern = getChapterFormat(formatType);
        }
    }

    private Map<String, MangaConfig> configs;
    private Map<String, String> detectedFormats; // Stores detected format patterns per manga
    private ExecutorService executor;
    private AtomicInteger totalNewChapters;

    public MangaUpdater() {
        configs = loadMangaConfigs();
        detectedFormats = new HashMap<>();
        totalNewChapters = new AtomicInteger(0);
    }

    private static String getChapterFormat(int formatType) {
        switch (formatType) {
            case 1: return "%d";    // 1, 2, 3
            case 2: return "%02d";  // 01, 02, 03
            case 3: return "%03d";  // 001, 002, 003
            default: return "%03d"; // default to 3 digits
        }
    }

    private String[] getChapterFormats(String mangaName, int chapter) {
        // If we've detected a format for this manga, try that first
        String detectedFormat = detectedFormats.get(mangaName);
        if (detectedFormat != null) {
            return new String[]{ String.format(detectedFormat, chapter) };
        }

        // Otherwise try formats in priority order
        return new String[]{
            String.format("%03d", chapter),    // Try 3 digits first (001)
            String.format("%02d", chapter),    // Then 2 digits (01)
            String.format("%d", chapter)       // Finally no padding (1)
        };
    }

    private void setDetectedFormat(String mangaName, String chapterFormat) {
        if (!detectedFormats.containsKey(mangaName)) {
            String pattern;
            if (chapterFormat.length() == 1) {
                pattern = "%d";      // e.g., "1"
            } else if (chapterFormat.length() == 2) {
                pattern = "%02d";    // e.g., "01"
            } else {
                pattern = "%03d";    // e.g., "001"
            }
            detectedFormats.put(mangaName, pattern);
            System.out.println("[INFO] Detected format pattern for " + mangaName + ": " + pattern);
        }
    }

    private boolean downloadChapterPages(String name, Path mangaPath, int chapter, String format) {
        String folderName = mangaPath.resolve(CHAPTER_PREFIX + chapter).toString();
        String page = "01";
        
        // Try each possible chapter format
        for (String chapterFormat : getChapterFormats(name, chapter)) {
            // Try each file type
            for (String fileType : SUPPORTED_FILE_TYPES) {
                String url = buildPageUrl(name, chapterFormat, page, fileType);
                if (download(folderName, page + fileType, url)) {
                    setDetectedFormat(name, chapterFormat);
                    System.out.println("[INFO] Found chapter " + chapter + " using format: " + chapterFormat);
                    downloadChapter(name, mangaPath, chapter, format);
                    return true;
                }
            }
        }
        return false;
    }

    private void downloadMissingChapters(String name, Path mangaPath, String format, Set<Integer> existingChapters, 
            int lastChapter, ExecutorService downloadExecutor, AtomicInteger newChapters) {
        List<Integer> missingChapters = new ArrayList<>();
        for (int i = 1; i <= lastChapter; i++) {
            if (!existingChapters.contains(i)) {
                missingChapters.add(i);
            }
        }
        
        if (!missingChapters.isEmpty()) {
            System.out.println("[INFO] Found " + missingChapters.size() + " missing chapters in sequence");
            for (int chapter : missingChapters) {
                if (downloadChapterPages(name, mangaPath, chapter, format)) {
                    newChapters.incrementAndGet();
                    System.out.println("[SUCCESS] Downloaded missing chapter " + chapter);
                }
            }
        }
    }

    private void checkForNewChapters(String name, Path mangaPath, String format, int lastChapter,
            ExecutorService downloadExecutor, AtomicInteger newChapters) throws InterruptedException {
        int currentChapter = lastChapter + 1;
        int consecutiveFailures = 0;

        while (consecutiveFailures < MAX_CONSECUTIVE_FAILURES) {
            if (downloadChapterPages(name, mangaPath, currentChapter, format)) {
                consecutiveFailures = 0;
                newChapters.incrementAndGet();
                System.out.println("[SUCCESS] Downloaded new chapter " + currentChapter);
            } else {
                consecutiveFailures++;
                if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
                    System.out.println("[INFO] No more chapters found after " + MAX_CONSECUTIVE_FAILURES + " attempts");
                    break;
                }
            }
            currentChapter++;
            Thread.sleep(CHECK_INTERVAL_MS);
        }
    }

    public int updateAllMangas() {
        Path mangasDir = Paths.get(MANGAS_DIR);
        if (!Files.exists(mangasDir)) {
            System.err.println("[ERROR] " + MANGAS_DIR + " directory not found");
            return 0;
        }

        executor = Executors.newFixedThreadPool(UPDATE_THREADS);
        
        try {
            processMangaSeries(mangasDir);
        } catch (IOException e) {
            System.err.println("[ERROR] Failed to read " + MANGAS_DIR + " directory: " + e.getMessage());
        } finally {
            shutdownExecutor(executor);
        }
        
        return totalNewChapters.get();
    }

    private void processMangaSeries(Path mangasDir) throws IOException {
        try (Stream<Path> paths = Files.list(mangasDir)) {
            paths.filter(Files::isDirectory)
                .forEach(mangaPath -> {
                    executor.submit(() -> processOneManga(mangaPath));
                });
        }
    }

    private void processOneManga(Path mangaPath) {
        String mangaName = mangaPath.getFileName().toString();
        System.out.println("\n[INFO] Checking updates for: " + mangaName);
        
        int newChapters;
        if (configs.containsKey(mangaName)) {
            newChapters = updateWithKnownFormat(mangaName, configs.get(mangaName));
        } else {
            newChapters = updateWithUnknownFormat(mangaName);
        }
        
        if (newChapters > 0) {
            totalNewChapters.addAndGet(newChapters);
            System.out.println("[SUCCESS] Downloaded " + newChapters + " new chapter(s) for " + mangaName);
        } else {
            System.out.println("[INFO] No new or missing chapters found for " + mangaName);
        }
    }

    private int updateWithKnownFormat(String mangaName, MangaConfig config) {
        System.out.println("[INFO] Using format type " + config.formatType + " for " + mangaName);
        return checkAndDownloadAllChapters(mangaName, config.formatPattern);
    }

    private int updateWithUnknownFormat(String mangaName) {
        System.out.println("[INFO] No format specified for " + mangaName + ", format will be auto-detected");
        return checkAndDownloadAllChapters(mangaName, "%03d"); // Starting format doesn't matter, will be auto-detected
    }

    private int checkAndDownloadAllChapters(String name, String format) {
        Path mangaPath = Paths.get(MANGAS_DIR, name);
        if (!Files.exists(mangaPath)) {
            System.err.println("[ERROR] Manga folder not found: " + name);
            return 0;
        }

        Set<Integer> existingChapters = findExistingChapters(mangaPath);
        int lastChapter = existingChapters.isEmpty() ? 0 : existingChapters.stream().max(Integer::compareTo).get();
        System.out.println("[INFO] Last downloaded chapter for " + name + ": " + lastChapter);
        
        ExecutorService downloadExecutor = Executors.newFixedThreadPool(DOWNLOAD_THREADS);
        AtomicInteger newChapters = new AtomicInteger(0);

        try {
            downloadMissingChapters(name, mangaPath, format, existingChapters, lastChapter, downloadExecutor, newChapters);
            checkForNewChapters(name, mangaPath, format, lastChapter, downloadExecutor, newChapters);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("[ERROR] Download process was interrupted");
        } finally {
            shutdownExecutor(downloadExecutor);
        }
        
        return newChapters.get();
    }

    private Map<String, MangaConfig> loadMangaConfigs() {
        Map<String, MangaConfig> configs = new HashMap<>();
        Path dataFile = Paths.get(DATA_FILE);
        
        if (!Files.exists(dataFile)) {
            System.err.println("[WARN] Data file not found: " + DATA_FILE);
            return configs;
        }

        try {
            Files.lines(dataFile).forEach(line -> {
                String[] parts = line.split(",");
                if (parts.length >= 3) {
                    try {
                        String mangaName = parts[0].trim();
                        int formatType = Integer.parseInt(parts[2].trim());
                        configs.put(mangaName, new MangaConfig(mangaName, formatType));
                    } catch (NumberFormatException e) {
                        System.err.println("[ERROR] Invalid format type in line: " + line);
                    }
                }
            });
        } catch (IOException e) {
            System.err.println("[ERROR] Failed to read " + DATA_FILE + ": " + e.getMessage());
        }
        
        return configs;
    }

    private Set<Integer> findExistingChapters(Path mangaPath) {
        Set<Integer> chapters = new TreeSet<>();
        try (Stream<Path> paths = Files.list(mangaPath)) {
            paths.filter(Files::isDirectory)
                .map(path -> path.getFileName().toString())
                .filter(name -> name.startsWith("Chapter "))
                .forEach(name -> {
                    try {
                        chapters.add(Integer.parseInt(name.substring(8).trim()));
                    } catch (NumberFormatException e) {
                        // Ignore invalid chapter numbers
                    }
                });
        } catch (IOException e) {
            System.err.println("[ERROR] Failed to read manga directory: " + e.getMessage());
        }
        return chapters;
    }

    private void shutdownExecutor(ExecutorService executor) {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(DOWNLOAD_TIMEOUT_HOURS, TimeUnit.HOURS)) {
                System.err.println("[WARN] Some downloads did not complete within " + DOWNLOAD_TIMEOUT_HOURS + " hour(s)");
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            System.err.println("[ERROR] Process was interrupted");
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private String buildPageUrl(String name, String chapter, String page, String fileType) {
        return "https://zuragtnom.site//uploads/manga/" + name + "/chapters/ch" + chapter + "/" + page + fileType;
    }
}

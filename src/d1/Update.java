package d1;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

public class Update extends DownloadManga {
    
    /**
     * Load format types from data.txt
     * @return Map of manga names to their format types
     */
    private static Map<String, Integer> loadFormatTypes() {
        Map<String, Integer> formatTypes = new HashMap<>();
        Path dataFile = Paths.get("data.txt");
        
        if (Files.exists(dataFile)) {
            try {
                Files.lines(dataFile).forEach(line -> {
                    String[] parts = line.split(",");
                    if (parts.length >= 3) {
                        String mangaName = parts[0];
                        try {
                            int formatType = Integer.parseInt(parts[2]);
                            formatTypes.put(mangaName, formatType);
                        } catch (NumberFormatException e) {
                            System.err.println("[ERROR] Invalid format type for manga: " + mangaName);
                        }
                    }
                });
            } catch (IOException e) {
                System.err.println("[ERROR] Failed to read data.txt: " + e.getMessage());
            }
        }
        
        return formatTypes;
    }
    
    /**
     * Get the appropriate chapter format string based on format type from data.txt
     * @param formatType 1 for "[1,2,3]", 2 for "[01,02,03]", 3 for "[001,002,003]"
     * @return Format string for chapter numbers
     */
    private static String getChapterFormat(int formatType) {
        switch (formatType) {
            case 1: return "%d";    // Simple numbers: 1, 2, 3
            case 2: return "%02d";  // Two digits: 01, 02, 03
            case 3: return "%03d";  // Three digits: 001, 002, 003
            default: // If format type is invalid, try all formats
                return null;
        }
    }
    
    /**
     * Updates all mangas in the Mangas directory
     * @return Total number of new chapters downloaded across all mangas
     */
    public static int updateAllMangas() {
        Path mangasDir = Paths.get("Mangas");
        if (!Files.exists(mangasDir)) {
            System.err.println("[ERROR] Mangas directory not found");
            return 0;
        }

        // Load format types for each manga
        Map<String, Integer> formatTypes = loadFormatTypes();
        AtomicInteger totalNewChapters = new AtomicInteger(0);
        
        // Create thread pool for concurrent manga updates
        int availableCores = Runtime.getRuntime().availableProcessors();
        int threadPoolSize = Math.min(availableCores, 5); // Limit to 5 concurrent manga updates
        ExecutorService executor = Executors.newFixedThreadPool(threadPoolSize);
        
        try (Stream<Path> paths = Files.list(mangasDir)) {
            paths.filter(Files::isDirectory)
                .forEach(mangaPath -> {
                    executor.submit(() -> {
                        String mangaName = mangaPath.getFileName().toString();
                        System.out.println("\n[INFO] Checking updates for: " + mangaName);
                        
                        int newChapters = 0;
                        
                        if (formatTypes.containsKey(mangaName)) {
                            // Use the specified format type from data.txt
                            int formatType = formatTypes.get(mangaName);
                            String format = getChapterFormat(formatType);
                            if (format != null) {
                                System.out.println("[INFO] Using format type " + formatType + " for " + mangaName);
                                newChapters = checkAndDownloadNewChapters(mangaName, format);
                            } else {
                                System.out.println("[WARN] Invalid format type " + formatType + " for " + mangaName + ", trying all formats...");
                                newChapters = tryAllFormats(mangaName);
                            }
                        } else {
                            System.out.println("[INFO] No format specified for " + mangaName + ", trying all formats...");
                            newChapters = tryAllFormats(mangaName);
                        }
                        
                        if (newChapters > 0) {
                            totalNewChapters.addAndGet(newChapters);
                            System.out.println("[SUCCESS] Downloaded " + newChapters + " new chapter(s) for " + mangaName);
                        } else {
                            System.out.println("[INFO] No new chapters found for " + mangaName);
                        }
                    });
                });
        } catch (IOException e) {
            System.err.println("[ERROR] Failed to read Mangas directory: " + e.getMessage());
            return totalNewChapters.get();
        }
        
        // Wait for all manga updates to complete
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.HOURS)) { // Allow up to 5 hours for all updates
                System.err.println("[WARN] Some manga updates did not complete within the expected time.");
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            System.err.println("[ERROR] Update process was interrupted.");
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        return totalNewChapters.get();
    }

    /**
     * Try all format types for a manga
     * @param name The name of the manga
     * @return Number of new chapters found
     */
    private static int tryAllFormats(String name) {
        for (int formatType = 1; formatType <= 3; formatType++) {
            String format = getChapterFormat(formatType);
            int newChapters = checkAndDownloadNewChapters(name, format);
            if (newChapters > 0) {
                System.out.println("[SUCCESS] Found correct format type: " + formatType);
                return newChapters;
            }
        }
        return 0;
    }

    /**
     * Checks and downloads new chapters for a manga
     * @param name The name of the manga
     * @param format The format string for chapter numbers
     * @return The number of new chapters downloaded
     */
    public static int checkAndDownloadNewChapters(String name, String format) {
        Path mangaPath = Paths.get("Mangas", name);
        if (!Files.exists(mangaPath)) {
            System.err.println("[ERROR] Manga folder not found: " + name);
            return 0;
        }

        // Find the highest chapter number currently downloaded
        int lastChapter = findLastChapter(mangaPath);
        System.out.println("[INFO] Last downloaded chapter for " + name + ": " + lastChapter);

        // Start checking from the last chapter (to ensure it's complete)
        int newChapters = 0;
        int currentChapter = Math.max(1, lastChapter); // Start from 1 if no chapters exist
        
        // Create thread pool for concurrent downloads
        int threadPoolSize = Math.min(Runtime.getRuntime().availableProcessors(), 3);
        ExecutorService executor = Executors.newFixedThreadPool(threadPoolSize);
        
        boolean moreChaptersExist = true;
        while (moreChaptersExist) {
            final int chapterToCheck = currentChapter;
            final boolean[] chapterExists = {false};
            
            // Try to download the first page of the chapter to check if it exists
            executor.submit(() -> {
                String chapter = String.format(format, chapterToCheck);
                String folderName = mangaPath.resolve("Chapter " + chapterToCheck).toString();
                String page = "01";
                
                // Delete existing chapter folder if it exists (to ensure complete download)
                if (chapterToCheck == lastChapter) {
                    try {
                        Files.walk(Paths.get(folderName))
                            .sorted((p1, p2) -> -p1.compareTo(p2)) // Reverse order for safe deletion
                            .forEach(path -> {
                                try {
                                    Files.deleteIfExists(path);
                                } catch (IOException e) {
                                    System.err.println("[ERROR] Failed to delete: " + path);
                                }
                            });
                    } catch (IOException e) {
                        System.err.println("[ERROR] Failed to clean chapter folder: " + folderName);
                    }
                }
                
                // Try each file type
                for (String fileType : new String[]{".jpg", ".webp", ".png"}) {
                    String url = "https://zuragtnom.site//uploads/manga/" + name + "/chapters/ch" + chapter + "/" + page + fileType;
                    if (download(folderName, page + fileType, url)) {
                        synchronized(chapterExists) {
                            chapterExists[0] = true;
                        }
                        // Download the rest of the chapter
                        downloadChapter(name, mangaPath, chapterToCheck, format);
                        break;
                    }
                }
            });
            
            // Wait a bit to check the result
            try {
                Thread.sleep(2000); // Wait 2 seconds
                synchronized(chapterExists) {
                    if (!chapterExists[0]) {
                        if (currentChapter > lastChapter) {
                            // Only stop if we've gone past the last known chapter
                            moreChaptersExist = false;
                        }
                    } else {
                        if (currentChapter > lastChapter) {
                            // Only count as new if it's beyond the last known chapter
                            newChapters++;
                        }
                    }
                    currentChapter++;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        
        // Shutdown executor and wait for remaining tasks
        executor.shutdown();
        try {
            if (!executor.awaitTermination(1, TimeUnit.HOURS)) {
                System.err.println("[WARN] Some downloads did not complete within the expected time.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        return newChapters;
    }
    
    /**
     * Finds the highest chapter number in the manga directory
     * @param mangaPath Path to the manga directory
     * @return The highest chapter number found, or 0 if no chapters exist
     */
    private static int findLastChapter(Path mangaPath) {
        try (Stream<Path> paths = Files.list(mangaPath)) {
            return paths
                .filter(Files::isDirectory)
                .map(path -> path.getFileName().toString())
                .filter(name -> name.startsWith("Chapter "))
                .mapToInt(name -> {
                    try {
                        return Integer.parseInt(name.substring(8).trim());
                    } catch (NumberFormatException e) {
                        return 0;
                    }
                })
                .max()
                .orElse(0);
        } catch (IOException e) {
            System.err.println("[ERROR] Failed to read manga directory: " + e.getMessage());
            return 0;
        }
    }

    /**
     * Example usage of the Update functionality
     */
    public static void main(String[] args) {
        System.out.println("Starting update check for all mangas...");
        int totalNewChapters = updateAllMangas();
        System.out.println("\nUpdate check complete.");
        
        if (totalNewChapters > 0) {
            System.out.println("Successfully downloaded " + totalNewChapters + " new chapter(s) total!");
        } else {
            System.out.println("No new chapters found for any manga.");
        }
    }
}

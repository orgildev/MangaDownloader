package d1;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

public class Update extends DownloadManga {
    
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
    
    private static String getChapterFormat(int formatType) {
        switch (formatType) {
            case 1: return "%d";    // 1, 2, 3
            case 2: return "%02d";  // 01, 02, 03
            case 3: return "%03d";  // 001, 002, 003
            default: return "%03d"; // default to 3 digits
        }
    }
    
    public static int updateAllMangas() {
        Path mangasDir = Paths.get("Mangas");
        if (!Files.exists(mangasDir)) {
            System.err.println("[ERROR] Mangas directory not found");
            return 0;
        }

        Map<String, Integer> formatTypes = loadFormatTypes();
        AtomicInteger totalNewChapters = new AtomicInteger(0);
        
        int availableCores = Runtime.getRuntime().availableProcessors();
        int threadPoolSize = Math.min(availableCores, 5);
        ExecutorService executor = Executors.newFixedThreadPool(threadPoolSize);
        
        try (Stream<Path> paths = Files.list(mangasDir)) {
            paths.filter(Files::isDirectory)
                .forEach(mangaPath -> {
                    executor.submit(() -> {
                        String mangaName = mangaPath.getFileName().toString();
                        System.out.println("\n[INFO] Checking updates for: " + mangaName);
                        
                        int newChapters = 0;
                        if (!formatTypes.containsKey(mangaName)) {
                            System.out.println("[INFO] No format specified for " + mangaName + ", trying all formats...");
                            for (int formatType = 1; formatType <= 3; formatType++) {
                                String format = getChapterFormat(formatType);
                                newChapters = checkAndDownloadAllChapters(mangaName, format);
                                if (newChapters > 0) {
                                    System.out.println("[SUCCESS] Found correct format type: " + formatType);
                                    break;
                                }
                            }
                        } else {
                            int formatType = formatTypes.get(mangaName);
                            String format = getChapterFormat(formatType);
                            System.out.println("[INFO] Using format type " + formatType + " for " + mangaName);
                            newChapters = checkAndDownloadAllChapters(mangaName, format);
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
        
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.HOURS)) {
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

    private static Set<Integer> findExistingChapters(Path mangaPath) {
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

    public static int checkAndDownloadAllChapters(String name, String format) {
        Path mangaPath = Paths.get("Mangas", name);
        if (!Files.exists(mangaPath)) {
            System.err.println("[ERROR] Manga folder not found: " + name);
            return 0;
        }

        Set<Integer> existingChapters = findExistingChapters(mangaPath);
        int lastChapter = existingChapters.isEmpty() ? 0 : existingChapters.stream().max(Integer::compareTo).get();
        System.out.println("[INFO] Last downloaded chapter for " + name + ": " + lastChapter);
        
        int newChapters = 0;
        int currentChapter = 1; // Start from chapter 1
        int consecutiveFailures = 0;
        int maxConsecutiveFailures = 5;
        
        ExecutorService executor = Executors.newFixedThreadPool(3);
        
        while (consecutiveFailures < maxConsecutiveFailures) {
            final int chapterToCheck = currentChapter;
            if (!existingChapters.contains(chapterToCheck) || chapterToCheck == lastChapter) {
                final boolean[] chapterExists = {false};
                
                executor.submit(() -> {
                    String chapter = String.format(format, chapterToCheck);
                    String folderName = mangaPath.resolve("Chapter " + chapterToCheck).toString();
                    String page = "01";
                    
                    if (chapterToCheck == lastChapter) {
                        try {
                            Files.walk(Paths.get(folderName))
                                .sorted((p1, p2) -> -p1.compareTo(p2))
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
                    
                    for (String fileType : new String[]{".jpg", ".webp", ".png"}) {
                        String url = "https://zuragtnom.site//uploads/manga/" + name + "/chapters/ch" + chapter + "/" + page + fileType;
                        if (download(folderName, page + fileType, url)) {
                            synchronized(chapterExists) {
                                chapterExists[0] = true;
                            }
                            downloadChapter(name, mangaPath, chapterToCheck, format);
                            break;
                        }
                    }
                });
                
                try {
                    Thread.sleep(2000);
                    synchronized(chapterExists) {
                        if (!chapterExists[0]) {
                            consecutiveFailures++;
                            if (currentChapter > lastChapter) {
                                if (consecutiveFailures >= maxConsecutiveFailures) {
                                    break;
                                }
                            }
                        } else {
                            consecutiveFailures = 0;
                            if (!existingChapters.contains(chapterToCheck)) {
                                newChapters++;
                                System.out.println("[INFO] Downloaded chapter " + chapterToCheck);
                            }
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            currentChapter++;
        }
        
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

    public static void main(String[] args) {
        System.out.println("Starting update check and checking for missing chapters...");
        int totalNewChapters = updateAllMangas();
        System.out.println("\nUpdate check complete.");
        
        if (totalNewChapters > 0) {
            System.out.println("Successfully downloaded " + totalNewChapters + " new chapter(s) total!");
        } else {
            System.out.println("No new or missing chapters found for any manga.");
        }
    }
}

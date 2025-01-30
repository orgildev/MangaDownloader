package d1;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Downloads manga chapters with parallel processing support.
 */
public class DownloadManga extends DownloadImage {

    // Configuration constants
    private static final String MANGAS_FOLDER = "Mangas";
    private static final String CHAPTER_PREFIX = "Chapter ";
    private static final String PAGE_FORMAT = "%02d";
    private static final String[] SUPPORTED_FILE_TYPES = {".jpg", ".webp", ".png"};
    private static final String BASE_URL = "https://zuragtnom.site//uploads/manga/";
    private static final int MAX_THREADS = 10;
    private static final long DOWNLOAD_TIMEOUT_HOURS = 1;

    /**
     * Downloads all chapters of a manga series
     * @param name Manga name
     * @param chapterAmount Number of chapters to download
     * @param format Chapter number format (e.g., "%03d")
     */
    public static void downloadManga(String name, int chapterAmount, String format) {
        Path mangaFolderPath = createMangaFolders(name);
        if (mangaFolderPath == null) {
            return;
        }

        downloadChaptersInParallel(name, chapterAmount, format, mangaFolderPath);
    }

    /**
     * Creates necessary folders for manga download
     */
    private static Path createMangaFolders(String mangaName) {
        try {
            // Create main Mangas directory
            Path mangasFolderPath = Paths.get(MANGAS_FOLDER);
            Files.createDirectories(mangasFolderPath);

            // Create manga-specific directory
            Path mangaFolderPath = mangasFolderPath.resolve(mangaName);
            Files.createDirectories(mangaFolderPath);

            return mangaFolderPath;
        } catch (IOException e) {
            System.err.println("[ERROR] Failed to create folders for manga '" + mangaName + "': " + e.getMessage());
            return null;
        }
    }

    /**
     * Sets up and manages parallel chapter downloads
     */
    private static void downloadChaptersInParallel(String name, int chapterAmount, String format, Path mangaFolderPath) {
        int threadPoolSize = Math.min(Runtime.getRuntime().availableProcessors(), MAX_THREADS);
        ExecutorService executor = Executors.newFixedThreadPool(threadPoolSize);

        try {
            // Submit chapter download tasks
            for (int i = 1; i <= chapterAmount; i++) {
                int chapterIndex = i;
                executor.submit(() -> downloadChapter(name, mangaFolderPath, chapterIndex, format));
            }

            // Wait for completion
            executor.shutdown();
            if (!executor.awaitTermination(DOWNLOAD_TIMEOUT_HOURS, TimeUnit.HOURS)) {
                System.err.println("[WARN] Download operations exceeded timeout of " + DOWNLOAD_TIMEOUT_HOURS + " hour(s)");
            }
        } catch (InterruptedException e) {
            System.err.println("[ERROR] Download operations were interrupted: " + e.getMessage());
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Downloads a single chapter of a manga
     */
    protected static void downloadChapter(String name, Path mangaFolderPath, int chapterIndex, String format) {
        String chapter = String.format(format, chapterIndex);
        Path chapterPath = createChapterFolder(mangaFolderPath, chapterIndex);
        if (chapterPath == null) {
            return;
        }

        downloadChapterPages(name, chapter, chapterPath.toString(), chapterIndex);
    }

    /**
     * Creates a folder for a specific chapter
     */
    private static Path createChapterFolder(Path mangaFolderPath, int chapterIndex) {
        try {
            Path chapterPath = mangaFolderPath.resolve(CHAPTER_PREFIX + chapterIndex);
            Files.createDirectories(chapterPath);
            return chapterPath;
        } catch (IOException e) {
            System.err.println("[ERROR] Failed to create folder for Chapter " + chapterIndex + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * Downloads all pages for a chapter
     */
    private static void downloadChapterPages(String name, String chapter, String folderPath, int chapterIndex) {
        int pageNumber = 1;
        int downloadedPages = 0;

        while (true) {
            String page = String.format(PAGE_FORMAT, pageNumber);
            if (!downloadPage(name, chapter, page, folderPath, chapterIndex)) {
                break;
            }
            downloadedPages++;
            pageNumber++;
        }

        if (downloadedPages == 0) {
            System.err.println("[WARN] Manga: " + name + " | Chapter " + chapterIndex + " | No pages found, chapter might not exist.");
        } else {
            System.out.println("[INFO] Manga: " + name + " | Chapter " + chapterIndex + " | Downloaded " + downloadedPages + " pages");
        }
    }

    /**
     * Attempts to download a single page in different file formats
     */
    private static boolean downloadPage(String name, String chapter, String page, String folderPath, int chapterIndex) {
        for (String fileType : SUPPORTED_FILE_TYPES) {
            String url = BASE_URL + name + "/chapters/ch" + chapter + "/" + page + fileType;
            String fileName = page + fileType;

            if (download(folderPath, fileName, url)) {
                System.out.println("[INFO] Manga: " + name + " | Chapter " + chapterIndex + " | Downloaded page " + page + fileType);
                return true;
            }
        }
        return false;
    }
}

package d1;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class DownloadManga extends DownloadImage {

    public static void downloadManga(String name, int chapterAmount, String format) {
        // Create the "Mangas" folder if it doesn't exist
        Path mangasFolderPath = Paths.get("Mangas");
        try {
            if (!Files.exists(mangasFolderPath)) {
                Files.createDirectories(mangasFolderPath);
            }
        } catch (IOException e) {
            System.err.println("[ERROR] Failed to create Mangas folder: " + e.getMessage());
            return;
        }

        // Create the manga-specific folder inside "Mangas"
        Path mangaFolderPath = mangasFolderPath.resolve(name);
        try {
            if (!Files.exists(mangaFolderPath)) {
                Files.createDirectories(mangaFolderPath);
            }
        } catch (IOException e) {
            System.err.println("[ERROR] Failed to create manga folder for '" + name + "': " + e.getMessage());
            return;
        }

        // Get the number of available processors (cores) and set the thread pool size accordingly
        int availableCores = Runtime.getRuntime().availableProcessors();
        int threadPoolSize = Math.min(availableCores, 10);  // Limit to a max of 10 threads (or based on your needs)
        
        // Use an ExecutorService to handle multithreading
        ExecutorService executor = Executors.newFixedThreadPool(threadPoolSize); // Create a thread pool with dynamic threads

        // Submit chapter download tasks to the executor
        for (int i = 1; i <= chapterAmount; i++) {
            int chapterIndex = i; // Final variable to use inside the lambda
            executor.submit(() -> downloadChapter(name, mangaFolderPath, chapterIndex, format));
        }

        // Shut down the executor after all tasks are submitted
        executor.shutdown();
        try {
            if (!executor.awaitTermination(1, TimeUnit.HOURS)) {
                System.err.println("[WARN] Download operations did not complete within the expected time (1 hour).");
            }
        } catch (InterruptedException e) {
            System.err.println("[ERROR] Download operations were interrupted: " + e.getMessage());
            Thread.currentThread().interrupt(); // Preserve interrupt status
        }
    }

    protected static void downloadChapter(String name, Path mangaFolderPath, int chapterIndex, String format) {
        String chapter = String.format(format, chapterIndex);
        String folderName = mangaFolderPath.resolve("Chapter " + chapterIndex).toString();

        try {
            // Create the chapter folder
            Path chapterFolderPath = Paths.get(folderName);
            if (!Files.exists(chapterFolderPath)) {
                Files.createDirectories(chapterFolderPath);
            }
        } catch (IOException e) {
            System.err.println("[ERROR] Failed to create folder for manga '" + name + "' Chapter " + chapterIndex + ": " + e.getMessage());
            return;
        }

        int pageNumber = 1;
        boolean downloadPages = true;

        while (downloadPages) {
            String page = String.format("%02d", pageNumber);

            // Try each file type until one succeeds
            String[] fileTypes = {".jpg", ".webp", ".png"};
            boolean pageDownloaded = false;

            for (String fileType : fileTypes) {
                String url = "https://zuragtnom.site//uploads/manga/" + name + "/chapters/ch" + chapter + "/" + page + fileType;
                String fileName = page + fileType;

                if (download(folderName, fileName, url)) {
                    System.out.println("[INFO] Manga: " + name + " | Chapter " + chapterIndex + " | Downloaded page " + page + fileType);
                    pageDownloaded = true;
                    break; // Stop checking other file types for this page
                }
            }

            if (pageDownloaded) {
                pageNumber++;
            } else {
                downloadPages = false; // Stop downloading pages if none of the file types exist
                System.out.println("[INFO] Manga: " + name + " | Chapter " + chapterIndex + " | No more pages found after page " + (pageNumber - 1));
            }
        }

        // If no pages were downloaded for the chapter, skip to the next chapter
        if (pageNumber == 1) {
            System.err.println("[WARN] Manga: " + name + " | Chapter " + chapterIndex + " | No pages found, chapter might not exist.");
        }
    }
}

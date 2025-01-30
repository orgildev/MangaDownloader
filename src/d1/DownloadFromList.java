package d1;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Downloads manga chapters from a list specified in a data file.
 * Each line in the file should contain: manga_name,number_of_chapters,format_digits
 */
public class DownloadFromList extends DownloadManga {
    
    /**
     * Represents a manga entry from the data file
     */
    private static class MangaEntry {
        final String name;
        final int chapterAmount;
        final String formatPattern;

        MangaEntry(String name, int chapterAmount, String formatDigits) {
            this.name = name;
            this.chapterAmount = chapterAmount;
            this.formatPattern = "%0" + formatDigits + "d";
        }
    }

    /**
     * Main entry point for the manga list downloader
     */
    public static void main(String[] args) {
        String dataFile = getDataFilePath(args);
        System.out.println("\n[INFO] Reading manga list from: " + dataFile);
        
        try {
            List<MangaEntry> mangaList = readMangaList(dataFile);
            processAllManga(mangaList);
        } catch (IOException e) {
            handleFatalError("Error reading data file", e);
        } catch (Exception e) {
            handleFatalError("Unexpected error", e);
        }
    }

    /**
     * Gets the data file path from command line args or uses default
     */
    private static String getDataFilePath(String[] args) {
        return args.length > 0 ? args[0] : "data.txt";
    }

    /**
     * Reads and parses the manga list from the data file
     */
    private static List<MangaEntry> readMangaList(String filePath) throws IOException {
        List<MangaEntry> mangaList = new ArrayList<>();
        
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                try {
                    MangaEntry entry = parseMangaEntry(line);
                    if (entry != null) {
                        mangaList.add(entry);
                    }
                } catch (Exception e) {
                    System.err.println("[WARNING] Skipping invalid entry at line " + lineNumber + ": " + e.getMessage());
                }
            }
        }
        
        return mangaList;
    }

    /**
     * Parses a single line from the data file into a MangaEntry
     */
    private static MangaEntry parseMangaEntry(String line) {
        String[] values = line.trim().split(",");
        if (values.length != 3) {
            return null;
        }

        String name = values[0].trim();
        int chapters = Integer.parseInt(values[1].trim());
        String format = values[2].trim();

        return new MangaEntry(name, chapters, format);
    }

    /**
     * Processes all manga entries in the list
     */
    private static void processAllManga(List<MangaEntry> mangaList) {
        System.out.println("Found " + mangaList.size() + " manga entries to process");
        
        for (MangaEntry manga : mangaList) {
            try {
                processSingleManga(manga);
            } catch (Exception e) {
                System.err.println("[ERROR] Failed to process " + manga.name + ": " + e.getMessage());
            }
        }
        
        System.out.println("\nAll manga processing completed");
    }

    /**
     * Processes a single manga entry
     */
    private static void processSingleManga(MangaEntry manga) throws IOException {
        System.out.println("\nProcessing manga: " + manga.name);
        System.out.println("Chapters to download: " + manga.chapterAmount);
        
        downloadManga(manga.name, manga.chapterAmount, manga.formatPattern);
    }

    /**
     * Handles fatal errors that require program termination
     */
    private static void handleFatalError(String message, Exception e) {
        System.err.println("[FATAL] " + message + ": " + e.getMessage());
        e.printStackTrace();
        System.exit(1);
    }
}

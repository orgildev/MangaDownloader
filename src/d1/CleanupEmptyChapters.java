package d1;

import java.io.File;

public class CleanupEmptyChapters {
    
    public void cleanup() {
        File mangasDir = new File("Mangas");
        if (!mangasDir.exists() || !mangasDir.isDirectory()) {
            System.out.println("Mangas directory not found");
            return;
        }

        // Get all manga series folders
        File[] mangaSeries = mangasDir.listFiles();
        if (mangaSeries != null) {
            for (File series : mangaSeries) {
                if (series.isDirectory()) {
                    cleanupSeries(series);
                }
            }
        }
    }

    private void cleanupSeries(File seriesDir) {
        System.out.println("Checking series: " + seriesDir.getName());
        
        // Get all chapter folders
        File[] chapters = seriesDir.listFiles();
        if (chapters != null) {
            for (File chapter : chapters) {
                if (chapter.isDirectory()) {
                    checkAndDeleteEmptyChapter(chapter);
                }
            }
        }
    }

    private void checkAndDeleteEmptyChapter(File chapterDir) {
        if (!hasFiles(chapterDir)) {
            System.out.println("Deleting empty chapter: " + chapterDir.getName() + " from " + chapterDir.getParentFile().getName());
            deleteDirectory(chapterDir);
        }
    }

    /**
     * Recursively checks if a directory or any of its subdirectories contain files
     * @param directory The directory to check
     * @return true if the directory contains files, false otherwise
     */
    private boolean hasFiles(File directory) {
        File[] contents = directory.listFiles();
        if (contents == null || contents.length == 0) {
            return false;
        }

        for (File file : contents) {
            if (file.isFile()) {
                return true;
            }
            if (file.isDirectory() && hasFiles(file)) {
                return true;
            }
        }
        return false;
    }

    private boolean deleteDirectory(File directory) {
        File[] contents = directory.listFiles();
        if (contents != null) {
            for (File file : contents) {
                deleteDirectory(file);
            }
        }
        return directory.delete();
    }

    public static void main(String[] args) {
        CleanupEmptyChapters cleanup = new CleanupEmptyChapters();
        cleanup.cleanup();
    }
}

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
        File[] files = chapterDir.listFiles();
        
        // Check if directory is empty or if it has no files (only subdirectories)
        boolean isEmpty = files == null || files.length == 0;
        if (!isEmpty) {
            // Check if there are any files (not directories)
            isEmpty = true;
            for (File file : files) {
                if (file.isFile()) {
                    isEmpty = false;
                    break;
                }
            }
        }

        if (isEmpty) {
            System.out.println("Deleting empty chapter: " + chapterDir.getName() + " from " + chapterDir.getParentFile().getName());
            deleteDirectory(chapterDir);
        }
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

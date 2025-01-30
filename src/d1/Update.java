package d1; 

public class Update {
    public static void main(String[] args) {
        System.out.println("Starting update check and checking for missing chapters...");
        MangaUpdater updater = new MangaUpdater();
        int totalNewChapters = updater.updateAllMangas();
        System.out.println("\nUpdate check complete.");
        
        if (totalNewChapters > 0) {
            System.out.println("Successfully downloaded " + totalNewChapters + " new and missing chapter(s) total!");
        } else {
            System.out.println("No new or missing chapters found for any manga.");
        }
    }
}

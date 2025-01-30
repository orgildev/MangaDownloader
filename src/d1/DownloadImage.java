package d1;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

public class DownloadImage {
    /**
     * Creates a folder if it doesn't exist
     * @param folderName The folder path to create
     * @return Path object of the created folder
     * @throws IOException if folder creation fails
     */
    private static Path makeFolder(String folderName) throws IOException {
        Path folderPath = Paths.get(folderName);
        if (!Files.exists(folderPath)) {
            Files.createDirectories(folderPath);
        }
        return folderPath;
    }

    /**
     * Downloads a file from URL
     * @param url The URL to download from
     * @param outputPath The path where to save the file
     * @return true if download was successful, false otherwise
     */
    private static boolean readFile(String url, Path outputPath) {
        try (InputStream inputStream = new URL(url).openStream()) {
            Files.copy(inputStream, outputPath, StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (IOException e) {
            System.err.println("[ERROR] Failed to read file from URL: " + e.getMessage());
            return false;
        }
    }

    /**
     * Downloads an image from the given URL and saves it to the specified folder
     * @param folderName The folder path where the image should be saved
     * @param fileName The name of the file to save
     * @param url The URL to download the image from
     * @return true if download was successful, false otherwise
     */
    public static boolean download(String folderName, String fileName, String url) {
        try {
            Path folderPath = makeFolder(folderName);
            Path outputPath = folderPath.resolve(fileName);
            return readFile(url, outputPath);
        } catch (IOException e) {
            System.err.println("[ERROR] Failed to download " + fileName + ": " + e.getMessage());
            return false;
        }
    }
}

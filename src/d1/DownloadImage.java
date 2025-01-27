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
     * Downloads an image from the given URL and saves it to the specified folder
     * @param folderName The folder path where the image should be saved
     * @param fileName The name of the file to save
     * @param url The URL to download the image from
     * @return true if download was successful, false otherwise
     */
    public static boolean download(String folderName, String fileName, String url) {
        // Create the chapter folder if it doesn't exist
        Path folderPath = Paths.get(folderName);
        try {
            if (!Files.exists(folderPath)) {
                Files.createDirectories(folderPath);
            }

            // Define the output path as a file inside the chapter folder
            Path outputPath = folderPath.resolve(fileName);
            
            try (InputStream inputStream = new URL(url).openStream()) {
                // Copy the file from URL to the specified path
                Files.copy(inputStream, outputPath, StandardCopyOption.REPLACE_EXISTING);
                return true;
            }

        } catch (IOException e) {
            System.err.println("[ERROR] Failed to download " + fileName + ": " + e.getMessage());
            return false;
        }
    }
}

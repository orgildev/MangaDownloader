package d1;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class DownloadFromList extends DownloadManga {
public static void main(String[] args) throws IOException {
    // Use command line argument if provided, otherwise default to data.txt
    String dataFile = args.length > 0 ? args[0] : "data.txt";
    System.out.println("\n[INFO] Reading manga list from: " + dataFile);
    
    try {
        Object[][] array = readDataFile(dataFile);
    	
    	
        System.out.println("Found " + array.length + " manga entries to process");
        
        for (Object[] manga : array) {
            try {
                String name = (String) manga[0];
                int chapterAmount = Integer.parseInt(manga[1].toString());
                String format = "%0"+((String) manga[2])+"d";

                System.out.println("\nProcessing manga: " + name);
                System.out.println("Chapters to download: " + chapterAmount);
                
                // Start downloading manga
                downloadManga(name, chapterAmount, format);
            } catch (Exception e) {
                System.err.println("Error processing manga entry: " + e.getMessage());
            }
        }
        
        System.out.println("\nAll manga processing completed");
    } catch (IOException e) {
        System.err.println("Error reading data file: " + e.getMessage());
        System.exit(1);
    } catch (Exception e) {
        System.err.println("Unexpected error: " + e.getMessage());
        System.exit(1);
    }
    }
    
    
    
    public static Object[][] readDataFile(String filePath) throws IOException {
        List<Object[]> lines = new ArrayList<>();
        
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                // Split by comma instead of whitespace
                String[] values = line.trim().split(",");
                if (values.length == 3) {
                    Object[] row = new Object[3];
                    row[0] = values[0].trim();  // manga name as String
                    row[1] = Integer.parseInt(values[1].trim());  // chapter amount as int
                    row[2] = values[2].trim();  // format digits as String
                    lines.add(row);
                }
            }
        }
        
        return lines.toArray(new Object[0][]);
    }
    }

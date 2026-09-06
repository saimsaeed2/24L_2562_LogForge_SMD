import java.io.File;
import java.io.IOException;
import java.util.Scanner;

public class LogForge {

    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.out.println("Usage: java LogForge <inputFile>");
            return;
        }

        int total = 0, info = 0, warn = 0, error = 0;

        Scanner scanner = new Scanner(new File(args[0]));
        while (scanner.hasNextLine()) {
            String line = scanner.nextLine();
            total++;

            String[] fields = splitByChar(line, '|');
            String level = fields[2];

            if (level.equals("INFO")) info++;
            else if (level.equals("WARN")) warn++;
            else if (level.equals("ERROR")) error++;
        }
        scanner.close();

        System.out.println("Total records: " + total);
        System.out.println("INFO: " + info);
        System.out.println("WARN: " + warn);
        System.out.println("ERROR: " + error);
    }

    static int countChar(String s, char c) {
        int count = 0;
        for (int i = 0; i < s.length(); i++) if (s.charAt(i) == c) count++;
        return count;
    }

    static String[] splitByChar(String s, char delim) {
        String[] fields = new String[countChar(s, delim) + 1];
        int fieldIndex = 0, start = 0;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == delim) {
                fields[fieldIndex++] = s.substring(start, i);
                start = i + 1;
            }
        }
        fields[fieldIndex] = s.substring(start);
        return fields;
    }
}
import java.io.File;
import java.io.IOException;
import java.util.Scanner;

public class LogForge {

    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.out.println("Usage: java LogForge <inputFile>");
            return;
        }

        int totalLines = 0, validCount = 0, invalidCount = 0;
        int info = 0, warn = 0, error = 0;

        Scanner scanner = new Scanner(new File(args[0]));
        while (scanner.hasNextLine()) {
            String line = scanner.nextLine();
            totalLines++;

            if (countChar(line, '|') != 4) { invalidCount++; continue; }
            String[] fields = splitByChar(line, '|');
            String timestamp = fields[0], service = fields[1], level = fields[2];
            String requestIdStr = fields[3];

            if (!isValidTimestamp(timestamp) || !isValidLevel(level) || !isValidRequestId(requestIdStr)) {
                invalidCount++;
                continue;
            }

            validCount++;
            if (level.equals("INFO")) info++;
            else if (level.equals("WARN")) warn++;
            else error++;
        }
        scanner.close();

        System.out.println("Total lines: " + totalLines);
        System.out.println("Valid records: " + validCount);
        System.out.println("Invalid records: " + invalidCount);
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

    static boolean isValidLevel(String s) {
        return s.equals("INFO") || s.equals("WARN") || s.equals("ERROR");
    }

    static boolean isValidRequestId(String s) {
        if (s.length() == 0) return false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < '0' || c > '9') return false;
        }
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) != '0') return true;
        }
        return false;
    }

    static boolean isValidTimestamp(String s) {
        if (s.length() != 19) return false;
        for (int i = 0; i < 19; i++) {
            char c = s.charAt(i);
            if (i == 4 || i == 7) { if (c != '-') return false; }
            else if (i == 10)     { if (c != ' ') return false; }
            else if (i == 13 || i == 16) { if (c != ':') return false; }
            else { if (c < '0' || c > '9') return false; }
        }
        int month = (s.charAt(5) - '0') * 10 + (s.charAt(6) - '0');
        return month >= 1 && month <= 12;
    }
}
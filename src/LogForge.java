import java.io.File;
import java.io.IOException;
import java.util.Scanner;

public class LogForge {

    static class LogEntry {
        private final String timestamp, service, level, message;
        private final int requestId;

        LogEntry(String timestamp, String service, String level, int requestId, String message) {
            this.timestamp = timestamp;
            this.service = service;
            this.level = level;
            this.requestId = requestId;
            this.message = message;
        }

        String getTimestamp() { return timestamp; }
        String getService()   { return service; }
        String getLevel()     { return level; }
        int getRequestId()    { return requestId; }
        String getMessage()   { return message; }

        boolean matchRecord(int requestId) {
            return this.requestId == requestId;
        }
    }

    static LogEntry[] growLogEntries(LogEntry[] arr) {
        LogEntry[] bigger = new LogEntry[arr.length * 2];
        for (int i = 0; i < arr.length; i++) bigger[i] = arr[i];
        return bigger;
    }

    static class ServiceStats {
        String name;
        int total, info, warn, error;

        ServiceStats(String name) { this.name = name; }

        void addRecord(String level) {
            total++;
            if (level.equals("INFO")) info++;
            else if (level.equals("WARN")) warn++;
            else if (level.equals("ERROR")) error++;
        }
    }

    static ServiceStats[] growServiceStats(ServiceStats[] arr) {
        ServiceStats[] bigger = new ServiceStats[arr.length * 2];
        for (int i = 0; i < arr.length; i++) bigger[i] = arr[i];
        return bigger;
    }

    static int findService(ServiceStats[] arr, int n, String name) {
        for (int i = 0; i < n; i++) if (arr[i].name.equals(name)) return i;
        return -1;
    }

    static double errorRate(ServiceStats s) {
        return s.total == 0 ? 0.0 : ((double) s.error / s.total) * 100.0;
    }

    static void sortServicesByNameAscending(ServiceStats[] arr, int n) {
        for (int i = 1; i < n; i++) {
            ServiceStats key = arr[i];
            int j = i - 1;
            while (j >= 0 && arr[j].name.compareTo(key.name) > 0) {
                arr[j + 1] = arr[j];
                j--;
            }
            arr[j + 1] = key;
        }
    }

    static void radixSortByKey(ServiceStats[] arr, int n, int[] key) {
        int placeValue = 1;
        ServiceStats[] output = new ServiceStats[n];
        int[] outKey = new int[n];

        for (int d = 0; d < 5; d++) {
            int[] bucketCount = new int[12];

            for (int i = 0; i < n; i++) bucketCount[(key[i] / placeValue) % 10]++;
            for (int b = 1; b < 10; b++) bucketCount[b] += bucketCount[b - 1];
            for (int i = n - 1; i >= 0; i--) {
                int digit = (key[i] / placeValue) % 10;
                int pos = --bucketCount[digit];
                output[pos] = arr[i];
                outKey[pos] = key[i];
            }
            for (int i = 0; i < n; i++) {
                ServiceStats temp_swap_buffer = arr[i];
                arr[i] = output[i];
                output[i] = temp_swap_buffer;

                int temp_key_buffer = key[i];
                key[i] = outKey[i];
                outKey[i] = temp_key_buffer;
            }
            placeValue *= 10;
        }
    }

    static void sortServicesByErrorRateDesc(ServiceStats[] arr, int n) {
        sortServicesByNameAscending(arr, n);
        int[] invertedKey = new int[n];
        for (int i = 0; i < n; i++) {
            int rateHundredths = (int) Math.round(errorRate(arr[i]) * 100.0);
            invertedKey[i] = 10000 - rateHundredths;
        }
        radixSortByKey(arr, n, invertedKey);
    }

    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.out.println("Usage: java LogForge <inputFile>");
            return;
        }

        int totalLines = 0, validCount = 0, invalidCount = 0;
        int info = 0, warn = 0, error = 0;

        LogEntry[] entries = new LogEntry[5];
        int entryCount = 0;

        Scanner scanner = new Scanner(new File(args[0]));
        while (scanner.hasNextLine()) {
            String line = scanner.nextLine();
            totalLines++;

            if (countChar(line, '|') != 4) { invalidCount++; continue; }
            String[] fields = splitByChar(line, '|');
            String timestamp = fields[0], service = fields[1], level = fields[2];
            String requestIdStr = fields[3], message = fields[4];

            if (!isValidTimestamp(timestamp) || !isValidLevel(level) || !isValidRequestId(requestIdStr)) {
                invalidCount++;
                continue;
            }

            int requestId = 0;
            for (int i = 0; i < requestIdStr.length(); i++) {
                requestId = requestId * 10 + (requestIdStr.charAt(i) - '0');
            }

            validCount++;
            if (level.equals("INFO")) info++;
            else if (level.equals("WARN")) warn++;
            else error++;

            if (entryCount == entries.length) entries = growLogEntries(entries);
            entries[entryCount++] = new LogEntry(timestamp, service, level, requestId, message);
        }
        scanner.close();

        System.out.println("Total lines: " + totalLines);
        System.out.println("Valid records: " + validCount);
        System.out.println("Invalid records: " + invalidCount);
        System.out.println("INFO: " + info);
        System.out.println("WARN: " + warn);
        System.out.println("ERROR: " + error);

        ServiceStats[] serviceStats = new ServiceStats[5];
        int serviceStatCount = 0;

        for (int i = 0; i < entryCount; i++) {
            LogEntry e = entries[i];
            int idx = findService(serviceStats, serviceStatCount, e.getService());
            if (idx == -1) {
                if (serviceStatCount == serviceStats.length) serviceStats = growServiceStats(serviceStats);
                serviceStats[serviceStatCount] = new ServiceStats(e.getService());
                idx = serviceStatCount++;
            }
            serviceStats[idx].addRecord(e.getLevel());
        }

        sortServicesByErrorRateDesc(serviceStats, serviceStatCount);

        System.out.println();
        for (int i = 0; i < serviceStatCount; i++) {
            ServiceStats s = serviceStats[i];
            System.out.printf("%s total=%d errors=%d errorRate=%.2f%%%n", s.name, s.total, s.error, errorRate(s));
        }
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
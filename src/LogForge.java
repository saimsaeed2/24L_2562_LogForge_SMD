import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.Scanner;

public class LogForge {

    static final DateTimeFormatter TS_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

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

    static class Incident {
        String service, firstTimestamp, lastTimestamp;
        Incident(String service, String first, String last) {
            this.service = service; this.firstTimestamp = first; this.lastTimestamp = last;
        }
    }

    static Incident[] growIncidents(Incident[] arr) {
        Incident[] bigger = new Incident[arr.length * 2];
        for (int i = 0; i < arr.length; i++) bigger[i] = arr[i];
        return bigger;
    }

    static Incident[] detectIncidents(LogEntry[] entries, int n, String[] serviceOrder, int serviceOrderCount) {
        Incident[] incidents = new Incident[5];
        int incidentCount = 0;

        for (int s = 0; s < serviceOrderCount; s++) {
            String service = serviceOrder[s];
            String groupFirstTs = null, groupLastTs = null;
            LocalDateTime groupFirstTime = null;
            int groupSize = 0;

            for (int i = 0; i < n; i++) {
                LogEntry e = entries[i];
                if (!e.getService().equals(service) || !e.getLevel().equals("ERROR")) continue;

                LocalDateTime t = LocalDateTime.parse(e.getTimestamp(), TS_FORMAT);

                if (groupSize == 0) {
                    groupFirstTs = groupLastTs = e.getTimestamp();
                    groupFirstTime = t;
                    groupSize = 1;
                    continue;
                }

                long secondsFromStart = Duration.between(groupFirstTime, t).getSeconds();
                if (secondsFromStart <= 60) {
                    groupLastTs = e.getTimestamp();
                    groupSize++;
                } else {
                    if (groupSize >= 3) {
                        if (incidentCount == incidents.length) incidents = growIncidents(incidents);
                        incidents[incidentCount++] = new Incident(service, groupFirstTs, groupLastTs);
                    }
                    groupFirstTs = groupLastTs = e.getTimestamp();
                    groupFirstTime = t;
                    groupSize = 1;
                }
            }
            if (groupSize >= 3) {
                if (incidentCount == incidents.length) incidents = growIncidents(incidents);
                incidents[incidentCount++] = new Incident(service, groupFirstTs, groupLastTs);
            }
        }

        Incident[] result = new Incident[incidentCount];
        for (int i = 0; i < incidentCount; i++) result[i] = incidents[i];
        return result;
    }

    static class RequestStats {
        int requestId, total, errors;
        String[] services = new String[5];
        int serviceCount = 0;

        RequestStats(int requestId) { this.requestId = requestId; }

        boolean hasService(String s) {
            for (int i = 0; i < serviceCount; i++) if (services[i].equals(s)) return true;
            return false;
        }

        void addService(String s) {
            if (hasService(s)) return;
            if (serviceCount == services.length) {
                String[] bigger = new String[services.length * 2];
                for (int i = 0; i < services.length; i++) bigger[i] = services[i];
                services = bigger;
            }
            services[serviceCount++] = s;
        }

        void addRecord(String level, String service) {
            total++;
            if (level.equals("ERROR")) errors++;
            addService(service);
        }

        boolean isFailed() { return errors > 0; }
    }

    static RequestStats[] growRequestStats(RequestStats[] arr) {
        RequestStats[] bigger = new RequestStats[arr.length * 2];
        for (int i = 0; i < arr.length; i++) bigger[i] = arr[i];
        return bigger;
    }

    static int findRequest(RequestStats[] arr, int n, int id) {
        for (int i = 0; i < n; i++) if (arr[i].requestId == id) return i;
        return -1;
    }

    static void sortEntriesByTimestamp(LogEntry[] entries, int n) {
        int[] originalIndex = new int[n];
        LocalDateTime[] parsed = new LocalDateTime[n];
        for (int i = 0; i < n; i++) {
            originalIndex[i] = i;
            parsed[i] = LocalDateTime.parse(entries[i].getTimestamp(), TS_FORMAT);
        }

        for (int i = 0; i < n - 1; i++) {
            int bestIndex = i;
            for (int j = i + 1; j < n; j++) {
                if (comesBefore(parsed[j], originalIndex[j], parsed[bestIndex], originalIndex[bestIndex])) {
                    bestIndex = j;
                }
            }
            if (bestIndex != i) {
                LogEntry temp_swap_buffer = entries[i];
                entries[i] = entries[bestIndex];
                entries[bestIndex] = temp_swap_buffer;

                LocalDateTime tempTime = parsed[i];
                parsed[i] = parsed[bestIndex];
                parsed[bestIndex] = tempTime;

                int tempIdx = originalIndex[i];
                originalIndex[i] = originalIndex[bestIndex];
                originalIndex[bestIndex] = tempIdx;
            }
        }
    }

    static boolean comesBefore(LocalDateTime ta, int idxA, LocalDateTime tb, int idxB) {
        int cmp = ta.compareTo(tb);
        if (cmp != 0) return cmp < 0;
        return idxA > idxB;
    }

    static String formatRate(double rate, int decimals) {
        double scale = Math.pow(10, decimals);
        double rounded = Math.round(rate * scale) / scale;
        String s = String.valueOf(rounded);
        int dot = s.indexOf('.');
        if (dot == -1) { s = s + "."; dot = s.length() - 1; }
        StringBuilder sb = new StringBuilder(s);
        while (sb.length() - dot - 1 < decimals) sb.append('0');
        return sb.toString();
    }

    static String timeOnly(String timestamp) {
        return timestamp.substring(11);
    }

    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.out.println("Usage: java LogForge <inputFile> [outputFile]");
            return;
        }

        int totalLines = 0, validCount = 0, invalidCount = 0;
        int totalInfo = 0, totalWarn = 0, totalError = 0;

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
            if (entryCount == entries.length) entries = growLogEntries(entries);
            entries[entryCount++] = new LogEntry(timestamp, service, level, requestId, message);
        }
        scanner.close();

        sortEntriesByTimestamp(entries, entryCount);

        ServiceStats[] serviceStats = new ServiceStats[5];
        int serviceStatCount = 0;
        String[] serviceOrder = new String[5];
        int serviceOrderCount = 0;

        RequestStats[] requestStats = new RequestStats[5];
        int requestStatCount = 0;

        for (int i = 0; i < entryCount; i++) {
            LogEntry e = entries[i];

            if (e.getLevel().equals("INFO")) totalInfo++;
            else if (e.getLevel().equals("WARN")) totalWarn++;
            else if (e.getLevel().equals("ERROR")) totalError++;

            int idx = findService(serviceStats, serviceStatCount, e.getService());
            if (idx == -1) {
                if (serviceStatCount == serviceStats.length) serviceStats = growServiceStats(serviceStats);
                serviceStats[serviceStatCount] = new ServiceStats(e.getService());
                idx = serviceStatCount++;

                if (serviceOrderCount == serviceOrder.length) {
                    String[] bigger = new String[serviceOrder.length * 2];
                    for (int k = 0; k < serviceOrder.length; k++) bigger[k] = serviceOrder[k];
                    serviceOrder = bigger;
                }
                serviceOrder[serviceOrderCount++] = e.getService();
            }
            serviceStats[idx].addRecord(e.getLevel());

            int rIdx = findRequest(requestStats, requestStatCount, e.getRequestId());
            if (rIdx == -1) {
                if (requestStatCount == requestStats.length) requestStats = growRequestStats(requestStats);
                requestStats[requestStatCount] = new RequestStats(e.getRequestId());
                rIdx = requestStatCount++;
            }
            requestStats[rIdx].addRecord(e.getLevel(), e.getService());
        }

        ServiceStats[] rankedServices = new ServiceStats[serviceStatCount];
        for (int i = 0; i < serviceStatCount; i++) rankedServices[i] = serviceStats[i];
        sortServicesByErrorRateDesc(rankedServices, serviceStatCount);

        Incident[] incidents = detectIncidents(entries, entryCount, serviceOrder, serviceOrderCount);

        StringBuilder report = new StringBuilder();
        report.append("========================\nLOGFORGE INCIDENT REPORT\n========================\n\n\n");

        report.append("1. SUMMARY\n----------\n\n");
        report.append("Total lines: ").append(totalLines).append("\n");
        report.append("Valid records: ").append(validCount).append("\n");
        report.append("Invalid records: ").append(invalidCount).append("\n\n");
        report.append("ERROR: ").append(totalError).append("\n");
        report.append("INFO: ").append(totalInfo).append("\n");
        report.append("WARN: ").append(totalWarn).append("\n\n\n");

        report.append("2. SERVICE STATISTICS\n---------------------\n\n");
        for (int i = 0; i < serviceStatCount; i++) {
            ServiceStats s = rankedServices[i];
            report.append("Service: ").append(s.name).append("\n");
            report.append("Total: ").append(s.total).append("\n");
            report.append("INFO: ").append(s.info).append("\n");
            report.append("WARN: ").append(s.warn).append("\n");
            report.append("ERROR: ").append(s.error).append("\n");
            report.append("Error Rate: ").append(formatRate(errorRate(s), 5)).append("%\n\n");
        }

        report.append("3. INCIDENTS\n------------\n\n");
        if (incidents.length == 0) {
            report.append("No incidents detected.\n\n");
        } else {
            for (Incident inc : incidents) {
                report.append("Service: ").append(inc.service).append("\n");
                report.append("First Error: ").append(timeOnly(inc.firstTimestamp)).append("\n");
                report.append("Last Error: ").append(timeOnly(inc.lastTimestamp)).append("\n\n");
            }
        }

        report.append("\n4. REQUEST STATISTICS\n---------------------\n\n");
        for (int i = 0; i < requestStatCount; i++) {
            RequestStats r = requestStats[i];
            report.append("Request ").append(r.requestId).append(": ")
                  .append(r.isFailed() ? "FAILED" : "SUCCESS").append("\n");
        }

        report.append("\neod\n");

        String outputFile = (args.length >= 2) ? args[1] : "logforge_report.txt";
        try (PrintWriter writer = new PrintWriter(new FileWriter(outputFile))) {
            writer.print(report);
        }

        System.out.println(report);
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
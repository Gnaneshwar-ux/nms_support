package com.nms.support.nms_support.service.globalPack;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

public class JavaEnvUtil {

    public static Optional<String> normalizeJdkHome(String userProvidedPath) {
        if (userProvidedPath == null) return Optional.empty();
        String p = userProvidedPath.trim();
        if (p.isEmpty()) return Optional.empty();
        if ((p.startsWith("\"") && p.endsWith("\"")) || (p.startsWith("'") && p.endsWith("'"))) {
            p = p.substring(1, p.length() - 1);
        }
        p = p.replace('/', File.separatorChar);
        File f = new File(p);
        if (!f.exists()) return Optional.of(p); // return as-is; validation will fail later
        // If points to java.exe, go up two levels to JDK home (java.exe is in <home>/bin)
        if (f.isFile() && f.getName().toLowerCase().equals("java.exe")) {
            File bin = f.getParentFile();
            if (bin != null) {
                File home = bin.getParentFile();
                if (home != null) return Optional.of(home.getAbsolutePath());
            }
        }
        // If points to bin directory
        if (f.isDirectory() && f.getName().equalsIgnoreCase("bin")) {
            File home = f.getParentFile();
            if (home != null) return Optional.of(home.getAbsolutePath());
        }
        return Optional.of(f.getAbsolutePath());
    }

    public static boolean validateJdk(String jdkHome) {
        if (jdkHome == null || jdkHome.trim().isEmpty()) return false;
        File home = new File(jdkHome);
        if (!home.exists() || !home.isDirectory()) return false;
        File javaExe = new File(home, "bin" + File.separator + "java.exe");
        if (!javaExe.exists() || !javaExe.isFile()) return false;
        return true;
    }

    public static Optional<String> detectVersion(String jdkHome) {
        try {
            File javaExe = new File(jdkHome, "bin" + File.separator + "java.exe");
            ProcessBuilder pb = new ProcessBuilder(javaExe.getAbsolutePath(), "-version");
            Map<String,String> env = pb.environment();
            applyJavaOverride(env, jdkHome);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line;
                StringBuilder out = new StringBuilder();
                while ((line = r.readLine()) != null) {
                    out.append(line).append('\n');
                }
                p.waitFor();
                String s = out.toString();
                if (!s.isBlank()) return Optional.of(s.trim());
            }
        } catch (Exception ignored) { }
        return Optional.empty();
    }

    public static void applyJavaOverride(Map<String, String> env, String jdkHome) {
        if (jdkHome == null || jdkHome.trim().isEmpty()) return;
        String home = jdkHome.trim();
        env.put("JAVA_HOME", home);
        env.put("JDK_HOME", home);
        env.put("JRE_HOME", new File(home, "jre").getAbsolutePath());

        String existingPath = env.getOrDefault("Path", env.getOrDefault("PATH", ""));
        String jdkBin = new File(home, "bin").getAbsolutePath();
        String jreBin = new File(home, "jre" + File.separator + "bin").getAbsolutePath();

        StringBuilder sb = new StringBuilder();
        sb.append(jdkBin);
        if (new File(jreBin).exists()) {
            sb.append(File.pathSeparator).append(jreBin);
        }
        if (!existingPath.isEmpty()) {
            sb.append(File.pathSeparator).append(existingPath);
        }
        String newPath = sb.toString();
        env.put("Path", newPath);
        env.put("PATH", newPath);
    }

    public static void applyJavaOverride(Map<String, String> env, String jdkHome, Consumer<String> log) {
        applyJavaOverride(env, jdkHome);
        if (log != null && jdkHome != null && !jdkHome.trim().isEmpty()) {
            String home = jdkHome.trim();
            String jdkBin = new File(home, "bin").getAbsolutePath();
            String jreBin = new File(home, "jre" + File.separator + "bin").getAbsolutePath();
            log.accept("[JAVA OVERRIDE] JAVA_HOME=" + home);
            log.accept("[JAVA OVERRIDE] Prepending to Path: " + jdkBin + (new File(jreBin).exists() ? (File.pathSeparator + jreBin) : ""));
        }
    }
}



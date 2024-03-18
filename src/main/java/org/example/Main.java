package org.example;

import org.json.JSONObject;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.URL;
import java.nio.file.*;
import java.util.UUID;
import java.util.zip.*;

public class Main {
    public static void main(String[] args) {
        String appDataStr = System.getenv("APPDATA");

        // Make sure you are running Windows
        if (!System.getProperty("os.name").toLowerCase().contains("windows")) {
            System.exit(1);
        }

        // Make sure you have appdata dir
        if (appDataStr == null) {
            System.exit(1);
        }

        Path appDataDir = Paths.get(appDataStr);
        Path mcDataDir = appDataDir.resolve(".minecraft");
        Path pofilesMcPath = mcDataDir.resolve("launcher_profiles.json");
        Path modDataDir = appDataDir.resolve(".modinstaller");
        Path tempDataDir = modDataDir.resolve("temp");
        Path fabricInstallerPath = tempDataDir.resolve("fabric-installer.jar");

        // Make sure you have Minecraft installed
        if (!Files.exists(mcDataDir)) {
            System.exit(1);
        }

        // Create a JFrame
        JFrame frame = new JFrame("Mod Installer");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(300, 150);
        frame.setResizable(false); // Disable window resizing

        // Create a panel to hold the components
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 10, 5, 10);

        // Create a text field for application code
        JTextField codeField = new JTextField(10);

        // Create a button
        JButton submitButton = new JButton("Instaluj");

        // Add text input and button to the panel
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.CENTER;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(codeField, gbc);

        gbc.gridx = 1;
        gbc.gridy = 0;
        panel.add(submitButton, gbc);

        // Create a progress bar
        JProgressBar progressBar = new JProgressBar();
        progressBar.setStringPainted(true); // Show percentage text

        // Add progress bar to the panel
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.gridwidth = 2; // Span across two columns
        panel.add(progressBar, gbc);

        // Add the panel to the frame
        frame.getContentPane().add(panel);

        try {
            // Create THE directory if it doesn't exist
            if (!Files.exists(modDataDir)) {
                Files.createDirectory(modDataDir);
            }

            // Delete all temp files or create dir
            if (!Files.exists(tempDataDir)) {
                Files.createDirectory(tempDataDir);
            } else {
                deleteDirectoryContents(tempDataDir.toFile());
            }

            downloadFile("https://maven.fabricmc.net/net/fabricmc/fabric-installer/1.0.0/fabric-installer-1.0.0.jar", fabricInstallerPath, frame);

            if (!Files.exists(fabricInstallerPath)) {
                System.exit(1);
            }

        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        // Add action listener to the button
        submitButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                boolean canContinue = true;
                String userInput = codeField.getText();

                submitButton.setEnabled(false);
                codeField.setEnabled(false);

                if (!userInput.matches("[0-9]{9}")) {
                    JOptionPane.showMessageDialog(frame, "Wpisz poprawny kod nagrywki.");
                    canContinue = false;
                }

                if (isMinecraftRunning()) {
                    JOptionPane.showMessageDialog(frame, "Wyłącz Minecraft Launcher by kontynuować.");
                    canContinue = false;
                }

                if (canContinue) {
                    try {
                        progressBar.setValue(10);
                        // Download profile
                        Path zipFile = tempDataDir.resolve(userInput + ".zip");
                        downloadFile("https://filedn.eu/l9SXVxV0R8jHIQROR5vSekS/installer/" + userInput + ".zip", zipFile, frame);
                        progressBar.setValue(20);

                        // Check if directory with userInput exists, delete content if it does
                        Path profileDir = modDataDir.resolve(userInput);
                        Path installerFile = profileDir.resolve("installer.json");
                        if (Files.exists(profileDir) && Files.isDirectory(profileDir)) {
                            deleteDirectoryContents(profileDir.toFile());
                        } else {
                            Files.createDirectory(profileDir);
                        }
                        progressBar.setValue(30);

                        // Unzip the ZIP file into appDataDir/.modinstaller/userInput
                        unzip(zipFile, profileDir);
                        progressBar.setValue(40);

                        String jsonStr = new String(Files.readAllBytes(installerFile));
                        JSONObject jsonObject = new JSONObject(jsonStr);

                        String profileName = jsonObject.getString("name") + " (" + userInput + ")";
                        String profileIcon = jsonObject.getString("icon");
                        String profileMcVersion = jsonObject.getString("mcversion");
                        String profileLoader = jsonObject.getString("loader");
                        progressBar.setValue(50);

                        runFabricInstaller(fabricInstallerPath.toString(), mcDataDir.toString(), profileMcVersion, profileLoader);
                        progressBar.setValue(80);

                        String jsonStrProfiles = new String(Files.readAllBytes(pofilesMcPath));
                        JSONObject launcherProfiles = new JSONObject(jsonStrProfiles);
                        JSONObject jsonObjectProfiles = launcherProfiles.getJSONObject("profiles");

                        // Iterate over the keys of the "profiles" object
                        for (String key : jsonObjectProfiles.keySet()) {
                            JSONObject profile = jsonObjectProfiles.getJSONObject(key);
                            // Check if the profile has a "name" attribute with value profileName
                            if (profile.has("name") && profile.getString("name").equals(profileName)) {
                                // Remove the profile from the "profiles" object
                                jsonObjectProfiles.remove(key);
                                break; // Break the loop once the profile is deleted
                            }
                        }
                        progressBar.setValue(90);

                        JSONObject profile = new JSONObject();
                        profile.put("created", "2024-03-16T10:02:37.000Z");
                        profile.put("gameDir", profileDir);
                        profile.put("icon", profileIcon);
                        profile.put("lastUsed", "2025-03-16T10:02:37.000Z");
                        profile.put("lastVersionId", "fabric-loader-" + profileLoader + "-" + profileMcVersion);
                        profile.put("name", profileName);
                        profile.put("type", "custom");

                        // Generate random UUID
                        UUID uuid = UUID.randomUUID();
                        String profileId = uuid.toString().replace("-", "");

                        // Add profile to profiles object
                        jsonObjectProfiles.put(profileId, profile);

                        try (FileWriter fileWriter = new FileWriter(String.valueOf(pofilesMcPath))) {
                            launcherProfiles.write(fileWriter);
                        }
                        progressBar.setValue(100);

                        // Add "DONE!" message after successful completion
                        JOptionPane.showMessageDialog(frame, "Pomyślnie zainstalowano! Możesz teraz uruchomić Minecraft.", "Message", JOptionPane.INFORMATION_MESSAGE);
                        System.exit(0);
                    } catch (Exception ex) {
                        // Display error message if any exception occurs
                        JOptionPane.showMessageDialog(frame, "Error: " + ex.getMessage());
                        System.exit(1);
                    }
                } else {
                    // Re-enable the button and text field
                    submitButton.setEnabled(true);
                    codeField.setEnabled(true);
                    canContinue = true;
                }
            }
        });

        // Center the frame on the screen
        frame.setLocationRelativeTo(null);

        // Show the frame
        frame.setVisible(true);
    }

    private static void unzip(Path zipFilePath, Path destDirectory) throws Exception {
        byte[] buffer = new byte[1024];
        try (ZipInputStream zipInputStream = new ZipInputStream(new FileInputStream(String.valueOf(zipFilePath)))) {
            ZipEntry zipEntry = zipInputStream.getNextEntry();
            while (zipEntry != null) {
                String fileName = zipEntry.getName();
                Path filePath = Paths.get(String.valueOf(destDirectory), fileName);
                if (!zipEntry.isDirectory()) {
                    Files.createDirectories(filePath.getParent());
                    try (FileOutputStream fos = new FileOutputStream(filePath.toFile())) {
                        int length;
                        while ((length = zipInputStream.read(buffer)) > 0) {
                            fos.write(buffer, 0, length);
                        }
                    }
                } else {
                    Files.createDirectories(filePath);
                }
                zipEntry = zipInputStream.getNextEntry();
            }
            zipInputStream.closeEntry();
        }
    }

    public static void downloadFile(String urlStr, Path filePath, JFrame frame) throws IOException {
        URL url = new URL(urlStr);
        try (InputStream in = url.openStream();
             FileOutputStream fos = new FileOutputStream(String.valueOf(filePath))) {
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                fos.write(buffer, 0, bytesRead);
            }
        } catch(Exception ex) {
            JOptionPane.showMessageDialog(frame, "Błędny kod nagrywki! Spróbuj ponownie później.");
            System.exit(1);
        }
    }


    public static boolean isMinecraftRunning() {
        try {
            ProcessBuilder processBuilder = new ProcessBuilder("tasklist");
            Process process = processBuilder.start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.contains("Minecraft")) {
                        return true; // Minecraft process found
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false; // Minecraft process not found
    }

    public static void deleteDirectory(File directory) {
        if (directory == null || !directory.exists()) {
            return;
        }

        File[] files = directory.listFiles();

        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    // Recursive call to delete subdirectories and their contents
                    deleteDirectory(file);
                } else {
                    // Delete regular files
                    file.delete();
                }
            }
        }

        // Delete the directory itself after all its contents have been deleted
        directory.delete();
    }

    public static void deleteDirectoryContents(File directory) {
        if (directory == null || !directory.exists()) {
            return;
        }

        File[] files = directory.listFiles();

        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    // Recursive call to delete subdirectories and their contents
                    deleteDirectory(file);
                } else {
                    // Delete regular files
                    file.delete();
                }
            }
        }
    }

    public static void runFabricInstaller(String installerPath, String minecraftDir, String mcVersion, String loader) throws IOException {
        ProcessBuilder processBuilder = new ProcessBuilder("java", "-jar", installerPath, "client", "-dir", minecraftDir, "-mcversion", mcVersion, "-loader", loader, "-noprofile");
        processBuilder.redirectErrorStream(true);
        Process process = processBuilder.start();

        // Read output
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        String line;
        while ((line = reader.readLine()) != null) {
            System.out.println(line);
        }

        // Wait for process to finish
        try {
            process.waitFor();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
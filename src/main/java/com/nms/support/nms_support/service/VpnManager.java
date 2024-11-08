package com.nms.support.nms_support.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nms.support.nms_support.model.VpnDetails;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class VpnManager implements IManager {

    private VpnDetails vpnDetails;
    private File source;

    public VpnManager(String sourcePath) {
        this.source = new File(sourcePath);
        ensureFileExists(this.source);
        initManager(this.source);
    }

    private void ensureFileExists(File source) {
        try {
            File parentDir = source.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                if (parentDir.mkdirs()) {
                    System.out.println("Directory created: " + parentDir.getAbsolutePath());
                } else {
                    System.out.println("Failed to create directory: " + parentDir.getAbsolutePath());
                }
            }

            if (!source.exists()) {
                if (source.createNewFile()) {
                    System.out.println("File created: " + source.getAbsolutePath());
                } else {
                    System.out.println("Failed to create file: " + source.getAbsolutePath());
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void initManager(File source) {
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            vpnDetails = objectMapper.readValue(source, VpnDetails.class);
            if (vpnDetails == null) {
                vpnDetails = new VpnDetails();
            }
        } catch (IOException e) {
            e.printStackTrace();
            vpnDetails = new VpnDetails();
        }
    }

    // VPN management methods

    public void setVpnDetails(VpnDetails vpnDetails) {
        this.vpnDetails = vpnDetails;
    }

    public VpnDetails getVpnDetails() {
        return vpnDetails;
    }

    public boolean saveData() {
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(this.source, vpnDetails);
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean updateVpnDetails(String username, String password, List<String> hosts) {
        if (vpnDetails != null && vpnDetails.getUsername().equals(username)) {
            vpnDetails.setPassword(password);
            vpnDetails.setHosts(hosts);
            return saveData();
        } else {
            System.out.println("VPN details not found for username: " + username);
            return false;
        }
    }

    public boolean deleteVpnDetails(String username) {
        if (vpnDetails != null && vpnDetails.getUsername().equals(username)) {
            vpnDetails = null;  // Remove the current VPN details
            return saveData();
        }
        return false;
    }

    public boolean addVpnDetails(String username, String password, List<String> hosts) {
        if (vpnDetails == null || !vpnDetails.getUsername().equals(username)) {
            vpnDetails = new VpnDetails(username, password, hosts);  // Add new VPN details
            return saveData();
        } else {
            System.out.println("VPN details already exist for username: " + username);
            return false;
        }
    }

    public void addHost(String host){
        if(!vpnDetails.getHosts().contains(host)) {
            vpnDetails.getHosts().add(host);
        }
        saveData();
    }
}

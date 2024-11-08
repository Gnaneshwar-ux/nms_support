package com.nms.support.nms_support.model;

import java.util.ArrayList;
import java.util.List;

public class VpnDetails {
    private String username;
    private String password;
    private List<String> hosts; // List of host addresses
    private String prevHost;

    public VpnDetails(){
        this.hosts = new ArrayList<>();
    }
    // Constructor
    public VpnDetails(String username, String password, List<String> hosts) {
        this.username = username;
        this.password = password;
        this.hosts = hosts;
    }

    public String getPrevHost() {
        return prevHost;
    }

    public void setPrevHost(String prevHost) {
        this.prevHost = prevHost;
    }

    // Getters and Setters
    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public List<String> getHosts() {
        return hosts;
    }

    public void setHosts(List<String> hosts) {
        this.hosts = hosts;
    }


    @Override
    public String toString() {
        return "VpnDetails{" +
                "username='" + username + '\'' +
                ", hosts=" + hosts +
                '}';
    }
}


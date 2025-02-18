package me.takinrom.passwordmanager;

public class Account {
    private final String service;
    private final String login;
    public Account(String service, String login) {
        this.service = service;
        this.login = login;
    }

    public String getLogin() {
        return login;
    }

    public String getService() {
        return service;
    }
}

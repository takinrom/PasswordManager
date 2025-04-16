package me.takinrom.passwordmanager;

public class Account {
    private final String service;
    private final String login;
    private int id;
    public Account (int id, String service, String login) {
        this.id = id;
        this.service = service;
        this.login = login;
    }
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

    public int getId() {
        return id;
    }
}

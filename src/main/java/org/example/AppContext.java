package org.example;

import org.example.util.ConsoleReader;

public class AppContext {

    private final ConsoleReader consoleReader;
    private final MainMenu mainMenu;

    public AppContext() {
        this.consoleReader = new ConsoleReader();
        this.mainMenu = new MainMenu(consoleReader);
    }

    public void start() {
        mainMenu.run();
    }
}

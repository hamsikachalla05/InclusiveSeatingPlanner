package com.seatingplanner.service;

import java.util.List;

/** Thrown when a hall configuration is rejected; carries every problem found, not just the first. */
public class InvalidConfigurationException extends Exception {

    private final List<String> problems;

    public InvalidConfigurationException(List<String> problems) {
        super(String.join("\n", problems));
        this.problems = List.copyOf(problems);
    }

    public List<String> getProblems() {
        return problems;
    }
}

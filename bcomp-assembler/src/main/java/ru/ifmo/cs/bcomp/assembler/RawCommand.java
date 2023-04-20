package ru.ifmo.cs.bcomp.assembler;

public record RawCommand(long memAddr, int cmd, String label) {
}

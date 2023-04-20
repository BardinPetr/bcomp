/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ru.ifmo.cs.bcomp.assembler;

/**
 * @author serge
 */
public class AddressingMode {
    // contains direct numbers: addresses, displacement, direct load numbers
    public volatile int number = MemoryWord.UNDEFINED;
    public volatile String reference = null;
    public AddressingType addressation;

    @Override
    public String toString() {
        String s = "";
        if (addressation == null) return s;
        switch (addressation) {
            case DIRECT_ABSOLUTE:
                if (number != MemoryWord.UNDEFINED) {
                    s += "0d" + number;
                    break;
                }
                if (reference != null) {
                    s = '$' + reference;
                    break;
                }
                s = "$undef";
                break;

            case DIRECT_RELATIVE_SP:
                if (number != MemoryWord.UNDEFINED) {
                    s = "&" + number;
                    break;
                }
                s = "&undef";
                break;
            case INDIRECT_SP:
                if (number != MemoryWord.UNDEFINED) {
                    s = "(&" + number + ')';
                    break;
                }
                s = "(undef)";
                break;
            case POST_INCREMENT_SP:
                if (number != MemoryWord.UNDEFINED) {
                    s = "(&" + number + ")+";
                    break;
                }
                s = "(&undef)+";
                break;
            case PRE_DECREMENT_SP:
                if (number != MemoryWord.UNDEFINED) {
                    s = "-(&" + number + ')';
                    break;
                }
                s = "-(&undef)";
                break;

            case INDIRECT_IP:
                if (reference != null) {
                    s = '(' + reference + ')';
                    break;
                }
                s = "(undef)";
                break;
            case POST_INCREMENT_IP:
                if (reference != null) {
                    s = '(' + reference + ")+";
                    break;
                }
                s = "(undef)+";
                break;
            case DIRECT_RELATIVE_IP:
                if (reference != null) {
                    s = reference;
                    break;
                }
                s = "undef";
                break;

            case DIRECT_LOAD:
                if (number != MemoryWord.UNDEFINED) {
                    s = "#" + number;
                    break;
                }
                s = "#undef";
                break;
            default:
                s = "UNDEF";
        }
        return s;
    }

    public enum AddressingType {
        DIRECT_ABSOLUTE,
        INDIRECT_IP, INDIRECT_SP,
        POST_INCREMENT_IP, POST_INCREMENT_SP, PRE_DECREMENT_SP,
        DIRECT_RELATIVE_SP, DIRECT_RELATIVE_IP,
        DIRECT_LOAD
    }

}

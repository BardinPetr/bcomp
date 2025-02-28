
package ru.ifmo.cs.bcomp.assembler.instructions;

/**
 *
 * @author serge
 */

public class Label {
    public final static int UNDEFINED = -1; 

    public String name;
    public volatile int address = UNDEFINED;
    public boolean referenced = false;

    @Override
    public String toString() {
        return "Label{" + "name=" + name + ", addr=" + (address != UNDEFINED ? address :"UNDEF") + '}';
    }
    
}

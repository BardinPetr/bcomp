package ru.ifmo.cs.bcomp.assembler.binary;

import ru.ifmo.cs.bcomp.assembler.instructions.InstructionWord;
import ru.ifmo.cs.bcomp.assembler.instructions.MemoryWord;

import java.util.ArrayList;
import java.util.List;

public class Section {

    private final List<Integer> commands = new ArrayList<>();
    private final short offset;
    private Short org = null;
    private Boolean isCodeSection = null;

    public Section(short offset) {
        this.offset = offset;
    }

    /**
     * @return true if command is successfully added, false if it should be added to new section
     */
    public boolean addCommand(MemoryWord word) {
        short curAddr = (short) word.address;

        boolean isInstruction = word instanceof InstructionWord;
        if (!isInstruction && word.value_addr_reference != null && !word.value_addr_reference.isEmpty()) {
            /*
                Here is a WORD $X construction, so we put it in code sector,
                and as addresses could only be up to 0x07FF, we set bit 15 to 1,
                therefore it will look like 8XXX, i.e. absolute addressed command
                so loader will successfully do remap as for real commands
                besides it, when such address is loaded into AR, it is cropped to XXX and absolutely valid
             */
            word.value |= 0x8000;
            isInstruction = true;
        }

        if (org == null)
            org = curAddr;
        if (isCodeSection == null)
            isCodeSection = isInstruction;

        if (isInstruction != isCodeSection || (org + getSize()) != curAddr)
            return false;

        isCodeSection = isInstruction;
        commands.add(word.value);
        return true;
    }

    public short getSize() {
        return (short) commands.size();
    }

    public SectionDescriptor getDescriptor() {
        if (getSize() == 0)
            throw new RuntimeException("Empty section");
        return new SectionDescriptor(isCodeSection, offset, getSize(), org);
    }

    public List<Integer> getCommands() {
        return commands;
    }
}

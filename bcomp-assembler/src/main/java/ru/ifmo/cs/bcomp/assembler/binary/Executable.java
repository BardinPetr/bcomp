package ru.ifmo.cs.bcomp.assembler.binary;

import ru.ifmo.cs.bcomp.assembler.instructions.MemoryWord;
import ru.ifmo.cs.bcomp.assembler.Program;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class Executable {
    private final short startAddr;
    private final List<Section> sections = new ArrayList<>();
    private short currentOffset = 0;

    public Executable(Program program) {
        this.startAddr = (short) program.start_address;

        var cmds = program.content
                .values().stream()
                .sorted(Comparator.comparing(MemoryWord::getAddress))
                .collect(Collectors.toList());
        addCommands(cmds);
    }

    protected void addSection(Section section) {
        if (section == null) return;
        sections.add(section);
        currentOffset += section.getSize();
    }

    public void addCommands(List<MemoryWord> commands) {
        Section section = new Section(currentOffset);

        var ptr = commands.listIterator();
        while (ptr.hasNext()) {
            if (section.addCommand(ptr.next())) continue;

            // here new section should be added
            addSection(section);
            section = new Section(currentOffset);
            ptr.previous();
        }
        addSection(section);
    }

    public byte[] build() {
        List<Integer> commands = sections.stream().flatMap(section -> section.getCommands().stream()).collect(Collectors.toList());

        var buf = ByteBuffer.allocate(2 * (3 + 3 * sections.size() + commands.size()));
        buf.putShort((short) sections.size());
        buf.putShort(startAddr);
        buf.putShort((short) commands.size());

        for (var i : sections)
            buf.put(i.getDescriptor().getBytes());

        for (var i : commands)
            buf.putShort((short) i.intValue());

        return buf.array();
    }

}

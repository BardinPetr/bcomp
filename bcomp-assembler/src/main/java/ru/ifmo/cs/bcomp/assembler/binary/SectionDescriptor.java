package ru.ifmo.cs.bcomp.assembler.binary;

import java.nio.ByteBuffer;

public record SectionDescriptor(boolean isCodeSection, short offset, short length, short org) {

    public byte[] getBytes() {
        short codeMarker = (short) ((isCodeSection ? 1 : 0) << 15);
        var buf = ByteBuffer.allocate(2 * 3);
        buf.putShort(offset);
        buf.putShort((short) (length | codeMarker));
        buf.putShort(org);
        return buf.array();
    }
}

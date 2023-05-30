package ru.ifmo.cs.bcomp.ui;

import ru.ifmo.cs.bcomp.io.IOCtrl;
import ru.ifmo.cs.bcomp.io.IOCtrlDuplex;
import ru.ifmo.cs.bcomp.ui.io.*;

public class IOGUI {

    private final IODevice<?>[] devices;

    public IOGUI(IOCtrl[] ioCtrls) {
        devices = new IODevice[]{
                new FirstIO(ioCtrls[1]),
                null,
//                new SecondIO(ioCtrls[2], ),
                new ThirdIO(ioCtrls[3]),
                new Terminal((IOCtrlDuplex) ioCtrls[4]),
                new TextPrinter(ioCtrls[5]),
                new Ticker(ioCtrls[6]),
                new SevenSegmentDisplay(ioCtrls[7]),
                new Keyboard(ioCtrls[8]),
                new Numpad(ioCtrls[9])
        };
    }

    public void load(int id) {
        if (id < 0 || id > devices.length)
            return;
        var dev = devices[id];
        dev.activate();
    }
}

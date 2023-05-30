/*
 * $Id$
 */
package ru.ifmo.cs.bcomp.io;

import ru.ifmo.cs.bcomp.CPU;
import ru.ifmo.cs.components.*;

/**
 * @author Dmitry Afanasiev <KOT@MATPOCKuH.Ru>
 */
public class IOCtrlDuplex extends IOCtrl {

    private final int FLAG_DATA_AVAILABLE = 6;
    private final int FLAG_SEND_READY = 5;

    private final int REG_IN = 0;
    private final int REG_OUT = 1;
    private final int STATE = 2;
    private final int CONTROL = 3;

    private final Register[] registers = {new Register(8), // input  (R)
            new Register(8), // output (W)
            new Register(8), // state
            new Register(8),}; // control
    private final Control[] writeToRegister = new Control[registers.length];
    private final DataDestination irqsc;
    private final DataDestination onDataHandler = value -> {
    };
    private DataDestination onReadyHandler;

    public IOCtrlDuplex(long addr, CPU cpu, DataDestination... chainctrl) {
        super(addr, 2, cpu);

        And reqirq = new And(registers[STATE], READYBIT, registers[CONTROL], 3);
        cpu.addIRQReqInput(reqirq);

        irqsc = new Valve(reqirq, 1, 0, 0, new Valve(registers[CONTROL], 3, 0, 0, ioaddr), new Valve(Consts.consts[1], 1, 0, 0, new PartWriter(ioctrl, 1, IOControlSignal.IRQ.ordinal())), new Not(0, chainctrl));

        Valve rdy = new Valve(Consts.consts[1], 1, 0, 0, new PartWriter(ioctrl, 1, IOControlSignal.RDY.ordinal()));

        // RW for STATUS and CONTROL
        for (int i = 2; i < registers.length; i++) {
            checkRegister(new Valve(ioctrl, 8, 0, i,
                    // Input
                    new Valve(Consts.consts[1], 1, 0, IOControlSignal.IN.ordinal(), new Valve(registers[i], 8, 0, 0, iodata), rdy),
                    // Output
                    new Valve(Consts.consts[1], 1, 0, IOControlSignal.OUT.ordinal(), writeToRegister[i] = new Valve(iodata, 8, 0, 0, registers[i]), rdy)));
        }

        writeToRegister[STATE].addDestination(cpu.getIRQReqValve());
        writeToRegister[CONTROL].addDestination(cpu.getIRQReqValve());

        checkRegister(
                // input register (0) (from device)
                new Valve(ioctrl, 8, 0, REG_IN,
                        new Valve(Consts.consts[1], 1, 0, IOControlSignal.IN.ordinal(),
                                // read value
                                new Valve(registers[REG_IN], 8, 0, 0, iodata),
                                // reset ready flag
                                new Valve(Consts.consts[0], 1, 0, 0, new PartWriter(registers[STATE], 1, FLAG_DATA_AVAILABLE)),
                                this::onInCalled,
                                rdy)
                ),
                // output register (1) (to device)
                new Valve(ioctrl, 8, 0, REG_OUT,
                        new Valve(Consts.consts[1], 1, 0, IOControlSignal.OUT.ordinal(),
                                // set to not ready to new OUT
                                new Valve(Consts.consts[0], 1, 0, 0, new PartWriter(registers[STATE], 1, FLAG_SEND_READY)),
                                // write value
                                writeToRegister[REG_OUT] = new Valve(iodata, 8, 0, 0, registers[REG_OUT]),
                                rdy
                        )
                ));

        setWriteAvailable();
    }

    private void onInCalled(long data) {
        if (onReadyHandler != null)
            onReadyHandler.setValue(data);
    }

    public void setOnReady(DataDestination handler) {
        onReadyHandler = handler;
    }

    @Override
    public boolean isReady() {
        throw new RuntimeException("do not use is-/set-Ready on IOCtrlDuplex");
    }

    @Override
    public void setReady() {
        throw new RuntimeException("do not use is-/set-Ready on IOCtrlDuplex");
    }


    /**
     * register handler for new data written by CPU
     */
    public void setDataHandler(DataDestination onDataHandler) {
        writeToRegister[REG_OUT].addDestination(onDataHandler);
    }

    /**
     * Set flag SR bit 6 that data from device is available now
     */
    public void setReadAvailable() {
        registers[STATE].setValue(1, 1, FLAG_DATA_AVAILABLE);
        updateStateIRQ();
    }

    /**
     * Set flag SR bit 5 that device is ready to receive new data
     */
    public void setWriteAvailable() {
        registers[STATE].setValue(1, 1, FLAG_SEND_READY);
    }

    public boolean isReadAvailable() {
        return registers[STATE].getValue(FLAG_DATA_AVAILABLE) == 1;
    }

    @Override
    public DataDestination getIRQSC() {
        return irqsc;
    }

    @Override
    public Register[] getRegisters() {
        return registers;
    }

    @Override
    public void addDestination(int reg, DataDestination... dsts) {
        writeToRegister[reg].addDestination(dsts);
    }

    /**
     * Get data from data OUT register
     */
    @Override
    public long getData() {
        return registers[REG_OUT].getValue();
    }

    /**
     * Put value into data IN register
     */
    @Override
    public void setData(long value) {
        registers[REG_IN].setValue(value);
        setReadAvailable();
    }

    @Override
    public String toString() {
        return "IN = " + registers[0] + " OUT = " + registers[1] + " State = " + registers[2] + " Control = " + registers[3];
    }
}
